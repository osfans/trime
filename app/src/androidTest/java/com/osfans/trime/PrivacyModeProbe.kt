// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.preference.PreferenceManager
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeKeyEvent
import com.osfans.trime.core.RimeLifecycle
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.SymbolHistory
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.db.CollectionHelper
import com.osfans.trime.data.db.Database
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.db.PersistencePermitExpiredException
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.userdict.UserDictManager
import com.osfans.trime.ime.core.PrivacyMode
import com.osfans.trime.ime.core.PrivacyModeState
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import com.osfans.trime.ui.main.EditorProbeActivity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Emulator-only acceptance probe for T07. Every persistent fixture has a unique owned prefix. */
internal object PrivacyModeProbe {
    private const val SENSITIVE_CLIP_EXTRA = "android.content.extra.IS_SENSITIVE"
    private const val LOG_TAG = "T07PrivacyProbe"

    private fun feedbackField(name: String): Any? = InputFeedbackManager::class.java.getDeclaredField(name).let {
        it.isAccessible = true
        it.get(InputFeedbackManager)
    }

    @SuppressLint("ApplySharedPref")
    private fun commitBooleanPreferences(
        preferences: SharedPreferences,
        values: Map<String, Boolean>,
        label: String,
    ) {
        val editor = preferences.edit()
        for ((key, value) in values) editor.putBoolean(key, value)
        check(editor.commit()) { "Could not persist restored $label preferences" }
        for ((key, value) in values) {
            check(preferences.getBoolean(key, !value) == value) {
                "Restored $label preference did not persist: $key"
            }
        }
    }

    internal data class UserDbSnapshot(
        val entries: Int,
        val contents: String,
    )

