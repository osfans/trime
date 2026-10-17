/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.paging.PagingSource
import com.osfans.trime.core.CandidateSnapshot
import com.osfans.trime.core.Candidates
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeDispatcher
import com.osfans.trime.core.RimeLifecycle
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.StaleCandidateSnapshotException
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.candidates.CandidateRow
import com.osfans.trime.ime.candidates.unrolled.CandidatesPagingSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.ArrayDeque
import java.util.UUID

/**
 * Dedicated-emulator native integration checks, executable on API 21 and later.
 * Uses actual Rime responses and PagingSource loads, without an Activity or InputConnection.
 * Does not measure candidate drawing, editor commits, latency budgets, or resident memory.
 */
object CandidateSnapshotProbe {
    private const val TOTAL = 49
    private const val BACKSPACE = 0xff08

    private val candidateTexts = List(TOTAL) { "候选${('一'.code + it).toChar()}" }

    private fun writeFixture(prefix: String): File {
        val dictionary = File(DataManager.userDataDir, "$prefix.dict.yaml")
        val schema = File(DataManager.userDataDir, "$prefix.schema.yaml")
        check(dictionary.createNewFile() && schema.createNewFile()) { "Candidate fixture already exists" }
        dictionary.writeText(
            """
            ---
            name: $prefix
            version: "1"
            sort: by_weight
            use_preset_vocabulary: false
            ...
            """.trimIndent() + "\n" +
                candidateTexts.mapIndexed { index, text -> "$text\ta\t${TOTAL - index}" }.joinToString("\n") + "\n",
        )
        schema.writeText(
            """
            schema:
              schema_id: $prefix
              name: T08 candidate snapshot probe
              version: "1"

            switches:
              - name: ascii_mode
                reset: 0
                states: [ native, ascii ]

            engine:
              processors:
                - ascii_composer
                - speller
                - selector
                - navigator
                - express_editor
              segmentors:
                - ascii_segmentor
                - abc_segmentor
                - fallback_segmentor
              translators:
                - table_translator

            speller:
              alphabet: abcdefghijklmnopqrstuvwxyz
              delimiter: " '"
              auto_select: false
              auto_clear: false
              max_code_length: 0

            translator:
              dictionary: $prefix
              enable_user_dict: false
              enable_sentence: false
              enable_completion: false

            menu:
              page_size: 5
            """.trimIndent() + "\n",
        )
        return schema
    }

    private fun removeFixture(prefix: String) {
        val root = DataManager.userDataDir
        root.walkBottomUp().filter { it != root && it.name.startsWith(prefix) }.forEach {
            check(!it.exists() || it.deleteRecursively()) { "Could not remove an owned candidate fixture" }
        }
        check(root.walkTopDown().none { it != root && it.name.startsWith(prefix) }) {
            "An owned candidate fixture remains"
        }
    }

    private fun page(result: PagingSource.LoadResult<Int, CandidateRow>): PagingSource.LoadResult.Page<Int, CandidateRow> = result as? PagingSource.LoadResult.Page<Int, CandidateRow>
        ?: error("Expected an actual native candidate page, got ${result.javaClass.simpleName}")

    private class NativeProbeProgress {
        var fifoKeys = 0
    }

    private data class InitialCandidateState(
        val snapshot: CandidateSnapshot,
        val source: CandidatesPagingSource,
    )

