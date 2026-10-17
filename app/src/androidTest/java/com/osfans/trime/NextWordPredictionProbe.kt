// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.preference.PreferenceManager
import com.osfans.trime.core.CandidateSnapshot
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.core.InlinePreeditProto
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeDispatcher
import com.osfans.trime.core.RimeLifecycle
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.core.InlinePreeditMode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID

/** Isolated real APK-engine acceptance; never modifies the shipped model or real dictionaries. */
object NextWordPredictionProbe {
    private const val RETURN = 0xff0d
    private const val BACKSPACE = 0xff08

    private data class SavedPreference(
        val preferences: SharedPreferences,
        val key: String,
        val present: Boolean,
        val value: Any?,
    ) {
        @SuppressLint("ApplySharedPref", "UseKtx")
        fun restore() {
            val editor = preferences.edit()
            if (present) {
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is String -> editor.putString(key, value)
                    else -> error("Invalid saved probe preference")
                }
            } else {
                editor.remove(key)
            }
            check(editor.commit()) { "Failed to restore the prediction preference" }
            check(preferences.contains(key) == present && preferences.all[key] == value)
        }

        companion object {
            fun capture(preferences: SharedPreferences, key: String): SavedPreference {
                val present = preferences.contains(key)
                val value = preferences.all[key]
                check(!present || value is Boolean || value is String) { "Invalid original probe preference" }
                return SavedPreference(preferences, key, present, value)
            }
        }
    }

    private data class ObservedCandidates(val snapshot: CandidateSnapshot, val prediction: Boolean, val paged: Boolean)

    private fun fixtures(prefix: String): List<File> {
        val root = DataManager.userDataDir
        val model = File(DataManager.sharedDataDir, "trime_predict.db")
        check(model.isFile) { "The shipped prediction model is absent" }
        File(root, "${prefix}_model.db").apply {
            check(createNewFile())
            writeBytes(model.readBytes())
        }
        File(root, "${prefix}_bad.db").apply {
            check(createNewFile())
            writeText("not a prediction database\n")
        }
        check(!File(root, "${prefix}_absent.db").exists())
        File(root, "$prefix.dict.yaml").apply {
            check(createNewFile())
            writeText(
                "---\nname: $prefix\nversion: \"1\"\nsort: by_weight\nuse_preset_vocabulary: false\n...\n" +
                    "你\tni\t100\n妮\tni\t90\n泥\tni\t80\n好\thao\t100\n你好\tni hao\t1000\n世\tshi\t100\n界\tjie\t100\n世界\tshi jie\t1000\n" +
                    "收\tshou\t100\n到\tdao\t100\n收到\tshou dao\t1000\n",
            )
        }
        return listOf("full", "t9", "zero", "cap", "filtered", "missing", "corrupt").map { suffix ->
            val schema = "${prefix}_$suffix"
            val nine = suffix == "t9"
            val db = when (suffix) {
                "missing" -> "absent"
                "corrupt" -> "bad"
                else -> "model"
            }
            val limits = when (suffix) {
                "cap" -> "  max_candidates: 999\n  max_iterations: 999\n"
                "zero" -> "  max_candidates: 0\n  max_iterations: 0\n"
                else -> "  max_candidates: 5\n  max_iterations: 1\n"
            }
            val algebra = if (nine) {
                "  algebra:\n    - derive/^(.*)$/\\U$1/\n" +
                    "    - xlit/ABCDEFGHIJKLMNOPQRSTUVWXYZ/22233344455566677778889999/\n" +
                    "    - derive/^([a-z]+)$/~$1~/\n"
            } else {
                ""
            }
            File(root, "$schema.schema.yaml").apply {
                check(createNewFile())
                writeText(
                    "schema:\n  schema_id: $schema\n  name: T11 $suffix probe\n  version: \"1\"\n" +
                        "trime:\n  t9: $nine\n" +
                        "switches:\n  - name: ascii_mode\n    reset: 0\n    states: [native, ascii]\n" +
                        "  - name: prediction\n    reset: 0\n    states: [off, on]\n" +
                        "engine:\n  processors: [ascii_composer, predictor, speller, punctuator, selector, navigator, express_editor]\n" +
                        "  segmentors: [ascii_segmentor, abc_segmentor, punct_segmentor, fallback_segmentor]\n" +
                        "  translators: [predict_translator, punct_translator, script_translator]\n" +
                        (if (suffix == "filtered") "  filters: [simplifier, uniquifier]\n" else "") +
                        "speller:\n  alphabet: abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ987654321~\n" +
                        "  delimiter: \" '\"\n" + algebra +
                        "translator:\n  dictionary: $prefix\n  prism: $schema\n  enable_user_dict: false\n" +
                        "  enable_sentence: false\n  enable_completion: false\n" +
                        "predictor:\n  db: ${prefix}_$db.db\n" + limits +
                        "ascii_composer:\n  switch_key:\n    Shift_L: inline_ascii\n    Shift_R: commit_text\n" +
                        "punctuator:\n  half_shape:\n    '.': '。'\n  full_shape:\n    '.': '。'\n" +
                        "simplifier:\n  opencc_config: t2s.json\n" +
                        "menu:\n  page_size: 5\n",
                )
            }
        }
    }

    fun run(instrumentation: Instrumentation) {
        val result = Bundle()
        val prefix = "__t11_${UUID.randomUUID().toString().replace("-", "")}"
        val owner = "${NextWordPredictionProbe::class.java.name}-$prefix"
        val firstEditor = SystemClock.elapsedRealtimeNanos().coerceAtLeast(1L)
        val secondEditor = firstEditor + 1
        val userConfig = File(DataManager.userDataDir, "user.yaml")
        val pinFile = File(DataManager.userDataDir, ".trime-candidate-pins.json")
        val pinFiles = listOf(pinFile, File(pinFile.path + ".bak"), File(pinFile.path + ".new"))
        val originalPins = mutableMapOf<File, ByteArray?>()
        var originalConfig: ByteArray? = null
        var originalSchema: String? = null
        var originalAscii = false
        var originalPaging = false
        val savedPreferences = mutableListOf<SavedPreference>()
        var captured = false
        var fixtureStarted = false
        var session: RimeSession? = null
        var failure: Throwable? = null
        var checks = 0
        val commits = mutableListOf<String>()
        val keys = mutableListOf<Pair<Long, Int>>()
        val observedGeneration = MutableStateFlow(0L)
        val observedCandidates = MutableStateFlow<ObservedCandidates?>(null)
        val observedInline = MutableStateFlow<Pair<CandidateSnapshot, InlinePreeditProto>?>(null)
        val observedComposition = MutableStateFlow<Pair<CandidateSnapshot, CompositionProto>?>(null)

        fun phase(description: String) {
            checks++
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS: T11 $description\n") })
        }

        suspend fun stopped() {
            val state = withTimeout(120_000) {
                RimeDaemon.engineState.first { it == RimeLifecycle.State.STOPPED || it == RimeLifecycle.State.FAILED }
            }
            check(state == RimeLifecycle.State.STOPPED) { "Prediction probe did not stop the engine cleanly" }
        }

        fun dispatcher(): RimeDispatcher {
            val real = RimeDaemon::class.java.getDeclaredMethod("getRealRime").apply { isAccessible = true }.invoke(RimeDaemon) as Rime
            return Rime::class.java.getDeclaredField("dispatcher").apply { isAccessible = true }.get(real) as RimeDispatcher
        }

        fun count(): Int = synchronized(commits) { commits.size }

        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish"))
            check(File(instrumentation.targetContext.getExternalFilesDir(null), "runtime-audit-dedicated").isFile)
            check(RimeDaemon.getFirstSessionOrNull() == null) { "Prediction probe requires an idle daemon" }
            runBlocking { stopped() }
            originalConfig = userConfig.takeIf(File::exists)?.readBytes()
            pinFiles.forEach { file ->
                check(!file.exists() || (file.isFile && file.length() <= 2 * 1024 * 1024)) { "Invalid original candidate pin storage" }
                originalPins[file] = file.takeIf(File::exists)?.readBytes()
            }
            val preference = AppPrefs.defaultInstance().candidates.nextWordPrediction
            preference.getValue() // Validate the original type before changing any preference.
            savedPreferences.add(SavedPreference.capture(preference.sharedPreferences, preference.key))
            val preeditPreference = AppPrefs.defaultInstance().general.inlinePreeditMode
            preeditPreference.getValue()
            savedPreferences.add(SavedPreference.capture(preeditPreference.sharedPreferences, preeditPreference.key))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val devicePreferences = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext.createDeviceProtectedStorageContext())
                savedPreferences.add(
                    SavedPreference.capture(
                        devicePreferences,
                        preference.key,
                    ),
                )
                savedPreferences.add(SavedPreference.capture(devicePreferences, preeditPreference.key))
            }
            captured = true
            instrumentation.runOnMainSync { session = RimeDaemon.createSession(owner) }
            runBlocking {
                withTimeout(300_000) {
                    val rime = checkNotNull(session)
                    rime.runOnReady {
                        originalSchema = selectedSchemaId()
                        originalAscii = getRuntimeOption("ascii_mode")
                    }
                    dispatcher().runConfined {
                        val real = RimeDaemon::class.java.getDeclaredMethod("getRealRime").apply { isAccessible = true }.invoke(RimeDaemon)
                        originalPaging = Rime::class.java.getDeclaredField("pagingMode").apply { isAccessible = true }.getBoolean(real)
                    }
                    dispatcher().runConfined {
                        fixtureStarted = true
                        fixtures(prefix).forEach { check(Rime.deployRimeSchemaFile(it.absolutePath)) }
                    }
                    val observer = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                        rime.run { messageFlow }.collect { message ->
                            when (message) {
                                is RimeMessage.CommitTextMessage -> if (!message.data.text.isNullOrEmpty()) {
                                    synchronized(commits) { commits.add(checkNotNull(message.data.text)) }
                                }

                                is RimeMessage.BulkCandidatesMessage -> observedCandidates.value = ObservedCandidates(message.snapshot, message.data.isPrediction, false)

                                is RimeMessage.PagedCandidatesMessage -> observedCandidates.value = ObservedCandidates(message.snapshot, message.data.isPrediction, true)

                                is RimeMessage.InlinePreeditMessage -> observedInline.value = message.snapshot to message.data

                                is RimeMessage.CompositionMessage -> observedComposition.value = message.snapshot to message.data

                                is RimeMessage.T9Message -> observedGeneration.value = maxOf(observedGeneration.value, message.snapshot.generation)

                                is RimeMessage.KeyMessage -> synchronized(keys) { keys.add(message.snapshot.generation to message.data.value.value) }

                                else -> Unit
                            }
                        }
                    }
                    try {
                        suspend fun barrier(): CandidateSnapshot {
                            val snapshot = rime.runOnReady { candidateSnapshotCached }
                            check(snapshot.generation > 0)
                            // The ordered bus emits commits and candidates before the final T9
                            // message. Reaching this generation proves they were consumed.
                            withTimeout(10_000) { observedGeneration.first { it >= snapshot.generation } }
                            return snapshot
                        }

                        suspend fun exactCommit(before: Int, expected: String) {
                            barrier()
                            check(synchronized(commits) { commits.drop(before) } == listOf(expected)) {
                                "The engine did not commit exactly the expected text once"
                            }
                        }

                        suspend fun preference(enabled: Boolean) {
                            instrumentation.runOnMainSync { AppPrefs.defaultInstance().candidates.nextWordPrediction.setValue(enabled) }
                            rime.runOnReady { refreshPredictionOptions() }
                        }

                        suspend fun word(suffix: String, input: String, target: String): CandidateSnapshot {
                            val before = count()
                            rime.runOnReady {
                                check(selectSchema("${prefix}_$suffix"))
                                setRuntimeOption("ascii_mode", false)
                                setRuntimeOption("ascii_punct", false)
                                setRuntimeOption("simplification", suffix == "filtered")
                                beginEditor(firstEditor, nullInputType = false, privacyMode = false)
                                setCandidatePagingMode(false)
                                clearComposition()
                                input.forEach { check(processKey(it.code, 0u, true)) }
                                val snapshot = candidateSnapshotCached
                                check(!snapshot.isPrediction && snapshot.isValid)
                                val index = getCandidates(0, 32, snapshot).indexOfFirst { it.text == target }
                                check(index >= 0) { "Fixture word is absent in $suffix" }
                                check(selectCandidate(index, true, snapshot))
                            }
                            exactCommit(before, target)
                            return barrier()
                        }

                        suspend fun hello(suffix: String = "full"): CandidateSnapshot = word(suffix, if (suffix == "t9") "64426" else "ni'hao", "你好")

                        suspend fun noCommit(before: Int) {
                            barrier()
                            check(count() == before) { "An ordinary key unexpectedly committed a prediction" }
                        }

                        suspend fun switchInternally(target: SchemaItem) {
                            rime.runOnReady {
                                check(processKey(0xffc1, 0u, true)) { "Native F4 switcher did not open" }
                                val menu = candidateSnapshotCached
                                check(!menu.isPrediction && hasMenu)
                                val rows = getCandidates(0, 128, menu)
                                val row = rows.indexOfFirst { it.text == target.name }
                                check(row >= 0) { "The deployed target schema is absent from the real switcher" }
                                check(selectCandidate(row, true, menu))
                                check(selectedSchemaId() == target.id)
                                check(getRawInput().isEmpty() && !candidateSnapshotCached.isPrediction)
                            }
                            barrier()
                        }

                        suspend fun verifyPredictionMenus() {
                            preference(false)
                            check(!hello().isPrediction)
                            rime.runOnReady { check(!hasMenu && getRawInput().isEmpty()) }
                            phase("disabled prediction still commits the ordinary Chinese greeting once")

                            preference(true)
                            for (mode in InlinePreeditMode.entries) {
                                instrumentation.runOnMainSync { preeditPreference.setValue(mode) }
                                for (suffix in listOf("full", "t9")) {
                                    val predicted = hello(suffix)
                                    check(predicted.isPrediction)
                                    check(observedInline.value == (predicted to InlinePreeditProto("")))
                                    check(observedComposition.value == (predicted to CompositionProto()))
                                }
                                phase("$mode keeps full-pinyin and T9 predictions out of editor preedit and commit preview")
                            }
                            for (suffix in listOf("full", "t9", "zero")) {
                                val predicted = hello(suffix)
                                val beforeUnidentified = count()
                                rime.runOnReady {
                                    check(predicted.isPrediction && predicted.isValid && getRawInput().isEmpty())
                                    val rows = getCandidates(0, 16, predicted)
                                    check(rows.size == 5 && rows.first().text == "世界")
                                    check(observedCandidates.value == ObservedCandidates(predicted, true, false))
                                    check(!selectCandidate(0, true, null)) { "Unidentified theme selection committed a suggestion" }
                                }
                                noCommit(beforeUnidentified)
                                val before = count()
                                rime.runOnReady { check(selectCandidate(0, true, predicted)) }
                                exactCommit(before, "世界")
                                rime.runOnReady { check(!hasMenu && !candidateSnapshotCached.isPrediction) }
                                phase("$suffix exact five next-word suggestions; only a current candidate click commits; one-round chain stops")
                            }
                        }
                        verifyPredictionMenus()

                        suspend fun verifyOrdinaryKeys() {
                            for (key in listOf(RETURN, ' '.code, BACKSPACE)) {
                                val stale = hello()
                                val before = count()
                                val generation = rime.runOnReady {
                                    check(!processKey(key, 0u, true)) { "The ordinary editor key was swallowed" }
                                    check(!candidateSnapshotCached.isPrediction)
                                    check(!selectCandidate(0, true, stale))
                                    candidateSnapshotCached.generation
                                }
                                noCommit(before)
                                withTimeout(10_000) {
                                    while (synchronized(keys) { keys.count { it == (generation to key) } } == 0) delay(10)
                                }
                                check(synchronized(keys) { keys.count { it == (generation to key) } } == 1)
                                phase("key $key cancels prediction without committing it or accepting its stale row")
                            }

                            val stalePunctuation = hello()
                            val beforePunctuation = count()
                            rime.runOnReady {
                                check(processKey('.'.code, 0u, true))
                                check(!candidateSnapshotCached.isPrediction)
                                check(!selectCandidate(0, true, stalePunctuation))
                            }
                            exactCommit(beforePunctuation, "。")
                            phase("punctuation clears prediction and commits only the configured Chinese full stop")

                            val staleShift = hello()
                            val beforeShift = count()
                            rime.runOnReady {
                                processKey(0xffe2, 0u, isVirtual = false)
                                processKey(0xffe2, 1u shl 30, isVirtual = false)
                                check(getRuntimeOption("ascii_mode")) { "Physical right Shift lost its normal ASCII toggle" }
                                check(!candidateSnapshotCached.isPrediction && getRawInput().isEmpty())
                                check(!selectCandidate(0, true, staleShift))
                            }
                            noCommit(beforeShift)
                            phase("physical right Shift press and release preserve ASCII switching without committing a suggestion")

                            for (macro in listOf("{Return}", "{Shift_R}{Release+Shift_R}")) {
                                val stale = hello()
                                val before = count()
                                rime.runOnReady {
                                    simulateKeySequence(macro)
                                    check(!candidateSnapshotCached.isPrediction)
                                    check(!selectCandidate(0, true, stale))
                                }
                                noCommit(before)
                                phase("control macro $macro clears prediction without committing a suggestion or its command source")
                            }
                        }
                        verifyOrdinaryKeys()

                        suspend fun verifyPagingAndNewT9() {
                            hello()
                            val pagedSnapshot = rime.runOnReady {
                                setCandidatePagingMode(true)
                                candidateSnapshotCached
                            }
                            barrier()
                            check(pagedSnapshot.isPrediction && observedCandidates.value == ObservedCandidates(pagedSnapshot, true, true))
                            val beforePaged = count()
                            rime.runOnReady { check(selectCandidate(0, true, pagedSnapshot)) }
                            exactCommit(beforePaged, "世界")
                            rime.runOnReady { setCandidatePagingMode(false) }
                            phase("Paged JNI snapshot marks suggestions and a current row commits exactly once")

                            val staleT9 = hello("t9")
                            val beforeDigit = count()
                            rime.runOnReady {
                                check(processKey('2'.code, 0u, true))
                                check(getRawInput() == "2" && t9Cached.input == "2" && !candidateSnapshotCached.isPrediction)
                                check(!selectCandidate(0, true, staleT9))
                            }
                            noCommit(beforeDigit)
                            phase("digit immediately starts real native T9 input and rejects the old suggestion")

                            preference(false)
                            rime.runOnReady { check(getRawInput() == "2" && t9Cached.input == "2") }
                            preference(true)
                            rime.runOnReady { check(getRawInput() == "2" && t9Cached.input == "2" && !candidateSnapshotCached.isPrediction) }
                            noCommit(beforeDigit)
                            phase("disabling and re-enabling suggestions preserves the unfinished T9 input")
                        }
                        verifyPagingAndNewT9()

                        suspend fun verifyRankingIsolation() {
                            rime.runOnReady {
                                check(selectSchema("${prefix}_full"))
                                setRuntimeOption("ascii_mode", false)
                                beginEditor(firstEditor, nullInputType = false, privacyMode = false)
                                clearComposition()
                                "ni".forEach { check(processKey(it.code, 0u, true)) }
                            }
                            val baselineRanking = rime.runOnReady {
                                val snapshot = candidateSnapshotCached
                                check(!snapshot.isPrediction && getRawInput() == "ni")
                                getCandidates(0, 32, snapshot).map { it.text }
                            }
                            check(baselineRanking.size == 3 && baselineRanking.toSet() == setOf("你", "妮", "泥"))
                            val pinnedText = baselineRanking.last()
                            val pinnedRanking = listOf(pinnedText) + baselineRanking.dropLast(1)
                            val beforeRanking = count()
                            for (togglePreference in listOf(false, true)) {
                                rime.runOnReady { check(pinCandidate(2, true, candidateSnapshotCached)) }
                                repeat(100) {
                                    if (togglePreference) {
                                        preference(false)
                                        preference(true)
                                    } else {
                                        rime.runOnReady { clearPredictionContext() }
                                    }
                                    rime.runOnReady {
                                        val snapshot = candidateSnapshotCached
                                        check(!snapshot.isPrediction && getRawInput() == "ni")
                                        check(getCandidates(0, 32, snapshot).map { it.text } == pinnedRanking)
                                    }
                                }
                                rime.runOnReady {
                                    check(resetCandidateRanking(candidateSnapshotCached))
                                    val snapshot = candidateSnapshotCached
                                    check(getRawInput() == "ni" && !snapshot.isPrediction)
                                    check(getCandidates(0, 32, snapshot).map { it.text } == baselineRanking) {
                                        "Prediction cleanup left a nested pinned menu after ranking reset"
                                    }
                                }
                                noCommit(beforeRanking)
                                phase(
                                    if (togglePreference) {
                                        "100 prediction preference cycles preserve unfinished input and restore ordinary candidate ranking"
                                    } else {
                                        "100 prediction context clears preserve unfinished input and restore ordinary candidate ranking"
                                    },
                                )
                            }
                            rime.runOnReady { check(selectCandidate(1, true, candidateSnapshotCached)) }
                            exactCommit(beforeRanking, baselineRanking[1])
                            phase("ordinary candidate selection commits the displayed text exactly once after prediction cleanup and ranking reset")
                        }
                        verifyRankingIsolation()

                        suspend fun verifyEditorPrivacyPreferences() {
                            val oldEditor = hello()
                            val beforeBoundary = count()
                            rime.runOnReady {
                                beginEditor(secondEditor, nullInputType = false, privacyMode = false)
                                check(!hasMenu && getRawInput().isEmpty() && !candidateSnapshotCached.isPrediction)
                                check(!selectCandidate(0, true, oldEditor))
                                check(processKey('n'.code, 0u, true))
                                check(getRawInput() == "n")
                            }
                            noCommit(beforeBoundary)
                            phase("editor replacement drops prediction context and preserves the new editor input")

                            val oldPrivacy = hello()
                            rime.runOnReady {
                                beginEditor(firstEditor, nullInputType = false, privacyMode = true)
                                check(!hasMenu && !candidateSnapshotCached.isPrediction)
                                check(!selectCandidate(0, true, oldPrivacy))
                            }
                            preference(true)
                            rime.runOnReady { check(!hasMenu && !getRuntimeOption("prediction")) }
                            phase("privacy overrides the enabled preference and cannot restore an old prediction")

                            hello()
                            preference(false)
                            rime.runOnReady { check(!hasMenu && !candidateSnapshotCached.isPrediction) }
                            preference(true)
                            rime.runOnReady { check(!hasMenu) }
                            phase("turning prediction off clears it; re-enabling does not reuse the old commit")
                        }
                        verifyEditorPrivacyPreferences()

                        suspend fun verifyModelLimitsAndFiltering() {
                            for (suffix in listOf("missing", "corrupt")) {
                                check(!hello(suffix).isPrediction)
                                rime.runOnReady { check(!hasMenu) }
                                phase("$suffix model preserves ordinary Chinese input and exact commit")
                            }

                            val capped = hello("cap")
                            rime.runOnReady {
                                check(capped.isPrediction)
                                check(getCandidates(0, 32, capped).size == 8) { "Unbounded config exceeded the hard candidate budget" }
                            }
                            phase("oversized schema configuration respects the hard eight-candidate budget")
                            for ((index, target) in listOf("世界", "你好").withIndex()) {
                                val before = count()
                                rime.runOnReady {
                                    val snapshot = candidateSnapshotCached
                                    check(snapshot.isPrediction)
                                    val row = getCandidates(0, 16, snapshot).indexOfFirst { it.text == target }
                                    check(row >= 0 && selectCandidate(row, true, snapshot))
                                }
                                exactCommit(before, target)
                                if (index == 1) rime.runOnReady { check(!hasMenu && !candidateSnapshotCached.isPrediction) }
                            }
                            phase("oversized iteration configuration stops after two clicks despite a valid third-round model entry")

                            val filtered = word("filtered", "shou'dao", "收到")
                            rime.runOnReady {
                                check(filtered.isPrediction)
                                check(getCandidates(0, 16, filtered).map { it.text } == listOf("谢谢", "稍后回复")) {
                                    "Real simplifier and uniquifier did not convert and merge the model rows"
                                }
                            }
                            val beforeFiltered = count()
                            rime.runOnReady { check(selectCandidate(0, true, filtered)) }
                            exactCommit(beforeFiltered, "谢谢")
                            rime.runOnReady { check(!hasMenu && !candidateSnapshotCached.isPrediction) }
                            phase("real simplification and deduplication preserve the one-round cap despite a matching follow-up model entry")
                        }
                        verifyModelLimitsAndFiltering()

                        suspend fun verifyInternalSchemaSwitching() {
                            val beforeSwitcher = hello()
                            val targetSchema = rime.runOnReady {
                                selectedSchemata().firstOrNull { it.id in setOf("luna_pinyin", "luna_pinyin_simp", "luna_pinyin_t9") }
                            }
                            checkNotNull(targetSchema) { "No deployed bundled prediction schema is available" }
                            val beforeInternal = count()
                            switchInternally(targetSchema)
                            rime.runOnReady {
                                check(getRuntimeOption("prediction")) { "Internal schema switching lost the enabled user preference" }
                                check(!selectCandidate(0, true, beforeSwitcher))
                            }
                            noCommit(beforeInternal)
                            phase("real native F4 switcher clears the old suggestion and restores the enabled preference after schema reset")

                            hello()
                            preference(false)
                            val beforeDisabledSwitcher = count()
                            switchInternally(targetSchema)
                            rime.runOnReady { check(!getRuntimeOption("prediction") && !hasMenu) }
                            noCommit(beforeDisabledSwitcher)
                            phase("real native internal schema switching preserves the disabled preference without replaying a commit")
                        }
                        verifyInternalSchemaSwitching()
                    } finally {
                        observer.cancelAndJoin()
                    }
                }
            }
        } catch (error: Throwable) {
            failure = error
        } finally {
            fun clean(block: () -> Unit) {
                try {
                    block()
                } catch (error: Throwable) {
                    failure?.addSuppressed(error) ?: run { failure = error }
                }
            }
            clean {
                runBlocking {
                    withTimeout(30_000) {
                        session?.runOnReady {
                            clearPredictionContext()
                            finishEditor(firstEditor)
                            finishEditor(secondEditor)
                            originalSchema?.let { check(selectSchema(it)) }
                            setRuntimeOption("ascii_mode", originalAscii)
                            setCandidatePagingMode(originalPaging)
                        }
                    }
                }
            }
            if (session != null) {
                clean {
                    RimeDaemon.destroySession(owner)
                    session = null
                }
            }
            var nativeStopped = false
            clean {
                runBlocking { stopped() }
                nativeStopped = true
            }
            if (nativeStopped && fixtureStarted) clean { PrivacyModeProbe.removeOwnedFiles(prefix) }
            if (nativeStopped && captured) {
                clean {
                    pinFiles.forEach { file ->
                        originalPins[file]?.let(file::writeBytes) ?: check(!file.exists() || file.delete())
                        check(originalPins[file]?.let { file.readBytes().contentEquals(it) } ?: !file.exists())
                    }
                }
                clean {
                    originalConfig?.let(userConfig::writeBytes) ?: check(!userConfig.exists() || userConfig.delete())
                    check(originalConfig?.let { userConfig.readBytes().contentEquals(it) } ?: !userConfig.exists())
                }
            }
            if (captured) {
                clean {
                    instrumentation.runOnMainSync { savedPreferences.forEach(SavedPreference::restore) }
                }
            }
        }
        result.putBoolean("passed", failure == null)
        result.putInt("checks", checks)
        result.putString("stream", failure?.let { "FAIL: ${it.stackTraceToString()}\n" } ?: "PASS: T11 $checks real APK prediction checks; no physical performance claim\n")
        instrumentation.finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }
}
