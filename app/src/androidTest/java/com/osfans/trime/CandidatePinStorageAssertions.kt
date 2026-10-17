/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Instrumentation
import android.util.AtomicFile
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeDispatcher
import com.osfans.trime.data.candidates.CandidatePin
import com.osfans.trime.data.candidates.CandidatePinRules
import com.osfans.trime.data.candidates.CandidatePinStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.util.UUID

/** Real Android storage and confined JNI checks using an exclusively owned cache fixture. */
object CandidatePinStorageAssertions {
    suspend fun verify(instrumentation: Instrumentation, dispatcher: RimeDispatcher): Int = dispatcher.runConfined {
        val directory = File(instrumentation.targetContext.cacheDir, "__t10_pin_storage_${UUID.randomUUID()}")
        check(directory.mkdir()) { "Could not create owned candidate pin storage fixture" }
        withCleanup(cleanup = { check(directory.deleteRecursively()) { "Owned candidate pin storage fixture could not be removed" } }) {
            var checks = 0
            val persistedFile = File(directory, "persisted.json")
            var activeFile = persistedFile
            val store = CandidatePinStore { AtomicFile(activeFile) }
            store.load()
            check(store.entries.isEmpty())
            checks++

            val first = CandidatePin("owned_schema", "a", "虚构置顶甲")
            store.pin(first)
            val savedBytes = persistedFile.readBytes()
            check(savedBytes.contentEquals(Json.encodeToString(listOf(first)).toByteArray(Charsets.UTF_8)))
            val recreated = CandidatePinStore { AtomicFile(persistedFile) }.apply { load() }
            check(recreated.entries == listOf(first)) { "Persisted candidate pin was not restored exactly" }
            checks++

            // A regular file cannot contain the target child file. A directory-as-target would
            // be unreliable because older AtomicFile versions can rename it to a backup first.
            val blockedParentBytes = byteArrayOf(17, 29, 31)
            val blockedParent = File(directory, "blocked-parent").apply { writeBytes(blockedParentBytes) }
            activeFile = File(blockedParent, "pins.json")
            expectFailure<IOException> { store.pin(first.copy(text = "虚构置顶乙")) }
            check(store.entries == listOf(first)) { "Failed storage write changed active candidate pins" }
            check(persistedFile.readBytes().contentEquals(savedBytes)) { "Failed storage write changed persisted candidate pins" }
            check(blockedParent.readBytes().contentEquals(blockedParentBytes)) { "Failed storage write changed its parent fixture" }
            activeFile = persistedFile
            store.reset(first.schema, first.input)
            check(store.entries.isEmpty())
            check(CandidatePinStore { AtomicFile(persistedFile) }.apply { load() }.entries.isEmpty())
            checks++

            val invalidFile = File(directory, "invalid-utf8.json")
            val invalidBytes = "[{\"schema\":\"owned_schema\",\"input\":\"a\",\"text\":\"".toByteArray(Charsets.UTF_8) +
                byteArrayOf(0xc3.toByte(), 0x28) + "\"}]".toByteArray(Charsets.UTF_8)
            invalidFile.writeBytes(invalidBytes)
            val invalidStore = CandidatePinStore { AtomicFile(invalidFile) }
            expectFailure<CharacterCodingException> { invalidStore.load() }
            check(invalidFile.readBytes().contentEquals(invalidBytes)) { "Invalid UTF-8 was rewritten during load" }
            expectFailure<IllegalStateException> { invalidStore.pin(first) }
            check(invalidFile.readBytes().contentEquals(invalidBytes)) { "Invalid pin storage was overwritten by a management action" }
            check(!File(invalidFile.path + ".bak").exists() && !File(invalidFile.path + ".new").exists())
            expectFailure<CharacterCodingException> { CandidatePinStore { AtomicFile(invalidFile) }.load() }
            checks++

            val capacity = CandidatePinRules.MAX_ENTRIES
            check(capacity == 512) { "Candidate pin capacity changed; update the JNI capacity fixture" }
            val pins = List(capacity) { index -> CandidatePin("owned_capacity_$index", "input_$index", "虚构容量$index") }
            val capacityFile = File(directory, "capacity.json")
            // Seed 511 validated records, then save all 512 through the production store. This
            // checks the boundary with one durable write rather than 512 syncs on the emulator.
            capacityFile.writeText(Json.encodeToString(pins.dropLast(1)), Charsets.UTF_8)
            val capacityStore = CandidatePinStore { AtomicFile(capacityFile) }.apply { load() }
            check(capacityStore.entries == pins.dropLast(1))
            capacityStore.pin(pins.last())
            check(capacityStore.entries == pins)
            check(CandidatePinStore { AtomicFile(capacityFile) }.apply { load() }.entries == pins)
            checks++

            val replacement = pins.first().copy(text = "虚构容量替换")
            capacityStore.pin(replacement)
            val replacementEntries = pins.drop(1) + replacement
            check(capacityStore.entries == replacementEntries)
            check(CandidatePinStore { AtomicFile(capacityFile) }.apply { load() }.entries == replacementEntries)
            val capacityBytes = capacityFile.readBytes()
            expectFailure<IllegalArgumentException> { capacityStore.pin(CandidatePin("owned_overflow", "extra", "虚构容量额外")) }
            check(capacityStore.entries == replacementEntries)
            check(capacityFile.readBytes().contentEquals(capacityBytes)) { "Rejected overflow changed persisted candidate pins" }
            checks++

            // Access only the existing private JNI entry on the actual Rime dispatcher. Each
            // array contains 512 strings, exceeding API 21's local reference table if elements
            // are not released while copying. Restore normal rules even if invocation fails.
            val normalPins = CandidatePinStore().apply { load() }.entries
            val nativeSetter = Rime::class.java.getDeclaredMethod(
                "setRimeCandidatePins",
                Array<String>::class.java,
                Array<String>::class.java,
                Array<String>::class.java,
            ).apply { isAccessible = true }
            withCleanup(
                cleanup = {
                    nativeSetter.invoke(
                        null,
                        normalPins.map { it.schema }.toTypedArray(),
                        normalPins.map { it.input }.toTypedArray(),
                        normalPins.map { it.text }.toTypedArray(),
                    )
                },
            ) {
                nativeSetter.invoke(null, pins.map { it.schema }.toTypedArray(), pins.map { it.input }.toTypedArray(), pins.map { it.text }.toTypedArray())
                checks++
            }
            checks
        }
    }

    private inline fun <T> withCleanup(cleanup: () -> Unit, block: () -> T): T {
        var failure: Throwable? = null
        try {
            return block()
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            try {
                cleanup()
            } catch (error: Throwable) {
                val prior = failure
                if (prior == null) throw error
                if (prior !== error) prior.addSuppressed(error)
            }
        }
    }

    private inline fun <reified T : Throwable> expectFailure(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            if (error !is T) throw error
            return
        }
        error("Expected candidate pin fixture failure did not occur")
    }
}