    private class NativeCandidateProbeContext(
        private val rime: RimeSession,
        private val dispatcher: RimeDispatcher,
        private val messages: ArrayDeque<RimeMessage<*>>,
        private val firstEditor: Long,
        private val secondEditor: Long,
        private val progress: NativeProbeProgress,
        private val phase: (String) -> Unit,
    ) {
        suspend fun verify(): Int {
            val initial = verifyInitialAndPaging()
            val newer = verifyGenerationRejection(initial)
            verifyEditorOwnership(newer)
            verifyNativeFifo()
            verifyFinalSelection()
            verifyFinishAndRebind()
            return progress.fifoKeys
        }

        private suspend fun fresh(editor: Long): CandidateSnapshot = rime.runOnReady {
            clearComposition()
            check(processKey('a'.code))
            check(getRawInput() == "a")
            candidateSnapshotCached.also {
                check(it.isValid && it.editorToken == editor) { "Candidate snapshot has the wrong owner" }
            }
        }

        private suspend fun snapshotMessages(snapshot: CandidateSnapshot): List<RimeMessage<*>> = withTimeout(10_000) {
            var found: List<RimeMessage<*>>
            do {
                found = synchronized(messages) { messages.filter { it.snapshot == snapshot } }
                if (found.none { it is RimeMessage.T9Message }) delay(10)
            } while (found.none { it is RimeMessage.T9Message })
            found
        }

        private suspend fun response(snapshot: CandidateSnapshot): Candidates.Bulk {
            val matching = snapshotMessages(snapshot)
            check(matching.all { it.editorToken == snapshot.editorToken })
            check(matching.count { it is RimeMessage.CommitTextMessage } == 1)
            check(matching.count { it is RimeMessage.CompositionMessage } == 1)
            check(matching.count { it is RimeMessage.StatusMessage } == 1)
            val bulk = matching.filterIsInstance<RimeMessage.BulkCandidatesMessage>().single().data
            check(bulk.snapshot == snapshot)
            check(bulk.candidates.size == 16 && bulk.total == -1) { "JNI bulk response read beyond its initial bounded window" }
            check(bulk.candidates.map { it.text } == candidateTexts.take(16)) { "Native fixture candidate order changed" }
            return bulk
        }

        private suspend fun reject(snapshot: CandidateSnapshot, source: CandidatesPagingSource) {
            val before = rime.runOnReady { getRawInput() to candidateSnapshotCached }
            rime.runOnReady {
                check(!selectCandidate(0, true, snapshot)) { "Stale selection entered the native engine" }
                check(!deleteCandidate(0, true, snapshot)) { "Stale deletion entered the native engine" }
                check(!changeCandidatePage(false, snapshot)) { "Stale page change entered the native engine" }
                val rejected = runCatching { getCandidates(0, 48, snapshot) }.exceptionOrNull()
                check(rejected is StaleCandidateSnapshotException) { "Stale native candidate read was accepted" }
            }
            check(source.load(PagingSource.LoadParams.Refresh(null, 48, false)) is PagingSource.LoadResult.Invalid<*, *>)
            check(source.load(PagingSource.LoadParams.Append(48, 48, false)) is PagingSource.LoadResult.Invalid<*, *>)
            check(rime.runOnReady { getRawInput() to candidateSnapshotCached } == before) {
                "Rejected asynchronous candidates changed the current input or generation"
            }
        }

        private suspend fun verifyInitialAndPaging(): InitialCandidateState {
            val initial = fresh(firstEditor)
            response(initial)
            phase("one native response associates commit, composition, candidates and status with a bounded 16-row snapshot")

            val known = CandidatesPagingSource(rime, TOTAL, 0, initial)
            // A large refresh must still perform one bounded 48-row native read.
            val first = page(known.load(PagingSource.LoadParams.Refresh(null, 144, false)))
            check(first.data.size == 48 && first.prevKey == null && first.nextKey == 48)
            check(first.data.map { it.candidate.text } == candidateTexts.take(48))
            check(first.data.map { it.index } == (0 until 48).toList())
            check(first.data.all { it.snapshot == initial })
            val last = page(known.load(PagingSource.LoadParams.Append(checkNotNull(first.nextKey), 48, false)))
            check(last.data.single().index == 48 && last.nextKey == null)
            check(last.data.single().candidate.text == candidateTexts.last() && last.data.single().snapshot == initial)
            phase("known 49-candidate native menu loads all 48 + 1 rows, including the final row")

            val unknown = CandidatesPagingSource(rime, -1, 0, initial)
            val unknownFirst = page(unknown.load(PagingSource.LoadParams.Refresh(null, 48, false)))
            val unknownLast = page(unknown.load(PagingSource.LoadParams.Append(checkNotNull(unknownFirst.nextKey), 48, false)))
            check(unknownFirst.data == first.data && unknownFirst.nextKey == 48)
            check(unknownLast.data == last.data && unknownLast.nextKey == null)
            val offset = page(CandidatesPagingSource(rime, TOTAL, 48, initial).load(PagingSource.LoadParams.Refresh(null, 48, false)))
            check(offset.data == last.data && offset.nextKey == null)
            val empty = page(unknown.load(PagingSource.LoadParams.Append(TOTAL, 48, false)))
            check(empty.data.isEmpty() && empty.nextKey == null)
            phase("unknown totals, tail offsets and an empty native tail terminate paging without duplicate rows")
            return InitialCandidateState(initial, known)
        }

        private suspend fun verifyGenerationRejection(state: InitialCandidateState): CandidateSnapshot {
            val initial = state.snapshot
            val known = state.source
            rime.runOnReady { check(processKey('b'.code)) }
            reject(initial, known)
            val newer = fresh(firstEditor)
            check(newer.generation > initial.generation)
            response(newer)
            phase("older generations reject select/delete/page/read and return PagingSource.Invalid without altering newer input")
            return newer
        }

        private suspend fun verifyEditorOwnership(newer: CandidateSnapshot) {
            val oldEditorSource = CandidatesPagingSource(rime, TOTAL, 0, newer)
            rime.runOnReady {
                beginEditor(secondEditor, nullInputType = false, privacyMode = false)
                check(getRawInput().isEmpty()) { "Input crossed the editor boundary" }
            }
            val second = fresh(secondEditor)
            check(second.generation > newer.generation)
            response(second)
            reject(newer, oldEditorSource)
            rime.runOnReady {
                finishEditor(firstEditor)
                check(candidateSnapshotCached == second && getRawInput() == "a") { "An old editor teardown erased the current input" }
                val current = getCandidates(0, 16, second)
                check(current.map { it.text } == candidateTexts.take(16))
            }
            phase("a new editor invalidates old asynchronous pages and actions while old teardown leaves current candidates intact")
        }

        private suspend fun verifyNativeFifo(): Int {
            rime.runOnReady { clearComposition() }
            val keys = "abcd".map { it.code } + BACKSPACE + 'e'.code + List(4) { BACKSPACE }
            var lastGeneration = rime.runOnReady { candidateSnapshotCached.generation }
            repeat(8) {
                var expected = ""
                val input = List(4) { keys }.flatten().map { key ->
                    expected = if (key == BACKSPACE) expected.dropLast(1) else expected + key.toChar()
                    key to expected
                }
                // UNDISPATCHED runs each caller up to its first dispatcher suspension;
                // awaitAll occurs only after the entire 40-operation burst is submitted.
                // Nested API calls remain on the same worker, making each observation
                // atomic with respect to subsequent queued keys.
                val observed = mutableListOf<Int>()
                coroutineScope {
                    input.mapIndexed { index, (key, raw) ->
                        async(start = CoroutineStart.UNDISPATCHED) {
                            dispatcher.runConfined {
                                rime.runOnReady {
                                    check(processKey(key)) { "The native engine dropped a queued key" }
                                    check(getRawInput() == raw) { "Native FIFO input differs at queued position $index" }
                                    val snapshot = candidateSnapshotCached
                                    check(snapshot.editorToken == secondEditor && snapshot.generation > lastGeneration)
                                    lastGeneration = snapshot.generation
                                    observed.add(index)
                                }
                            }
                        }
                    }.awaitAll()
                }
                check(observed == input.indices.toList()) { "Queued native keys completed out of order" }
                progress.fifoKeys += input.size
                check(rime.runOnReady { getRawInput().isEmpty() }) { "A FIFO burst lost a backspace or left residual input" }
            }
            check(progress.fifoKeys == 320)
            phase("320 real native letter/backspace operations in eight concurrent bursts retain every key and FIFO state")
            return progress.fifoKeys
        }

        private suspend fun verifyFinalSelection() {
            val selectable = fresh(secondEditor)
            rime.runOnReady {
                check(selectCandidate(TOTAL - 1, true, selectable)) { "The last valid native candidate could not be selected" }
                check(getRawInput().isEmpty()) { "Valid candidate selection did not finish the native composition" }
            }
            val committed = rime.runOnReady { candidateSnapshotCached }
            val commitMessages = snapshotMessages(committed).filterIsInstance<RimeMessage.CommitTextMessage>()
            check(committed.editorToken == secondEditor && committed.generation > selectable.generation)
            check(commitMessages.single().data.text == candidateTexts.last()) { "Native candidate selection committed the wrong or duplicate text" }
            phase("the final native candidate selects once with the current editor and a new commit snapshot")
        }

        private suspend fun verifyFinishAndRebind() {
            val beforeFinish = fresh(secondEditor)
            val finishSource = CandidatesPagingSource(rime, TOTAL, 0, beforeFinish)
            rime.runOnReady {
                finishOwnedEditors(longArrayOf(firstEditor, secondEditor))
                check(!candidateSnapshotCached.isValid && getRawInput().isEmpty())
            }
            reject(beforeFinish, finishSource)
            rime.runOnReady { beginEditor(secondEditor, nullInputType = false, privacyMode = false) }
            val rebound = fresh(secondEditor)
            check(rebound.generation > beforeFinish.generation)
            response(rebound)
            reject(beforeFinish, finishSource)
            phase("finish and same-token rebind invalidate former native pages without reusing a generation")
        }
    }

