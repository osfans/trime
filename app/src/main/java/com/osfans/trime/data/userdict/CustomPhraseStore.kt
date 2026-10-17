// SPDX-FileCopyrightText: 2015 - 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.userdict

import com.osfans.trime.core.data.DataManager
import com.osfans.trime.util.appContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Shared stabledb source used by the bundled full-pinyin and nine-key schemas. */
object CustomPhraseStore {
    const val FILE_NAME = "custom_phrase.txt"

    private fun root() = DataManager.userDataDir.canonicalFile

    private fun files() = DictionaryFileStore(
        root(),
        File(appContext.filesDir, "userdict-backups"),
    )

    fun read(): String {
        val source = File(root(), FILE_NAME)
        if (source.canonicalFile != source.absoluteFile) throw IOException("Unsafe phrase dictionary path")
        if (!source.exists()) return ""
        if (source.length() > DictionaryTextFormats.MAX_PHRASE_BYTES) throw IOException("Phrase dictionary is too large for the editor; use export")
        DictionaryTextFormats.validateTable(source)
        return source.readText()
    }

    /** Replace all entries after validation; an exact backup and crash journal precede the write. */
    fun save(text: String): Int = text.byteInputStream().use(::importPhrases)

    fun importPhrases(input: InputStream): Int {
        val root = root()
        val temporary = File.createTempFile(".custom-phrase-", ".txt", root)
        try {
            DictionaryTextFormats.copyBounded(input, temporary, DictionaryTextFormats.MAX_PHRASE_BYTES)
            val entries = DictionaryTextFormats.validateTable(temporary)
            FileOutputStream(temporary, true).use { it.fd.sync() }
            files().mutate(FILE_NAME) {
                if (!temporary.renameTo(File(root, FILE_NAME))) throw IOException("Cannot save phrase dictionary")
            }
            return entries
        } finally {
            temporary.delete()
        }
    }

    fun export(output: OutputStream) {
        val source = File(root(), FILE_NAME)
        if (source.canonicalFile != source.absoluteFile) throw IOException("Unsafe phrase dictionary path")
        if (source.exists()) source.inputStream().use { it.copyTo(output) }
    }

    fun hasRecoveryBackup(): Boolean = files().latest(FILE_NAME) != null

    fun recover() {
        val store = files()
        store.recoverPendingMutation()
        val backup = store.latest(FILE_NAME) ?: throw IOException("No phrase recovery backup is available")
        store.mutate(FILE_NAME) { store.restore(backup) }
    }
}