    private fun ownLogcat(): String {
        // A process may read its own logd records without privileged READ_LOGS.
        // Avoid UiAutomation here: its output pipe can remain open on API 21.
        val process =
            ProcessBuilder("logcat", "-d", "-v", "threadtime", "*:V")
                .redirectErrorStream(true)
                .start()
        val result =
            FutureTask<Pair<String, Int>> {
                val output = process.inputStream.bufferedReader().use { it.readText() }
                output to process.waitFor()
            }
        val reader =
            Thread(result, "t07-privacy-logcat").apply {
                isDaemon = true
                start()
            }
        try {
            val (output, exitCode) = result.get(15, TimeUnit.SECONDS)
            check(exitCode == 0) { "Could not read the application log" }
            return output
        } catch (failure: TimeoutException) {
            throw IllegalStateException("Timed out while reading the application log", failure)
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw failure
        } finally {
            process.destroy()
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
            result.cancel(true)
            reader.interrupt()
            try {
                reader.join(1_000)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    /** Retain continuation lines only while their preceding threadtime record belongs to this process. */
    private fun logForPid(
        output: String,
        pid: Int,
    ): String {
        val header = Regex("^\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d+\\s+(\\d+)\\s+\\d+\\s+[VDIWEFAS]\\s+")
        return buildString {
            var owned = false
            output.lineSequence().forEach { line ->
                header.find(line)?.let { owned = it.groupValues[1] == pid.toString() }
                if (owned) appendLine(line)
            }
        }
    }

    /** Shared by the real-editor trigger matrix so both probes apply the same PID filtering. */
    internal fun ownProcessLogs(pid: Int): String = logForPid(ownLogcat(), pid)

    /** Export logical records after the fixture schema has released its LevelDB handle. */
    internal fun userDbSnapshot(prefix: String): UserDbSnapshot {
        val output = ByteArrayOutputStream()
        val entries =
            UserDictManager.exportUserDict(output, prefix, "$prefix-export.txt")
                .getOrThrow()
        return UserDbSnapshot(entries, output.toByteArray().toString(Charsets.UTF_8))
    }

    internal fun removeOwnedFiles(prefix: String) {
        val root = DataManager.userDataDir
        root.walkBottomUp()
            .filter { it != root && it.name.startsWith(prefix) }
            .forEach { check(!it.exists() || it.deleteRecursively()) { "Could not remove an owned privacy fixture" } }
        check(root.walkTopDown().none { it != root && it.name.startsWith(prefix) }) {
            "An owned privacy fixture remains"
        }
    }

    private fun luaGearEvents(marker: File): List<String> = if (!marker.exists() || marker.length() == 0L) {
        emptyList()
    } else {
        marker.readLines().filter { it.isNotBlank() }.map { it.substringBefore('\t') }
    }

    private fun writeRimeFixture(
        prefix: String,
        privateCandidate: String,
    ): File {
        val dictionary = File(DataManager.userDataDir, "$prefix.dict.yaml")
        val schema = File(DataManager.userDataDir, "$prefix.schema.yaml")
        val luaDirectory = File(DataManager.userDataDir, "lua")
        check(luaDirectory.exists() || luaDirectory.mkdirs()) { "Could not create the Lua fixture directory" }
        val luaModule = File(luaDirectory, "$prefix.lua")
        val luaMarker = File(DataManager.userDataDir, "$prefix.lua_gear_marker")
        check(dictionary.createNewFile() && schema.createNewFile() && luaModule.createNewFile()) {
            "Temporary Rime fixture already exists"
        }
        val markerPath = luaMarker.absolutePath.replace("\\", "\\\\").replace("'", "\\'")
        luaModule.writeText(
            """
            local probe = {}

            local function mark(event, env)
              local marker = assert(io.open('$markerPath', 'ab'))
              marker:write(event, '\t', env.engine.context.input, '\n')
              marker:close()
            end

            local function fini(event)
              return function(env)
                mark('fini_' .. event, env)
              end
            end

            probe.processor = {}
            function probe.processor.func(key, env)
              mark('processor:' .. key:repr(), env)
              return 2
            end
            probe.processor.fini = fini('processor')

            probe.segmentor = {}
            function probe.segmentor.func(segmentation, env)
              mark('segmentor', env)
              return true
            end
            probe.segmentor.fini = fini('segmentor')

            probe.translator = {}
            function probe.translator.func(input, segment, env)
              mark('translator:' .. input, env)
              yield(Candidate('lua_probe', segment.start, segment._end, 'lua_probe_decoy', ''))
            end
            probe.translator.fini = fini('translator')

            probe.filter = {}
            function probe.filter.tags_match(segment, env)
              mark('filter_tags_match', env)
              return true
            end
            function probe.filter.func(input, env, candidates)
              mark('filter', env)
              for candidate in input:iter() do
                yield(candidate)
              end
            end
            probe.filter.fini = fini('filter')

            return probe
            """.trimIndent() + "\n",
        )
        dictionary.writeText(
            """
            ---
            name: $prefix
            version: "1"
            sort: by_weight
            use_preset_vocabulary: false
            ...
            decoy	q	100
            $privateCandidate	q	1
            """.trimIndent() + "\n",
        )
        schema.writeText(
            """
            schema:
              schema_id: $prefix
              name: T07 privacy probe
              version: "1"

            switches:
              - name: ascii_mode
                reset: 0
                states: [ native, ascii ]

            engine:
              processors:
                - lua_processor@*$prefix*processor
                - ascii_composer
                - chord_composer
                - key_binder
                - speller
                - punctuator
                - selector
                - navigator
                - express_editor
              segmentors:
                - lua_segmentor@*$prefix*segmentor
                - ascii_segmentor
                - abc_segmentor
                - punct_segmentor
                - fallback_segmentor
              translators:
                - punct_translator
                - lua_translator@*$prefix*translator
                - table_translator
              filters:
                - lua_filter@*$prefix*filter
                - uniquifier

            speller:
              alphabet: q
              delimiter: " '"

            chord_composer:
              alphabet: x
              bindings:
                Return: commit_raw_input

            ascii_composer:
              switch_key:
                Shift_L: inline_ascii

            key_binder:
              bindings:
                - { when: always, accept: y, toggle: __privacy_probe_binding }

            punctuator:
              half_shape:
                '"': { pair: [ PAIR_OPEN, PAIR_CLOSE ] }

            translator:
              dictionary: $prefix
              enable_user_dict: true
              enable_sentence: false
              enable_completion: false
            """.trimIndent() + "\n",
        )
        return luaMarker
    }

    fun run(instrumentation: Instrumentation) {
        val result = Bundle()
        var passed = false
        var checks = 0
        val pid = android.os.Process.myPid()
        val fixturePrefix = "__t07_privacy_${pid}_${UUID.randomUUID().toString().replace("-", "")}"
        val sensitiveClip = "virtualotp${UUID.randomUUID().toString().replace("-", "")}"
        val appOwnedSensitiveClip = "trimesensitivelog${UUID.randomUUID().toString().replace("-", "")}"
        val secondAppOwnedSensitiveClip = "trimesensitivecrash${UUID.randomUUID().toString().replace("-", "")}"
        val privateClip = "virtualpin${UUID.randomUUID().toString().replace("-", "")}"
        val appRequestedClip = "virtualotp${UUID.randomUUID().toString().replace("-", "")}"
        val manualPrivacyClip = "virtualpin${UUID.randomUUID().toString().replace("-", "")}"
        val normalClip = "ordinarytext${UUID.randomUUID().toString().replace("-", "")}"
        val editableClip = "editableclip${UUID.randomUUID().toString().replace("-", "")}"
        val editableCollection = "editablecollection${UUID.randomUUID().toString().replace("-", "")}"
        val privateClipEdit = "privateclipedit${UUID.randomUUID().toString().replace("-", "")}"
        val privateCollectionEdit = "privatecollectionedit${UUID.randomUUID().toString().replace("-", "")}"
        val privateSymbol = "privatesymbol${UUID.randomUUID().toString().replace("-", "")}"
        val retainedSymbol = "retainedsymbol${UUID.randomUUID().toString().replace("-", "")}"
        val normalSymbol = "normalsymbol${UUID.randomUUID().toString().replace("-", "")}"
        val privateCandidate = "virtualpassword${UUID.randomUUID().toString().replace("-", "")}"
        val ordinarySpeech = "ordinaryspeech${UUID.randomUUID().toString().replace("-", "")}"
        val logBoundary = UUID.randomUUID().toString().replace("-", "")
        val privateLogStart = "T07_PRIVATE_BEGIN_$logBoundary"
        val privateLogEnd = "T07_PRIVATE_END_$logBoundary"
        val privacyOwner = System.nanoTime().let { if (it == 0L) 1L else it }
        val privateEditor = privacyOwner xor 0x51A7L
        val normalEditor = privacyOwner xor 0x6E0BL
        val secondNormalEditor = privacyOwner xor 0x71B2L
        val sessionName = "${PrivacyModeProbe::class.java.name}-$pid"
        var session: RimeSession? = null
        var clipboardForegroundActivity: Activity? = null
        var originalSchema: String? = null
        var privateEditorStarted = false
        var normalEditorStarted = false
        var stateStarted = false
        var enabledSchemaCount = 0
        var recordedFailure: Throwable? = null
        var feedbackPrepared = false
        var primaryClipboardCaptured = false
        var previousPrimaryClip: ClipData? = null
        val generalPrefs = AppPrefs.defaultInstance().general
        val clipboardPrefs = AppPrefs.defaultInstance().clipboard
        val feedbackPrefs = AppPrefs.defaultInstance().keyboard
        val previousManualPrivacy = generalPrefs.manualPrivacyMode.getValue()
        val previousClipboardListening = clipboardPrefs.clipboardListening.getValue()
        val previousSpeakOnKeyPress = feedbackPrefs.speakOnKeyPress.getValue()
        val previousSpeakOnCommit = feedbackPrefs.speakOnCommit.getValue()
        val previousLastBean = ClipboardHelper.lastBean
        val symbolHistoryFile =
            TrimeApplication.getInstance().directBootAwareContext.filesDir.resolve(SymbolHistory.FILE_NAME)
        val previousSymbolHistory = symbolHistoryFile.takeIf(File::exists)?.readBytes()
        val userConfigFile = File(DataManager.userDataDir, "user.yaml")
        val previousUserConfig = userConfigFile.takeIf(File::exists)?.readBytes()
        // API 21 constructs ClipboardManager with a Handler for the calling thread.
        // Instrumentation.onStart runs on a worker without a Looper, so create and
        // cache the framework service on the main thread before the probe uses it.
        val clipboard =
            java.util.concurrent.atomic.AtomicReference<ClipboardManager>().let { reference ->
                instrumentation.runOnMainSync {
                    reference.set(
                        instrumentation.targetContext.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager,
                    )
                }
                checkNotNull(reference.get())
            }
        val database = ClipboardHelper::class.java.getDeclaredField("clbDb").let {
            it.isAccessible = true
            it.get(ClipboardHelper) as Database
        }
        val dao = database.databaseDao()
        val collectionDatabase = CollectionHelper::class.java.getDeclaredField("cltDb").let {
            it.isAccessible = true
            it.get(CollectionHelper) as Database
        }
        val collectionDao = collectionDatabase.databaseDao()

        fun phase(name: String) {
            checks++
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS $checks: $name\n") })
        }

        fun assertClipboardReadable(expected: String) {
            check(clipboard.primaryClip?.getItemAt(0)?.text?.toString() == expected) {
                "Privacy probe cannot read its own clipboard; negative assertions would be inconclusive"
            }
        }

        fun setClipboard(clip: ClipData) {
            instrumentation.runOnMainSync {
                clipboard.setPrimaryClip(clip)
                assertClipboardReadable(checkNotNull(clip.getItemAt(0).text).toString())
                // The listener is asynchronous on some releases. Invoke the same production entry
                // point once so a negative assertion never passes because delivery was late.
                ClipboardHelper.onPrimaryClipChanged()
            }
        }

        suspend fun assertNotCaptured(text: String) {
            delay(750)
            check(dao.find(text) == null) { "Private clipboard content reached Room" }
            check(ClipboardHelper.lastBean?.text != text) { "Private clipboard content reached the in-memory suggestion" }
        }

        suspend fun awaitCaptured(text: String): DatabaseBean = withTimeout(15_000) {
            var captured: DatabaseBean? = null
            while (captured == null) {
                captured = dao.find(text)?.takeIf { ClipboardHelper.lastBean?.text == text }
                if (captured != null) break
                delay(50)
            }
            checkNotNull(captured)
        }

        fun assertSpeechRejected(text: String) {
            instrumentation.runOnMainSync {
                InputFeedbackManager.keyPressSpeak(KeyEvent.KEYCODE_A)
                InputFeedbackManager.textCommitSpeak(text)
                check(feedbackField("tts") == null) { "Private speech initialized TextToSpeech" }
                check(feedbackField("pendingSpeech") == null) { "Private content reached pending speech" }
            }
        }

        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Emulator only" }
            check(File(instrumentation.targetContext.getExternalFilesDir(null), "runtime-audit-dedicated").isFile) {
                "Requires a dedicated runtime-audit installation"
            }
            check(RimeDaemon.getFirstSessionOrNull() == null) { "Privacy probe requires an idle Rime daemon" }
            instrumentation.runOnMainSync {
                generalPrefs.manualPrivacyMode.setValue(false)
                clipboardPrefs.clipboardListening.setValue(true)
                PrivacyModeState.initialize(manual = false)
                ClipboardHelper.clearTransientSuggestion()
                feedbackPrepared = true
                InputFeedbackManager.destroy()
                feedbackPrefs.speakOnKeyPress.setValue(true)
                feedbackPrefs.speakOnCommit.setValue(true)
                InputFeedbackManager.startInput(sensitive = false)
                check(feedbackField("tts") == null && feedbackField("pendingSpeech") == null) {
                    "Idle speech fixture was not clean"
                }
            }

            runBlocking {
                withTimeout(600_000) {
                    // Android 13+ denies clipboard reads to a background app that is not
                    // the selected IME. Keep the daemon idle and give this probe its own
                    // focused Activity so negative assertions exercise a readable clip.
                    val foregroundActivity = instrumentation.startActivitySync(
                        Intent(instrumentation.targetContext, EditorProbeActivity::class.java)
                            .putExtra(EditorProbeActivity.EXTRA_CLIPBOARD_FOREGROUND, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    clipboardForegroundActivity = foregroundActivity
                    withTimeout(15_000) {
                        while (true) {
                            var focused = false
                            instrumentation.runOnMainSync {
                                focused = !foregroundActivity.isFinishing && foregroundActivity.hasWindowFocus()
                            }
                            if (focused) break
                            delay(50)
                        }
                    }
                    instrumentation.runOnMainSync {
                        val windowFlags = foregroundActivity.window.attributes.flags
                        check((windowFlags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM) != 0) {
                            "Clipboard foreground window must not bind an input method"
                        }
                        check(foregroundActivity.currentFocus?.onCheckIsTextEditor() == false) {
                            "Clipboard foreground fixture requires a focused non-editor view"
                        }
                    }
                    check(RimeDaemon.getFirstSessionOrNull() == null) {
                        "The clipboard foreground Activity unexpectedly started a Rime session"
                    }
                    instrumentation.runOnMainSync {
                        previousPrimaryClip = clipboard.primaryClip?.let(::ClipData)
                        primaryClipboardCaptured = true
                    }
                    Log.i(LOG_TAG, privateLogStart)
                    setClipboard(ClipData.newPlainText("editable test", editableClip))
                    val editableClipboardBean = awaitCaptured(editableClip)
                    phase("ordinary clipboard is readable and persists before exclusion checks")
                    instrumentation.runOnMainSync {
                        ClipboardHelper.setSensitivePrimaryClip(
                            clipboard,
                            ClipData.newPlainText("private app log", appOwnedSensitiveClip),
                        )
                        assertClipboardReadable(appOwnedSensitiveClip)
                        ClipboardHelper.setSensitivePrimaryClip(
                            clipboard,
                            ClipData.newPlainText("private app crash", secondAppOwnedSensitiveClip),
                        )
                        assertClipboardReadable(secondAppOwnedSensitiveClip)
                    }
                    instrumentation.waitForIdleSync()
                    assertNotCaptured(appOwnedSensitiveClip)
                    assertNotCaptured(secondAppOwnedSensitiveClip)
                    phase("consecutive app-owned sensitive clips are excluded on API 21 and later")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val clip = ClipData.newPlainText("private test", sensitiveClip)
                        val extras = clip.description.extras?.let(::PersistableBundle) ?: PersistableBundle()
                        extras.putBoolean(SENSITIVE_CLIP_EXTRA, true)
                        clip.description.extras = extras
                        setClipboard(clip)
                        assertNotCaptured(sensitiveClip)
                        phase("sensitive ClipDescription is excluded from history and suggestions")
                    } else {
                        phase("API 21 correctly skips unsupported sensitive ClipDescription metadata")
                    }

                    val luaGearMarker = writeRimeFixture(fixturePrefix, privateCandidate)
                    session = RimeDaemon.createSession(sessionName)
                    val activeSession = checkNotNull(session)
                    val beforePrivate = activeSession.runOnReady {
                        originalSchema =
                            selectedSchemaId().also {
                                check(it.isNotBlank()) { "Original schema has no identifier" }
                            }
                        check(Rime.deployRimeSchemaFile(File(DataManager.userDataDir, "$fixturePrefix.schema.yaml").path)) {
                            "Temporary privacy schema deployment failed"
                        }
                        check(selectSchema(fixturePrefix)) { "Temporary privacy schema selection failed" }
                        setRuntimeOption("ascii_mode", false)
                        clearComposition()
                        check(selectSchema(checkNotNull(originalSchema))) {
                            "Could not close the temporary privacy schema for its baseline export"
                        }
                        userDbSnapshot(fixturePrefix)
                    }
                    check(!beforePrivate.contents.contains(privateCandidate)) {
                        "The private fixture candidate existed before private input"
                    }
                    activeSession.runOnReady {
                        check(selectSchema(fixturePrefix)) { "Temporary privacy schema reselection failed" }
                        setRuntimeOption("ascii_mode", false)
                        clearComposition()
                    }
                    // The ordinary preflight teardown may run fixture fini hooks. Only events
                    // produced after this boundary belong to the private-input assertion.
                    luaGearMarker.writeBytes(ByteArray(0))
                    check(luaGearEvents(luaGearMarker).isEmpty()) {
                        "The Lua gear marker was not clean before private input"
                    }

                    val editableCollectionBean =
                        checkNotNull(
                            collectionDao.get(
                                collectionDao.insert(DatabaseBean(text = editableCollection)),
                            ),
                        )
                    SymbolHistory(180).apply {
                        load()
                        insert(retainedSymbol)
                        save()
                    }
                    // This resident instance intentionally remains unloaded until privacy is on.
                    // Its first ordinary insert must restore the disk history before saving.
                    val sharedSymbolHistory = SymbolHistory(180)

                    PrivacyModeState.beginInput(privacyOwner, PrivacyMode(password = true))
                    stateStarted = true
                    assertSpeechRejected(privateClip)
                    setClipboard(ClipData.newPlainText("private test", privateClip))
                    assertNotCaptured(privateClip)
                    phase("active unified privacy state excludes an ordinary clipboard item")

                    check(
                        runCatching {
                            ClipboardHelper.updateText(editableClipboardBean.id, privateClipEdit)
                        }.exceptionOrNull() is PersistencePermitExpiredException,
                    ) { "Privacy mode did not reject a clipboard history edit" }
                    check(
                        runCatching {
                            CollectionHelper.updateText(editableCollectionBean.id, privateCollectionEdit)
                        }.exceptionOrNull() is PersistencePermitExpiredException,
                    ) { "Privacy mode did not reject a collection edit" }
                    check(dao.get(editableClipboardBean.id)?.text == editableClip && dao.find(privateClipEdit) == null) {
                        "Privacy mode changed an existing clipboard history row"
                    }
                    check(
                        collectionDao.get(editableCollectionBean.id)?.text == editableCollection &&
                            collectionDao.find(privateCollectionEdit) == null,
                    ) { "Privacy mode changed an existing collection row" }
                    phase("privacy mode rejects edits to existing clipboard and collection rows")

                    sharedSymbolHistory.load()
                    val symbolHistoryBeforePrivateSave = symbolHistoryFile.readBytes()
                    sharedSymbolHistory.insert(privateSymbol)
                    sharedSymbolHistory.save()
                    check(sharedSymbolHistory.toOrderedList().isEmpty()) { "Private symbol history remained visible" }
                    check(symbolHistoryFile.readBytes().contentEquals(symbolHistoryBeforePrivateSave)) {
                        "Private symbol input changed symbol_history"
                    }
                    check(!symbolHistoryFile.readText().contains(privateSymbol)) {
                        "Private symbol input reached symbol_history"
                    }
                    phase("privacy mode hides symbol history and rejects symbol persistence")

                    val afterPrivateCommits = activeSession.runOnReady {
                        beginEditor(privateEditor, nullInputType = false, privacyMode = true)
                        privateEditorStarted = true
                        check(Rime.isRimePrivacyGuardEnabledForTesting()) { "Native privacy guard is inactive" }
                        check(Rime.getRimeCommitHistorySizeForTesting() == 0) { "Private editor inherited commit history" }
                        var privateCandidateOrder: List<String>? = null

                        suspend fun commitFixtureCandidate() {
                            clearComposition()
                            check(processKey('q'.code)) { "Fixture key was not processed" }
                            val candidateOrder = getCandidates(0, 10).map { it.text }
                            check(candidateOrder.count { it == privateCandidate } == 1) {
                                "Fixture candidate list is ambiguous: $candidateOrder"
                            }
                            privateCandidateOrder?.let { expected ->
                                check(candidateOrder == expected) {
                                    "Private selection changed candidate text/order: $expected -> $candidateOrder"
                                }
                            } ?: run { privateCandidateOrder = candidateOrder }
                            val index = candidateOrder.indexOf(privateCandidate)
                            check(index >= 0) { "Fixture candidate is unavailable" }
                            check(selectCandidate(index, false)) { "Fixture candidate was not selected" }
                            check(getRawInput().isEmpty()) { "Fixture candidate did not commit" }
                        }

                        repeat(8) {
                            commitFixtureCandidate()
                            check(Rime.getRimeCommitHistorySizeForTesting() == 0) {
                                "Private commit entered Rime context history"
                            }
                        }
                        check(luaGearEvents(luaGearMarker).isEmpty()) {
                            "A Lua gear callback observed private input before schema teardown"
                        }

                        check(selectSchema(checkNotNull(originalSchema))) {
                            "Could not tear down the private Lua fixture"
                        }
                        check(Rime.isRimePrivacyGuardEnabledForTesting()) {
                            "Private Lua teardown disabled the privacy guard"
                        }
                        check(Rime.getRimeCommitHistorySizeForTesting() == 0) {
                            "Private Lua teardown restored private history"
                        }
                        check(
                            Rime.deployRimeSchemaFile(
                                File(DataManager.userDataDir, "$fixturePrefix.schema.yaml").path,
                            ),
                        ) { "Private Lua fixture redeployment failed" }
                        check(selectSchema(fixturePrefix)) { "Private Lua fixture reselection failed" }
                        setRuntimeOption("ascii_mode", false)
                        clearComposition()
                        check(processKey('q'.code)) { "Redeployed private fixture key was not processed" }
                        val redeployedCandidateOrder = getCandidates(0, 10).map { it.text }
                        check(redeployedCandidateOrder == privateCandidateOrder) {
                            "Private redeployment changed candidate text/order: " +
                                "$privateCandidateOrder -> $redeployedCandidateOrder"
                        }
                        check(selectSchema(checkNotNull(originalSchema))) {
                            "Could not tear down the redeployed private Lua fixture"
                        }
                        check(Rime.isRimePrivacyGuardEnabledForTesting()) {
                            "Redeployed private Lua teardown disabled the privacy guard"
                        }
                        check(Rime.getRimeCommitHistorySizeForTesting() == 0) {
                            "Redeployed private Lua teardown restored private history"
                        }

                        val enabledSchemaIds = enabledSchemata().map { it.id }
                        check(enabledSchemaIds.isNotEmpty()) { "No enabled schema is available for privacy validation" }
                        check(enabledSchemaIds.none { it.isBlank() }) { "An enabled schema has no identifier" }
                        enabledSchemaIds.distinct().also { schemaIds ->
                            enabledSchemaCount = schemaIds.size
                            schemaIds.forEach { schemaId ->
                                clearComposition()
                                check(selectSchema(schemaId)) { "Could not select enabled schema '$schemaId'" }
                                check(selectedSchemaId() == schemaId) { "Enabled schema '$schemaId' was not selected" }
                                check(Rime.isRimePrivacyGuardEnabledForTesting()) {
                                    "Enabled schema '$schemaId' disabled the privacy guard"
                                }
                                check(Rime.getRimeCommitHistorySizeForTesting() == 0) {
                                    "Enabled schema '$schemaId' restored private history"
                                }
                            }
                        }
                        check(selectSchema(checkNotNull(originalSchema))) { "Could not restore the original schema" }
                        check(Rime.isRimePrivacyGuardEnabledForTesting()) { "Original schema restore disabled the privacy guard" }
                        check(Rime.getRimeCommitHistorySizeForTesting() == 0) { "Original schema restore restored private history" }

                        // Keep the malicious fixture alive across the editor boundary. Left
                        // exercises Navigator::input_/spans_, while the unhandled printable key
                        // would remain in ChordComposer::raw_sequence_ without guarded cleanup.
                        check(selectSchema(fixturePrefix)) { "Could not select the built-in state fixture" }
                        setRuntimeOption("ascii_mode", false)
                        clearComposition()
                        check(processKey('q'.code)) { "Private navigation fixture key was not processed" }
                        check(processKey(RimeKeyEvent.getKeycodeByName("Left"))) {
                            "Navigator did not process a private composition"
                        }
                        clearComposition()
                        setRuntimeOption("_auto_commit", false)
                        val privatePairPreviews = mutableListOf<String?>()
                        repeat(3) {
                            check(processKey('"'.code)) { "Private paired punctuation was not processed" }
                            privatePairPreviews += compositionCached.commitTextPreview
                            clearComposition()
                        }
                        setRuntimeOption("_auto_commit", true)
                        check(privatePairPreviews == listOf("PAIR_OPEN", "PAIR_CLOSE", "PAIR_OPEN")) {
                            "Private paired punctuation lost its in-session alternation: $privatePairPreviews"
                        }
                        check(!processKey('z'.code)) { "Unhandled private chord fixture key was consumed" }
                        check(getRawInput().isEmpty()) { "Unhandled private chord fixture changed the Rime context" }
                        check(!processKey(RimeKeyEvent.getKeycodeByName("Return"))) {
                            "A directly committed private key was replayed by ChordComposer"
                        }
                        check(processKey('y'.code)) { "Private key-binding fixture was not processed" }
                        check(processKey('q'.code)) { "Private inline-ASCII fixture key was not processed" }
                        val shiftDown = RimeKeyEvent.parse("Shift_L")
                        val shiftUp = RimeKeyEvent.parse("Release+Shift_L")
                        processKey(shiftDown.value, shiftDown.modifiers.toUInt())
                        processKey(shiftUp.value, shiftUp.modifiers.toUInt())
                        check(getRuntimeOption("ascii_mode")) { "Private inline ASCII mode did not activate" }

                        // The schema switcher owns a temporary Context. Exercise a privacy
                        // change while it is active so the primary editor Context and its
                        // stateful gears cannot survive behind the switcher and replay later.
                        clearComposition()
                        setRuntimeOption("ascii_mode", false)
                        check(processKey('q'.code)) { "Private switcher fixture key was not processed" }
                        check(processKey(RimeKeyEvent.getKeycodeByName("Left"))) {
                            "Navigator did not retain the private switcher fixture"
                        }
                        val privateChordDown = RimeKeyEvent.parse("x")
                        check(processKey(privateChordDown.value, privateChordDown.modifiers.toUInt())) {
                            "ChordComposer did not retain the private held key"
                        }
                        check(processKey(RimeKeyEvent.getKeycodeByName("F4"))) {
                            "Schema switcher did not activate during private input"
                        }
                        check(selectedSchemaId() == ".default") {
                            "F4 was consumed without activating the schema switcher"
                        }
                        beginEditor(privateEditor, nullInputType = false, privacyMode = false)
                        check(!Rime.isRimePrivacyGuardEnabledForTesting()) {
                            "Manual privacy exit left the native guard enabled"
                        }
                        check(!getRuntimeOption("trime_privacy_mode")) {
                            "Primary Rime context retained the private runtime option"
                        }
                        check(!commitComposition()) {
                            "Private primary composition replayed after leaving an active switcher"
                        }
                        check(getRawInput().isEmpty()) {
                            "Private primary input survived an active-switcher boundary"
                        }
                        beginEditor(privateEditor, nullInputType = false, privacyMode = true)
                        check(Rime.isRimePrivacyGuardEnabledForTesting()) {
                            "Manual privacy re-entry did not restore the native guard"
                        }
                        check(selectedSchemaId() == fixturePrefix) {
                            "Schema switcher remained selected after the privacy boundary"
                        }
                        check(getRuntimeOption("trime_privacy_mode")) {
                            "Primary Rime context did not receive the private runtime option"
                        }
                        val privateChordUp = RimeKeyEvent.parse("Release+x")
                        check(!processKey(privateChordUp.value, privateChordUp.modifiers.toUInt())) {
                            "Held private chord replayed after the active-switcher boundary"
                        }
                        check(!processKey(RimeKeyEvent.getKeycodeByName("Escape"))) {
                            "Schema switcher remained active across a privacy boundary"
                        }
                        check(getRawInput().isEmpty()) {
                            "Switcher navigation or chord state replayed after privacy re-entry"
                        }
                        check(Rime.getRimeCommitHistorySizeForTesting() == 0) {
                            "Active-switcher transition restored private history"
                        }
                        check(selectSchema(checkNotNull(originalSchema))) {
                            "Could not close the private fixture for its protected export"
                        }
                        check(Rime.isRimePrivacyGuardEnabledForTesting()) {
                            "Private fixture export disabled the privacy guard"
                        }
                        userDbSnapshot(fixturePrefix)
                    }
                    check(
                        afterPrivateCommits == beforePrivate &&
                            !afterPrivateCommits.contents.contains(privateCandidate),
                    ) { "Private input changed the fixture user dictionary" }
                    check(luaGearEvents(luaGearMarker).isEmpty()) {
                        "A Lua gear callback or fini hook wrote through io.open during private input"
                    }
                    phase(
                        "malicious Lua processor, segmentor, translator, filter/tags_match and fini hooks " +
                            "remain inert across private teardown and redeployment",
                    )
                    val afterPrivate = activeSession.runOnReady {
                        check(selectSchema(fixturePrefix)) { "Could not restore the built-in state fixture" }
                        check(Rime.isRimePrivacyGuardEnabledForTesting()) { "Schema switch disabled the privacy guard" }
                        check(Rime.getRimeCommitHistorySizeForTesting() == 0) { "Schema switch restored private history" }
                        finishOwnedEditors(longArrayOf(privateEditor + 1))
                        check(Rime.isRimePrivacyGuardEnabledForTesting()) {
                            "A foreign service generation cleared the private editor"
                        }
                        finishOwnedEditors(longArrayOf(privateEditor - 1, privateEditor))
                        privateEditorStarted = false
                        check(!Rime.isRimePrivacyGuardEnabledForTesting()) {
                            "Privacy guard remained enabled after owned-editor cleanup"
                        }
                        checkTransientEditorStateCleared("successful private editor finish")
                        check(selectSchema(checkNotNull(originalSchema))) {
                            "Could not close the private fixture after owned-editor cleanup"
                        }
                        userDbSnapshot(fixturePrefix)
                    }
                    check(
                        afterPrivate == beforePrivate &&
                            !afterPrivate.contents.contains(privateCandidate),
                    ) { "Private input changed the fixture user dictionary" }
                    instrumentation.runOnMainSync {
                        PrivacyModeState.endInput(privacyOwner, ClipboardHelper::quarantineCurrentPrimaryClip)
                    }
                    stateStarted = false
                    instrumentation.runOnMainSync { ClipboardHelper.onPrimaryClipChanged() }
                    assertNotCaptured(privateClip)
                    listOf(
                        Triple(privacyOwner xor 0x23A1L, PrivacyMode(appRequested = true), appRequestedClip),
                        Triple(privacyOwner xor 0x34B2L, PrivacyMode(manual = true), manualPrivacyClip),
                    ).forEach { (owner, mode, text) ->
                        check(owner != 0L)
                        instrumentation.runOnMainSync { PrivacyModeState.beginInput(owner, mode) }
                        try {
                            check(PrivacyModeState.active) { "$mode did not activate unified privacy" }
                            assertSpeechRejected(text)
                            setClipboard(ClipData.newPlainText("private trigger test", text))
                            assertNotCaptured(text)
                        } finally {
                            instrumentation.runOnMainSync {
                                if (mode.manual) PrivacyModeState.setManual(false)
                                PrivacyModeState.endInput(owner, ClipboardHelper::quarantineCurrentPrimaryClip)
                            }
                        }
                        check(!PrivacyModeState.active) { "$mode did not restore ordinary persistence" }
                        instrumentation.runOnMainSync { ClipboardHelper.onPrimaryClipChanged() }
                        assertNotCaptured(text)
                    }
                    instrumentation.runOnMainSync {
                        InputFeedbackManager.textCommitSpeak(ordinarySpeech)
                        check(feedbackField("tts") == null) {
                            "No-audio ordinary speech control initialized TextToSpeech"
                        }
                        check(feedbackField("pendingSpeech") == ordinarySpeech) {
                            "Ordinary speech dispatch did not recover after privacy mode"
                        }
                        InputFeedbackManager.finishInput()
                        check(feedbackField("pendingSpeech") == null) {
                            "Ordinary speech control left pending content"
                        }
                    }
                    phase("real speech entry points reject private content and recover without device audio")
                    Log.i(LOG_TAG, privateLogEnd)
                    phase(
                        "private commits, $enabledSchemaCount enabled schemas and active-switcher boundaries " +
                            "retain the guard without history or userdb writes",
                    )
                    phase("password, no-learning and manual privacy each reject an independently owned clipboard token")

                    val processLogs = logForPid(ownLogcat(), pid)
                    val privateLogStartIndex = processLogs.indexOf(privateLogStart)
                    val privateLogEndIndex = processLogs.indexOf(privateLogEnd, privateLogStartIndex + 1)
                    check(privateLogStartIndex >= 0 && privateLogEndIndex > privateLogStartIndex) {
                        "Application log boundaries were not captured for the private input window"
                    }
                    val privateLogs =
                        processLogs.substring(
                            privateLogStartIndex + privateLogStart.length,
                            privateLogEndIndex,
                        )
                    check(
                        listOf(
                            sensitiveClip,
                            appOwnedSensitiveClip,
                            secondAppOwnedSensitiveClip,
                            privateClip,
                            appRequestedClip,
                            manualPrivacyClip,
                            privateClipEdit,
                            privateCollectionEdit,
                            privateSymbol,
                            privateCandidate,
                        )
                            .none { privateLogs.contains(it) },
                    ) {
                        "Private content appeared in application log output"
                    }
                    phase("application log output contains no private fixture content")

                    setClipboard(ClipData.newPlainText("ordinary test", normalClip))
                    awaitCaptured(normalClip)
                    phase("ordinary clipboard persistence resumes after privacy mode")

                    sharedSymbolHistory.insert(normalSymbol)
                    sharedSymbolHistory.save()
                    val resumedSymbolHistory = symbolHistoryFile.readText()
                    check(
                        resumedSymbolHistory.contains(retainedSymbol) &&
                            resumedSymbolHistory.contains(normalSymbol) &&
                            !resumedSymbolHistory.contains(privateSymbol),
                    ) {
                        "Normal symbol history did not preserve ordinary state or reject private input"
                    }
                    phase("ordinary symbol history persistence resumes after privacy mode")

                    activeSession.runOnReady {
                        beginEditor(normalEditor, nullInputType = false, privacyMode = false)
                        normalEditorStarted = true
                        check(!Rime.isRimePrivacyGuardEnabledForTesting()) { "Normal editor unexpectedly enabled privacy" }
                        check(!getRuntimeOption("ascii_mode")) { "Private inline ASCII mode crossed the editor boundary" }
                        check(selectSchema(fixturePrefix)) { "Could not select the normal-learning fixture" }
                        setRuntimeOption("ascii_mode", false)
                        clearComposition()
                        setRuntimeOption("_auto_commit", false)
                        check(processKey('"'.code)) { "Ordinary paired punctuation was not processed" }
                        check(compositionCached.commitTextPreview == "PAIR_OPEN") {
                            "Private punctuation parity crossed the editor boundary"
                        }
                        clearComposition()
                        setRuntimeOption("_auto_commit", true)
                        check(!processKey(RimeKeyEvent.getKeycodeByName("Return"))) {
                            "Private ChordComposer raw input crossed the editor boundary"
                        }
                        check(getRawInput().isEmpty()) { "Private chord state restored a normal composition" }
                        check(processKey('q'.code)) { "Normal navigation fixture key was not processed" }
                        check(processKey(RimeKeyEvent.getKeycodeByName("Left"))) {
                            "Navigator did not resume in ordinary mode"
                        }
                        check(getRawInput() == "q") { "Ordinary navigation changed the fixture input" }
                        clearComposition()
                        check(!processKey('z'.code)) { "Ordinary direct-key fixture was unexpectedly consumed" }
                        check(processKey('y'.code)) { "Ordinary key-binding fixture was not processed" }
                        beginEditor(secondNormalEditor, nullInputType = false, privacyMode = false)
                        check(!processKey(RimeKeyEvent.getKeycodeByName("Return"))) {
                            "Ordinary ChordComposer raw input crossed an editor boundary"
                        }
                        check(getRawInput().isEmpty()) { "Ordinary editor boundary restored stale chord input" }
                    }
                    phase("stateful chord, navigation and paired-punctuation gears clear only at editor boundaries")
                    val beforeNormal = activeSession.runOnReady {
                        check(selectSchema(checkNotNull(originalSchema))) {
                            "Could not close the normal fixture for its learning baseline"
                        }
                        userDbSnapshot(fixturePrefix)
                    }
                    check(beforeNormal == afterPrivate && !beforeNormal.contents.contains(privateCandidate)) {
                        "Ordinary non-commit input changed the fixture user dictionary"
                    }
                    val afterNormal = activeSession.runOnReady {
                        check(selectSchema(fixturePrefix)) { "Could not restore the normal-learning fixture" }
                        check(processKey('q'.code)) { "Normal fixture key was not processed" }
                        val index = getCandidates(0, 10).indexOfFirst { it.text == privateCandidate }
                        check(index >= 0) { "Normal fixture candidate is unavailable" }
                        val ordinaryEvents = luaGearEvents(luaGearMarker)
                        check(ordinaryEvents.any { it.startsWith("processor:") }) {
                            "The Lua processor fixture did not execute in ordinary mode"
                        }
                        check("segmentor" in ordinaryEvents) {
                            "The Lua segmentor fixture did not execute in ordinary mode"
                        }
                        check(ordinaryEvents.any { it.startsWith("translator:") }) {
                            "The Lua translator fixture did not execute in ordinary mode"
                        }
                        check("filter_tags_match" in ordinaryEvents) {
                            "The Lua filter tags_match fixture did not execute in ordinary mode"
                        }
                        check("filter" in ordinaryEvents) {
                            "The Lua filter fixture did not execute in ordinary mode"
                        }
                        check(selectCandidate(index, false)) { "Normal fixture candidate was not selected" }
                        check(getRawInput().isEmpty()) { "Normal fixture candidate did not commit" }
                        // Memory batches a selected candidate until an unhandled key or the next
                        // query. Force that production boundary before exporting the userdb.
                        processKey(' '.code)
                        check(Rime.getRimeCommitHistorySizeForTesting() > 0) { "Normal commit did not enter Rime history" }
                        check(selectSchema(checkNotNull(originalSchema))) {
                            "Could not tear down the ordinary Lua fixture"
                        }
                        check(!Rime.isRimePrivacyGuardEnabledForTesting()) {
                            "Ordinary Lua teardown unexpectedly restored privacy mode"
                        }
                        userDbSnapshot(fixturePrefix)
                    }
                    check(
                        afterNormal != beforeNormal &&
                            afterNormal.entries > beforeNormal.entries &&
                            afterNormal.contents.contains(privateCandidate),
                    ) { "Normal learning did not change the fixture user dictionary" }
                    val eventsAfterNormalTeardown = luaGearEvents(luaGearMarker).toSet()
                    check(
                        setOf(
                            "fini_processor",
                            "fini_segmentor",
                            "fini_translator",
                            "fini_filter",
                        ).all { it in eventsAfterNormalTeardown },
                    ) { "Ordinary Lua fini hooks did not all execute after private-mode recovery" }
                    phase("normal commit history and user dictionary learning resume")
                    phase("all Lua gear callbacks and fini hooks resume after private mode")
                }
            }
            passed = true
            result.putString("stream", "PASS: $checks T07 clipboard, Rime learning, schema, log and restoration checks\n")
        } catch (failure: Throwable) {
            recordedFailure = failure
            result.putString("stream", "FAIL: ${failure.stackTraceToString()}\n")
        } finally {
            var cleanupFailure: Throwable? = null
            fun clean(block: () -> Unit) {
                runCatching(block).onFailure { failure ->
                    cleanupFailure?.addSuppressed(failure) ?: run { cleanupFailure = failure }
                }
            }
            fun cleanMain(block: () -> Unit) = clean { instrumentation.runOnMainSync(block) }

            clean {
                runBlocking {
                    withContext(NonCancellable) {
                        session?.let { activeSession ->
                            activeSession.runOnReady {
                                if (privateEditorStarted) finishEditor(privateEditor)
                                if (normalEditorStarted) {
                                    finishOwnedEditors(longArrayOf(normalEditor, secondNormalEditor))
                                }
                                clearComposition()
                                originalSchema?.let { check(selectSchema(it)) { "Could not restore the original schema" } }
                            }
                        }
                    }
                }
            }
            clean {
                if (stateStarted) {
                    PrivacyModeState.endInput(privacyOwner)
                    stateStarted = false
                }
            }
            listOf(
                sensitiveClip,
                appOwnedSensitiveClip,
                secondAppOwnedSensitiveClip,
                privateClip,
                appRequestedClip,
                manualPrivacyClip,
                normalClip,
                editableClip,
                privateClipEdit,
            ).forEach { text ->
                clean {
                    runBlocking {
                        withContext(NonCancellable) {
                            while (true) {
                                val bean = dao.find(text) ?: break
                                ClipboardHelper.delete(bean.id)
                            }
                        }
                    }
                }
            }
            listOf(editableCollection, privateCollectionEdit).forEach { text ->
                clean {
                    runBlocking {
                        withContext(NonCancellable) {
                            while (true) {
                                val bean = collectionDao.find(text) ?: break
                                CollectionHelper.delete(bean.id)
                            }
                        }
                    }
                }
            }
            clean {
                runBlocking {
                    withContext(NonCancellable) {
                        session?.let {
                            RimeDaemon.destroySession(sessionName)
                            session = null
                            val terminalState =
                                withTimeout(120_000) {
                                    RimeDaemon.engineState.first { state ->
                                        state == RimeLifecycle.State.STOPPED ||
                                            state == RimeLifecycle.State.FAILED
                                    }
                                }
                            // FAILED follows native finalization. Continue restoring fixtures, but
                            // fail acceptance because a clean probe run must finish in STOPPED.
                            check(terminalState == RimeLifecycle.State.STOPPED) {
                                "Rime shutdown ended in $terminalState before restoring user.yaml"
                            }
                        }
                    }
                }
            }
            clean { removeOwnedFiles(fixturePrefix) }
            clean {
                if (previousUserConfig == null) {
                    check(!userConfigFile.exists() || userConfigFile.delete()) {
                        "Could not remove the probe-created user configuration"
                    }
                } else {
                    userConfigFile.writeBytes(previousUserConfig)
                }
            }
            clean {
                if (previousSymbolHistory == null) {
                    check(!symbolHistoryFile.exists() || symbolHistoryFile.delete()) {
                        "Could not remove the probe-created symbol history"
                    }
                } else {
                    symbolHistoryFile.writeBytes(previousSymbolHistory)
                }
            }
            cleanMain { clipboardPrefs.clipboardListening.setValue(false) }
            cleanMain { PrivacyModeState.initialize(manual = false) }
            if (primaryClipboardCaptured) {
                cleanMain {
                    val clipToRestore = previousPrimaryClip
                    if (clipToRestore != null) {
                        clipboard.setPrimaryClip(ClipData(clipToRestore))
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        // Android 5.0-8.1 has no public clearPrimaryClip API.
                        clipboard.setPrimaryClip(ClipData.newPlainText("privacy-probe-cleanup", ""))
                    }
                }
            }
            cleanMain { ClipboardHelper.onPrimaryClipChanged() }
            if (feedbackPrepared) {
                cleanMain {
                    InputFeedbackManager.destroy()
                    feedbackPrefs.speakOnKeyPress.setValue(previousSpeakOnKeyPress)
                    feedbackPrefs.speakOnCommit.setValue(previousSpeakOnCommit)
                }
            }
            cleanMain { generalPrefs.manualPrivacyMode.setValue(previousManualPrivacy) }
            cleanMain { PrivacyModeState.initialize(previousManualPrivacy) }
            cleanMain { ClipboardHelper.lastBean = previousLastBean }
            cleanMain { clipboardPrefs.clipboardListening.setValue(previousClipboardListening) }
            cleanMain {
                val credentialPreferences = feedbackPrefs.speakOnKeyPress.sharedPreferences
                check(
                    listOf(
                        feedbackPrefs.speakOnCommit.sharedPreferences,
                        generalPrefs.manualPrivacyMode.sharedPreferences,
                        clipboardPrefs.clipboardListening.sharedPreferences,
                    ).all { it === credentialPreferences },
                ) { "Privacy probe preferences do not share the credential store" }
                val restoredValues =
                    mapOf(
                        feedbackPrefs.speakOnKeyPress.key to previousSpeakOnKeyPress,
                        feedbackPrefs.speakOnCommit.key to previousSpeakOnCommit,
                        generalPrefs.manualPrivacyMode.key to previousManualPrivacy,
                        clipboardPrefs.clipboardListening.key to previousClipboardListening,
                    )
                commitBooleanPreferences(credentialPreferences, restoredValues, "credential")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val devicePreferences =
                        PreferenceManager.getDefaultSharedPreferences(
                            instrumentation.targetContext.createDeviceProtectedStorageContext(),
                        )
                    commitBooleanPreferences(devicePreferences, restoredValues, "device-protected")
                }
            }
            cleanMain { clipboardForegroundActivity?.finish() }
            cleanupFailure?.let { failure ->
                passed = false
                val combinedFailure = recordedFailure?.apply { addSuppressed(failure) } ?: failure
                recordedFailure = combinedFailure
                result.putString("stream", "FAIL: ${combinedFailure.stackTraceToString()}\n")
            }
        }
        result.putBoolean("passed", passed)
        result.putInt("checks", checks)
        result.putInt("enabled_schema_count", enabledSchemaCount)
        instrumentation.finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }
}