    fun run(instrumentation: Instrumentation) {
        val result = Bundle().apply {
            putBoolean("ascii_tips_snapshot_captured", false)
            putBoolean("ascii_tips_restore_attempted", false)
            putBoolean("ascii_tips_restore_verified", false)
        }
        val sessionName = "${CandidateSnapshotProbe::class.java.name}-${android.os.Process.myPid()}"
        val prefix = "__t08_candidates_${UUID.randomUUID().toString().replace("-", "")}"
        val firstEditor = SystemClock.elapsedRealtimeNanos().coerceAtLeast(1L)
        val secondEditor = firstEditor + 1
        val tips = AppPrefs.defaultInstance().general.asciiSwitchTips
        var previousTips = false
        var previousTipsPresent = false
        val userConfig = File(DataManager.userDataDir, "user.yaml")
        var previousUserConfig: ByteArray? = null
        var userConfigCaptured = false
        var preferencesChanged = false
        var fixtureStarted = false
        var session: RimeSession? = null
        var originalSchema: String? = null
        var originalAscii = false
        var originalPaging = false
        var settingsCaptured = false
        var passed = false
        var failure: Throwable? = null
        var checks = 0
        val progress = NativeProbeProgress()

        fun phase(description: String) {
            checks++
            android.util.Log.i("TrimeRuntimeAudit", "T08: $description")
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS: T08 $description\n") })
        }

        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Candidate probe only runs on an emulator" }
            check(File(instrumentation.targetContext.getExternalFilesDir(null), "runtime-audit-dedicated").isFile) {
                "Requires a dedicated runtime-audit installation"
            }
            check(RimeDaemon.getFirstSessionOrNull() == null) { "Candidate probe requires an idle Rime daemon" }
            runBlocking {
                val state = withTimeout(120_000) {
                    RimeDaemon.engineState.first { it == RimeLifecycle.State.STOPPED || it == RimeLifecycle.State.FAILED }
                }
                check(state == RimeLifecycle.State.STOPPED) { "Previous native editor did not shut down cleanly" }
            }
            check(RimeDaemon.getFirstSessionOrNull() == null) { "Another Rime client appeared before candidate probe setup" }
            previousUserConfig = userConfig.takeIf(File::exists)?.readBytes()
            userConfigCaptured = true
            instrumentation.runOnMainSync {
                val previousRawTips = tips.sharedPreferences.all
                previousTipsPresent = previousRawTips.containsKey(tips.key)
                val previousRawValue = previousRawTips[tips.key] as? Boolean
                check(!previousTipsPresent || previousRawValue != null) { "ASCII-tip preference is not a Boolean" }
                previousTips = previousRawValue ?: tips.defaultValue
                result.putString("ascii_tips_key", tips.key)
                result.putBoolean("ascii_tips_snapshot_captured", true)
                result.putBoolean("ascii_tips_before_present", previousTipsPresent)
                result.putBoolean("ascii_tips_before_effective", previousTips)
                if (previousTipsPresent) result.putBoolean("ascii_tips_before_raw_boolean", previousTips)
                preferencesChanged = true
                val overrideCommitted = tips.sharedPreferences.edit().putBoolean(tips.key, false).commit()
                result.putBoolean("ascii_tips_override_commit", overrideCommitted)
                val overridden = tips.sharedPreferences.all
                result.putBoolean("ascii_tips_override_present", overridden.containsKey(tips.key))
                val overrideValue = overridden[tips.key]
                if (overrideValue is Boolean) result.putBoolean("ascii_tips_override_raw_boolean", overrideValue)
                check(overrideCommitted && overridden.containsKey(tips.key) && overrideValue == false && !tips.getValue()) {
                    "Could not persist the dedicated probe's temporary ASCII-tip preference"
                }
                session = RimeDaemon.createSession(sessionName)
            }
            val rime = checkNotNull(session)
            runBlocking {
                withTimeout(300_000) {
                    rime.runOnReady { check(isReady) }
                    // Reflect the existing worker, never create a second native engine or add a
                    // production test API. Fixture deployment uses that same confined context.
                    val realRime = RimeDaemon::class.java.getDeclaredMethod("getRealRime").apply {
                        isAccessible = true
                    }.invoke(RimeDaemon) as Rime
                    val dispatcher = Rime::class.java.getDeclaredField("dispatcher").apply {
                        isAccessible = true
                    }.get(realRime) as RimeDispatcher
                    val messages = ArrayDeque<RimeMessage<*>>()
                    val observer = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                        rime.run { messageFlow }.collect { message ->
                            synchronized(messages) {
                                if (messages.size == 4096) messages.removeFirst()
                                messages.addLast(message)
                            }
                        }
                    }
                    try {
                        dispatcher.runConfined {
                            rime.runOnReady {
                                originalSchema = selectedSchemaId().also { check(it.isNotBlank()) }
                                originalAscii = getRuntimeOption("ascii_mode")
                                originalPaging = Rime::class.java.getDeclaredField("pagingMode").apply {
                                    isAccessible = true
                                }.getBoolean(realRime)
                                settingsCaptured = true
                                fixtureStarted = true
                                check(Rime.deployRimeSchemaFile(writeFixture(prefix).absolutePath)) {
                                    "Temporary candidate schema deployment failed"
                                }
                                check(selectSchema(prefix)) { "Temporary candidate schema selection failed" }
                                setRuntimeOption("ascii_mode", false)
                                setCandidatePagingMode(false)
                                beginEditor(firstEditor, nullInputType = false, privacyMode = false)
                                clearComposition()
                            }
                        }

                        NativeCandidateProbeContext(
                            rime,
                            dispatcher,
                            messages,
                            firstEditor,
                            secondEditor,
                            progress,
                            ::phase,
                        ).verify()
                    } finally {
                        observer.cancelAndJoin()
                    }
                }
            }
            passed = true
        } catch (error: Throwable) {
            failure = error
        } finally {
            fun clean(block: () -> Unit) {
                runCatching(block).onFailure { error ->
                    failure?.addSuppressed(error) ?: run { failure = error }
                    passed = false
                }
            }
            clean {
                runBlocking {
                    withContext(NonCancellable) {
                        withTimeout(120_000) {
                            session?.runOnReady {
                                finishOwnedEditors(longArrayOf(firstEditor, secondEditor))
                                clearComposition()
                                if (settingsCaptured) {
                                    check(selectSchema(checkNotNull(originalSchema))) { "Could not restore the original schema" }
                                    setRuntimeOption("ascii_mode", originalAscii)
                                    setCandidatePagingMode(originalPaging)
                                }
                            }
                        }
                    }
                }
            }
            var nativeStopped = session == null
            clean {
                session?.let {
                    RimeDaemon.destroySession(sessionName)
                    session = null
                    runBlocking {
                        val state = withTimeout(120_000) {
                            RimeDaemon.engineState.first { it == RimeLifecycle.State.STOPPED || it == RimeLifecycle.State.FAILED }
                        }
                        nativeStopped = true
                        check(state == RimeLifecycle.State.STOPPED) { "Native candidate probe did not finish with a clean shutdown" }
                    }
                }
            }
            // Never remove dictionaries or replace user.yaml while native Rime could still own them.
            if (nativeStopped) {
                if (fixtureStarted) clean { removeFixture(prefix) }
                if (userConfigCaptured) {
                    clean {
                        previousUserConfig?.let(userConfig::writeBytes) ?: check(!userConfig.exists() || userConfig.delete()) {
                            "Could not remove the probe-created user configuration"
                        }
                    }
                }
            }
            if (preferencesChanged) {
                clean {
                    instrumentation.runOnMainSync {
                        result.putBoolean("ascii_tips_restore_attempted", true)
                        val editor = tips.sharedPreferences.edit()
                        if (previousTipsPresent) editor.putBoolean(tips.key, previousTips) else editor.remove(tips.key)
                        val restoreCommitted = editor.commit()
                        result.putBoolean("ascii_tips_restore_commit", restoreCommitted)
                        val restored = tips.sharedPreferences.all
                        val restoredPresent = restored.containsKey(tips.key)
                        val restoredRawValue = restored[tips.key]
                        result.putBoolean("ascii_tips_after_present", restoredPresent)
                        if (restoredRawValue is Boolean) result.putBoolean("ascii_tips_after_raw_boolean", restoredRawValue)
                        val restoredEffective = when {
                            !restoredPresent -> tips.defaultValue
                            restoredRawValue is Boolean -> restoredRawValue
                            else -> null
                        }
                        if (restoredEffective != null) result.putBoolean("ascii_tips_after_effective", restoredEffective)
                        val restoredExactly = restoreCommitted && restoredPresent == previousTipsPresent &&
                            (!previousTipsPresent || restoredRawValue == previousTips) && restoredEffective == previousTips && tips.getValue() == previousTips
                        result.putBoolean("ascii_tips_restore_verified", restoredExactly)
                        check(restoredExactly) { "ASCII-tip preference presence/value was not durably restored" }
                    }
                }
            }
        }
        result.putBoolean("passed", passed)
        result.putInt("checks", checks)
        result.putInt("native_fifo_keys", progress.fifoKeys)
        result.putInt("fixture_candidates", TOTAL)
        result.putString(
            "stream",
            failure?.let { "FAIL: ${it.stackTraceToString()}\n" }
                ?: "PASS: T08 $checks native snapshot/paging checks, ${progress.fifoKeys} FIFO keys; no UI drawing or physical performance claim\n",
        )
        instrumentation.finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }
}
