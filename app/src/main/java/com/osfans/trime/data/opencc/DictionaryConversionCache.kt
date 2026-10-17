// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.data.opencc

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Process-local evidence of successful conversions, bounded independently of user dictionaries. */
internal class DictionaryConversionCache(private val capacity: Int = 128) {
    private data class Fingerprint(val source: ByteArray, val output: ByteArray)

    private val entries = LinkedHashMap<Pair<String, String>, Fingerprint>()

    init {
        require(capacity > 0)
    }

    /** Returns true after conversion, false when both files still match a successful conversion. */
    @Synchronized
    fun convert(source: File, output: File, converter: (File, File) -> Unit): Boolean {
        val input = source.canonicalFile
        val destination = output.canonicalFile
        require(input != destination) { "Dictionary source and output must differ" }
        val key = input.path to destination.path
        // Remove before any I/O so failed validation or conversion cannot retain a cache hit.
        val previous = entries.remove(key)
        val sourceHash = digest(input)
        if (previous != null && previous.source.contentEquals(sourceHash) &&
            destination.isFile && previous.output.contentEquals(digest(destination))
        ) {
            entries[key] = previous
            return false
        }

        // A snapshot binds the converter's actual input to its fingerprint, even if an
        // external editor changes the original file and restores it during conversion.
        // .tmp files are not enumerated as OpenCC dictionaries after an interrupted process.
        val snapshot = File.createTempFile(".opencc-source-", ".tmp", destination.parentFile)
        try {
            input.copyTo(snapshot, overwrite = true)
            if (!digest(snapshot).contentEquals(sourceHash)) throw IOException("Dictionary changed while copying: $input")
            val temporary = File.createTempFile(".opencc-output-", ".tmp", destination.parentFile)
            try {
                converter(snapshot, temporary)
                if (!temporary.isFile || temporary.length() == 0L) throw IOException("Dictionary conversion produced no output: $input")
                val outputHash = digest(temporary)
                if (!digest(input).contentEquals(sourceHash)) throw IOException("Dictionary changed during conversion: $input")
                // Same-directory rename replaces the output atomically on Android. Never
                // delete the last usable dictionary before a new conversion has succeeded.
                if (!temporary.renameTo(destination)) throw IOException("Cannot publish converted dictionary: $destination")
                entries[key] = Fingerprint(sourceHash, outputHash)
                if (entries.size > capacity) entries.remove(entries.keys.first())
                return true
            } finally {
                temporary.delete()
            }
        } finally {
            snapshot.delete()
        }
    }

    private fun digest(file: File): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val size = stream.read(buffer)
                if (size < 0) break
                hash.update(buffer, 0, size)
            }
        }
        return hash.digest()
    }
}
