// SPDX-FileCopyrightText: 2015 - 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.userdict

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction

internal object DictionaryTextFormats {
    const val MAX_IMPORT_BYTES = 32L * 1024 * 1024
    const val MAX_PHRASE_BYTES = 256L * 1024

    fun copyBounded(input: InputStream, destination: File, limit: Long = MAX_IMPORT_BYTES) {
        destination.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            var copied = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                copied += count
                if (copied > limit) throw IOException("Dictionary file is too large")
                output.write(buffer, 0, count)
            }
        }
    }

    private fun lines(file: File, block: (String, Int) -> Unit) {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        InputStreamReader(file.inputStream(), decoder).buffered().use { reader ->
            var number = 0
            val line = StringBuilder()
            val buffer = CharArray(4096)
            fun consume() {
                number++
                val text = line.toString().trimEnd()
                if ('\r' in text) throw IOException("Invalid dictionary line $number")
                block(text, number)
                line.setLength(0)
            }
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                for (index in 0 until count) {
                    val character = buffer[index]
                    if (character == '\n') {
                        consume()
                    } else {
                        if (line.length >= 64 * 1024 || character == '\u0000') throw IOException("Invalid dictionary line ${number + 1}")
                        line.append(character)
                    }
                }
            }
            if (line.isNotEmpty()) consume()
        }
    }

    /** Same phrase/code/weight column order as Rime TableDb; refuse silently skipped rows. */
    fun validateTable(file: File): Int {
        var count = 0
        var comments = true
        lines(file) { line, number ->
            when {
                line.isEmpty() -> Unit

                comments && line.startsWith('#') -> {
                    if (line == "# no comment") comments = false
                }

                else -> {
                    val row = line.split('\t')
                    if (row.size !in 2..3 || row[0].isBlank() || row[1].isBlank() ||
                        (row.size == 3 && row[2].toIntOrNull() == null)
                    ) {
                        throw IOException("Invalid dictionary entry at line $number")
                    }
                    count++
                }
            }
        }
        return count
    }

    /** Snapshot metadata determines the destination in Rime; never trust a provider file name. */
    fun snapshotDictionaryName(file: File): String {
        var dbName: String? = null
        var dbType: String? = null
        var comments = true
        lines(file) { line, number ->
            when {
                line.isEmpty() -> Unit

                comments && line.startsWith("#@") -> {
                    val row = line.substring(2).split('\t')
                    if (row.size != 2) throw IOException("Invalid dictionary metadata at line $number")
                    when (row[0]) {
                        "/db_name" -> {
                            if (dbName != null) throw IOException("Duplicate dictionary identity")
                            dbName = row[1]
                        }

                        "/db_type" -> {
                            if (dbType != null) throw IOException("Duplicate dictionary type")
                            dbType = row[1]
                        }

                        "/tick" -> {
                            if (row[1].toLongOrNull()?.let { it >= 0 } != true) throw IOException("Invalid dictionary tick metadata")
                        }
                    }
                }

                comments && line.startsWith('#') -> {
                    if (line == "# no comment") comments = false
                }

                else -> {
                    val row = line.split('\t')
                    if (row.size !in 2..3 || row[0].isBlank() || row[1].isBlank()) {
                        throw IOException("Invalid dictionary snapshot entry at line $number")
                    }
                    if (row.size == 3 && row[2].isNotEmpty()) {
                        val fields = row[2].split(' ').filter { it.isNotEmpty() }
                        if (fields.any { field ->
                                val pair = field.split('=', limit = 2)
                                pair.size != 2 || when (pair[0]) {
                                    "c" -> pair[1].toIntOrNull() == null
                                    "d" -> pair[1].toDoubleOrNull()?.isFinite() != true
                                    "t" -> pair[1].toLongOrNull()?.let { it >= 0 } != true
                                    else -> true
                                }
                            }
                        ) {
                            throw IOException("Invalid dictionary learning data at line $number")
                        }
                    }
                }
            }
        }
        if (dbType != "userdb") throw IOException("File is not a Rime user dictionary snapshot")
        val rawName = dbName ?: throw IOException("Dictionary snapshot identity is missing")
        val marker = rawName.lastIndexOf(".userdb")
        val name = if (marker >= 0) {
            if (rawName.substring(marker) !in setOf(".userdb", ".userdb.txt", ".userdb.kct", ".userdb.ldb")) {
                throw IOException("Unrecognized dictionary snapshot name")
            }
            rawName.substring(0, marker)
        } else {
            rawName
        }
        return requireDictionaryName(name)
    }
}
