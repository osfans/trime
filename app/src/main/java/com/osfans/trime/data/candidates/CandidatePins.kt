// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.data.candidates

import android.util.AtomicFile
import com.osfans.trime.core.data.DataManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

@Serializable
data class CandidatePin(val schema: String, val input: String, val text: String)

/** One pin per exact input, bounded independently of the number of native candidates. */
internal class CandidatePinRules(initial: List<CandidatePin> = emptyList()) {
    var entries = initial.toList()
        private set

    init {
        require(initial.size <= MAX_ENTRIES)
        require(initial.all(::valid))
        require(initial.map { it.schema to it.input }.distinct().size == initial.size)
    }

    fun pin(entry: CandidatePin): List<CandidatePin> {
        require(valid(entry))
        val replacement = entries.filterNot { it.schema == entry.schema && it.input == entry.input } + entry
        require(replacement.size <= MAX_ENTRIES) { "Candidate pin limit reached" }
        return replacement
    }

    fun reset(schema: String, input: String): List<CandidatePin> = entries.filterNot { it.schema == schema && it.input == input }

    fun replace(entries: List<CandidatePin>) {
        this.entries = CandidatePinRules(entries).entries
    }

    companion object {
        const val MAX_ENTRIES = 512

        fun valid(entry: CandidatePin): Boolean = entry.schema.length in 1..128 && entry.input.length in 1..256 &&
            entry.text.length in 1..4096 && listOf(entry.schema, entry.input, entry.text).none { '\u0000' in it }
    }
}

/** Used only by the Rime dispatcher. Failed writes never replace the active rule set. */
internal class CandidatePinStore(
    private val fileProvider: () -> AtomicFile = { AtomicFile(File(DataManager.userDataDir, ".trime-candidate-pins.json")) },
) {
    private val file get() = fileProvider()
    private var rules = CandidatePinRules()
    private var available = false

    val entries get() = rules.entries

    fun load() {
        available = false
        val storage = file
        if (!storage.baseFile.exists() && !File(storage.baseFile.path + ".bak").exists()) {
            rules = CandidatePinRules()
            available = true
            return
        }
        val bytes = read(storage)
        val text = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        rules = CandidatePinRules(Json.decodeFromString<List<CandidatePin>>(text))
        available = true
    }

    private fun read(storage: AtomicFile): ByteArray = storage.openRead().use { input ->
        val buffer = ByteArray(MAX_BYTES + 1)
        var size = 0
        while (size < buffer.size) {
            val read = input.read(buffer, size, buffer.size - size)
            if (read < 0) break
            size += read
        }
        require(size <= MAX_BYTES) { "Candidate pin file is too large" }
        buffer.copyOf(size)
    }

    fun pin(entry: CandidatePin) = save(rules.pin(entry))

    fun reset(schema: String, input: String) = save(rules.reset(schema, input))

    private fun save(entries: List<CandidatePin>) {
        check(available) { "Candidate pin storage needs recovery" }
        val bytes = Json.encodeToString(entries).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES)
        val storage = file
        val output = storage.startWrite()
        var finished = false
        try {
            output.write(bytes)
            // AtomicFile can log sync/rename failures without throwing, including
            // on API 21. Verify storage before acknowledging the pin to the user.
            output.fd.sync()
            finished = true
            storage.finishWrite(output)
            check(read(storage).contentEquals(bytes)) { "Candidate pin write could not be verified" }
        } catch (error: Exception) {
            if (!finished) storage.failWrite(output) else available = false
            throw error
        }
        rules.replace(entries)
    }

    companion object {
        private const val MAX_BYTES = 2 * 1024 * 1024
    }
}
