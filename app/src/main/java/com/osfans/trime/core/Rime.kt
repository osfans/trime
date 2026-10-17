/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core

import android.annotation.SuppressLint
import androidx.core.net.toUri
import com.osfans.trime.BuildConfig
import com.osfans.trime.core.data.ConfigImportValidator
import com.osfans.trime.core.data.ConfigurationImportException
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.core.data.SyncRelativePath
import com.osfans.trime.core.data.UserDataManager
import com.osfans.trime.data.candidates.CandidatePin
import com.osfans.trime.data.candidates.CandidatePinStore
import com.osfans.trime.data.opencc.OpenCCDictManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.InputSetupController
import com.osfans.trime.data.prefs.InputSetupSelection
import com.osfans.trime.data.prefs.InputSetupState
import com.osfans.trime.data.prefs.RimeInputSetupEngine
import com.osfans.trime.data.theme.ThemeLoader
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.data.theme.ThemePrefs
import com.osfans.trime.data.theme.ThemeYaml
import com.osfans.trime.data.userdict.UserDictManager
import com.osfans.trime.ime.core.InlinePreeditMode
import com.osfans.trime.ime.core.PrivacyModeState
import com.osfans.trime.ime.performance.InputLatencyLog
import com.osfans.trime.ime.performance.InputRequest
import com.osfans.trime.util.mapping
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.minutes

/**
 * Rime JNI and instance methods
 *
 * @see [librime](https://github.com/rime/librime)
 */
class Rime :
    RimeApi,
    RimeLifecycleOwner {
    private val lifecycleRegistry = RimeLifecycleRegistry()
    override val lifecycle get() = lifecycleRegistry

    override val messageFlow = messageBus.flow

    override val isReady: Boolean
        get() = lifecycle.currentState == RimeLifecycle.State.READY

    @Volatile override var usingExistingResources: Boolean = false
        private set

    override var schemaCached = RimeSchema.empty()
        private set

    override var statusCached = StatusProto()
        private set

    override var compositionCached = CompositionProto()
        private set

    override var hasMenu: Boolean = false
        private set

    override var paging: Boolean = false
        private set

    override var t9Cached = T9StateProto()
        private set

    private val optionCache = RuntimeOptionCache()
    private val candidateSnapshots = CandidateSnapshotState()
    private var activeRequest: InputRequest? = null
    private val candidatePins = CandidatePinStore()

    override val candidateSnapshotCached: CandidateSnapshot get() = candidateSnapshots.current

    private fun invalidateCandidates() {
        InputLatencyLog.supersede(candidateSnapshots.current)
        candidateSnapshots.invalidate()
    }

    override fun getRuntimeOptionCached(option: String): Boolean = optionCache[option]

    override suspend fun refreshT9Options() = withRimeContext { emitResponse() }

    override suspend fun refreshPredictionOptions() = withRimeContext {
        invalidateCandidates()
        applyPredictionOptions()
        emitResponse()
    }

    override suspend fun clearPredictionContext() = withRimeContext {
        invalidateCandidates()
        clearRimePredictionContext()
        emitResponse()
    }

    private fun applyPredictionOptions() {
        setRimePredictionEnabled(
            editorToken != 0L && !isNullInputType && AppPrefs.defaultInstance().candidates.nextWordPrediction.getValue(),
        )
    }

    override suspend fun beginEditor(
        token: Long,
        nullInputType: Boolean,
        privacyMode: Boolean,
    ) = withRimeContext {
        invalidateCandidates()
        val editorChanged = editorToken != token
        if (editorChanged || this.privacyMode != privacyMode) {
            // Drop the previous editor's Java snapshots before any JNI boundary
            // can fail. Native privacy cleanup is still performed below.
            clearTransientEditorState()
            clearRimePredictionContext()
        }
        if (editorChanged) {
            clearRimeComposition()
            getRimeCommit()
        }
        // Every editor boundary clears native gear caches, including ordinary-to-ordinary app
        // switches. Privacy changes use the same guarded boundary before the new editor can type.
        if (editorChanged || this.privacyMode != privacyMode) {
            setRimePrivacyMode(privacyMode)
        }
        // Native cleanup above may emit option callbacks for the old owner.
        if (editorChanged || this.privacyMode != privacyMode || isNullInputType != nullInputType) cancelAsciiSwitchTips()
        editorToken = token
        this.privacyMode = privacyMode
        isNullInputType = nullInputType
        applyPredictionOptions()
        emitResponse()
    }

    override suspend fun finishEditor(token: Long) = withRimeContext {
        if (editorToken != token) return@withRimeContext
        finishCurrentEditor()
    }

    override suspend fun finishOwnedEditors(tokens: LongArray) = withRimeContext {
        if (editorToken == 0L || !tokens.contains(editorToken)) return@withRimeContext
        finishCurrentEditor()
    }

    /** Must only be called from [withRimeContext]. */
    private fun finishCurrentEditor() {
        invalidateCandidates()
        clearTransientEditorState()
        try {
            setRimePredictionEnabled(false)
            if (privacyMode) {
                setRimePrivacyMode(false)
            } else {
                clearRimeComposition()
            }
            getRimeCommit()
            emitResponse()
        } finally {
            // The engine may fail during privacy reset, composition clearing,
            // commit retrieval, or response publication. No editor-owned Java
            // snapshot may survive that failure or the process privacy boundary.
            clearTransientEditorState()
            privacyMode = false
            editorToken = 0
            isNullInputType = true
        }
    }

    override suspend fun t9Action(
        revision: Int,
        action: T9Action,
        start: Int,
        end: Int,
        spelling: String,
    ): Boolean = withRimeContext {
        invalidateCandidates()
        performRimeT9Action(revision, action.ordinal, start, end, spelling, AppPrefs.defaultInstance().keyboard.t9AssistMask())
            .also { emitResponse() }
    }

    private val dispatcher =
        RimeDispatcher(
            object : RimeDispatcher.RimeController {
                override fun nativeStartup() {
                    // Loading/relocating the native engine must not block Activity or IME creation.
                    System.loadLibrary("rime_jni")
                    startRime(startupFullCheck)
                    lifecycleRegistry.emitEvent(RimeLifecycle.Event.ON_READY)
                }

                override fun nativeFinalize() {
                    stopRime()
                }

                override fun nativeFailure(error: Throwable) {
                    clearTransientEditorState()
                    Timber.e(error, "Rime engine lifecycle failed")
                    lifecycleRegistry.fail(error)
                    handleRimeMessage(RimeMessage.MessageType.Deploy.ordinal, arrayOf("failure"))
                    unregisterRimeMessageHandler(::handleRimeMessage)
                }
            },
        )

    private val inlinePreeditMode by AppPrefs.defaultInstance().general.inlinePreeditMode
    private val showAsciiSwitchTips by AppPrefs.defaultInstance().general.asciiSwitchTips

    private var asciiSwitchTipsJob: Job? = null

    @Volatile private var asciiSwitchTipsGeneration = 0L
    private var startupFullCheck = false
    private var nativeInitialized = false
    private var isNullInputType = true
    private var lastAsciiTipsText = ""
    private var pagingMode = false
    private var privacyMode = false

    init {
        if (lifecycle.currentState != RimeLifecycle.State.STOPPED) {
            throw IllegalStateException("Rime has already been created!")
        }
    }

    private suspend inline fun <T> withRimeContext(crossinline block: suspend () -> T): T {
        val request = currentCoroutineContext()[InputRequest]
        InputLatencyLog.rimeQueued(request)
        return dispatcher.runConfined {
            InputLatencyLog.engineStart(request)
            val previous = activeRequest
            activeRequest = request
            try {
                block()
            } finally {
                activeRequest = previous
            }
        }
    }

    override suspend fun isEmpty(): Boolean = withRimeContext {
        getCurrentRimeSchema() == ".default" // 無方案
    }

    override suspend fun deploy(skipImport: Boolean) = withImportMaintenance { restoreTheme, acceptTheme ->
        if (UserDataManager.usesExternalSync() && !UserDataManager.hasExternalAccess()) {
            UserDataManager.fallbackToAppStorage()
        }
        var activated = false
        if (UserDataManager.usesExternalSync() && !skipImport) {
            var previous: ImportRuntime? = null
            val result = UserDataManager.importToLocal(
                keepNotificationUntilDeploySuccess = true,
                beforePublish = {
                    previous = captureImportRuntimeConfined()
                    activated = true
                    stopRime()
                },
                beforeRollback = { if (activated) stopRime() },
                afterRollback = {
                    if (activated) {
                        restoreImportRuntimeConfined(checkNotNull(previous))
                        restoreTheme()
                    }
                },
                activationContext = ::withImportActivation,
            ) { stats ->
                if (stats.configurationChanged) {
                    acceptTheme(activateImportedNativeConfined())
                } else if (stats.copied + stats.deleted > 0) {
                    ThemeManager.loadedThemeForImport()?.let(acceptTheme)
                }
            }
            if (result.isFailure) {
                val failure = result.exceptionOrNull()!!
                if (failure is ConfigurationImportException && !failure.previousConfigurationRestored) {
                    dispatcher.fail(failure)
                    throw failure
                }
                activated = false
                UserDataManager.fallbackToAppStorage(failure)
            } else {
                Timber.i("Import finished: ${result.getOrNull()}")
            }
        }
        // An explicit Deploy still runs maintenance when imported bytes were unchanged.
        if (!activated) deployNativeLocked()
    }

    override suspend fun importConfiguration(treeUri: String): UserDataManager.SyncStats = withImportMaintenance { restoreTheme, acceptTheme ->
        var previous: ImportRuntime? = null
        var activated = false
        try {
            UserDataManager.importFromPickedTree(
                treeUri.toUri(),
                beforePublish = {
                    previous = captureImportRuntimeConfined()
                    activated = true
                    stopRime()
                },
                beforeRollback = { if (activated) stopRime() },
                afterRollback = {
                    if (activated) {
                        restoreImportRuntimeConfined(checkNotNull(previous))
                        restoreTheme()
                    }
                },
                activationContext = ::withImportActivation,
            ) { stats ->
                if (stats.configurationChanged) {
                    acceptTheme(activateImportedNativeConfined())
                } else if (stats.copied + stats.deleted > 0) {
                    ThemeManager.loadedThemeForImport()?.let(acceptTheme)
                }
            }
        } catch (failure: Throwable) {
            if (failure is ConfigurationImportException && !failure.previousConfigurationRestored) dispatcher.fail(failure)
            throw failure
        }
    }

    override suspend fun importThemeConfiguration(configId: String): UserDataManager.SyncStats = withImportMaintenance { restoreTheme, acceptTheme ->
        SyncRelativePath.requireSafeName(configId)
        var previousTheme = ThemeManager.prefs.selectedTheme.getValue()
        var selectionWriteAttempted = false
        var previous: ImportRuntime? = null
        var activated = false

        // KTX edit(commit = true) discards commit's Boolean; rollback requires checking it.
        @SuppressLint("UseKtx")
        fun rememberTheme(id: String) {
            check(
                ThemeManager.prefs.selectedTheme.sharedPreferences.edit()
                    .putString(ThemePrefs.SELECTED_THEME, id).commit(),
            ) { "Cannot persist the verified theme selection" }
        }
        UserDataManager.importThemeToLocal(
            configId,
            beforePublish = {
                previousTheme = ThemeManager.prefs.selectedTheme.getValue()
                previous = captureImportRuntimeConfined()
                activated = true
                stopRime()
            },
            beforeRollback = { if (activated) stopRime() },
            afterRollback = {
                if (activated) {
                    restoreImportRuntimeConfined(checkNotNull(previous))
                    restoreTheme()
                }
            },
            activationContext = ::withImportActivation,
            rememberSelection = {
                check(ThemeManager.prefs.selectedTheme.getValue() == previousTheme) { "Theme selection changed during activation" }
                selectionWriteAttempted = true
                rememberTheme(configId)
            },
            restoreSelection = {
                if (selectionWriteAttempted && ThemeManager.prefs.selectedTheme.getValue() == configId) rememberTheme(previousTheme)
            },
        ) {
            // A theme selection does not require full schema maintenance.
            startRime(false)
            check(!usingExistingResources && hasUsableRimeDictionary()) { "Theme activation could not start the current input scheme" }
            acceptTheme(decodeImportedThemeConfined(configId))
        }
    }

    private suspend fun withImportActivation(block: () -> Unit) = withRimeContext {
        cancelAsciiSwitchTips()
        configurationImportInProgress = true
        try {
            block()
        } finally {
            configurationImportInProgress = false
        }
    }

    /** Apply verified theme objects after unlocking, without repeating native config deployment. */
    private suspend fun <T> withImportMaintenance(
        block: suspend (() -> Unit, (ThemeLoader.ThemeLoadResult.Success) -> Unit) -> T,
    ): T {
        var validatedTheme: ThemeLoader.ThemeLoadResult.Success? = null
        var originalFailure: Throwable? = null
        try {
            return RimeMaintenanceMutex.withLock {
                val previousTheme = ThemeManager.loadedThemeForImport()
                block(
                    {
                        // Reloading from disk may deploy a theme again after its
                        // artifact was restored. Keep the already verified model.
                        validatedTheme = previousTheme
                    },
                    {
                        validatedTheme = it
                    },
                )
            }
        } catch (failure: Throwable) {
            originalFailure = failure
            val incompleteRollback = (failure is ConfigurationImportException && !failure.previousConfigurationRestored) ||
                failure.suppressed.any { it is ConfigurationImportException && !it.previousConfigurationRestored }
            if (incompleteRollback) {
                validatedTheme = null
                dispatcher.fail(failure)
            }
            throw failure
        } finally {
            val verified = validatedTheme
            if (verified != null) {
                withContext(NonCancellable) {
                    runCatching {
                        ThemeManager.applyImportedTheme(verified)
                    }.exceptionOrNull()?.let { error ->
                        val failure = originalFailure
                        if (failure != null) {
                            if (failure is ConfigurationImportException) failure.previousConfigurationRestored = false
                            failure.addSuppressed(error)
                        } else {
                            throw error
                        }
                    }
                }
            }
        }
    }

    override suspend fun inspectInputSetup(): InputSetupState = RimeMaintenanceMutex.withLock {
        withRimeContext { InputSetupController.inspect(RimeInputSetupEngine(this@Rime)) }
    }

    override suspend fun configureInput(selection: InputSetupSelection): InputSetupState = RimeMaintenanceMutex.withLock {
        withRimeContext { InputSetupController.apply(RimeInputSetupEngine(this@Rime), selection) }
    }

    private data class ImportRuntime(
        val schema: String,
        val options: Map<String, Boolean>,
        val wasReady: Boolean,
        val usingExistingResources: Boolean,
    )

    private fun captureImportRuntimeConfined(): ImportRuntime {
        val options = (schemaCached.switches.flatMap { if (it.name.isEmpty()) it.options else listOf(it.name) } + "ascii_mode")
            .filter { it.isNotEmpty() }.distinct().associateWith(::getRimeOption)
        return ImportRuntime(getCurrentRimeSchema(), options, nativeInitialized && isReady, usingExistingResources)
    }

    /** JNI startup synchronously joins maintenance; this whole phase keeps input behind it. */
    private fun activateImportedNativeConfined(): ThemeLoader.ThemeLoadResult.Success {
        val outcome = java.util.concurrent.atomic.AtomicReference<RimeMessage.DeployMessage.State?>()
        val handler: (RimeMessage<*>) -> Unit = { message ->
            if (message is RimeMessage.DeployMessage && message.data != RimeMessage.DeployMessage.State.Start) outcome.set(message.data)
        }
        registerRimeMessageHandler(handler)
        try {
            startRime(true)
            check(outcome.get() == RimeMessage.DeployMessage.State.Success) { "Imported configuration deployment did not succeed" }
        } finally {
            unregisterRimeMessageHandler(handler)
        }
        check(hasUsableRimeDictionary()) { "Imported configuration has no usable current dictionary" }
        return decodeImportedThemeConfined(ThemeManager.prefs.selectedTheme.getValue())
    }

    /** Compile and decode the exact requested theme; ThemeManager's fallback does not authorize a commit. */
    private fun decodeImportedThemeConfined(themeId: String): ThemeLoader.ThemeLoadResult.Success {
        SyncRelativePath.requireSafeName(themeId)
        check(deployRimeConfigFile(themeId, ThemeLoader.CONFIG_VERSION_KEY)) { "Selected theme could not be deployed" }
        val file = File(DataManager.resolveDeployedResourcePath(themeId))
        check(file.isFile && file.length() in 1..2L * 1024 * 1024) { "Selected theme artifact is unavailable or too large" }
        ConfigImportValidator.validate(file, "$themeId.yaml")
        val mapping = ThemeYaml.parser.parseToYamlNode(file.readText()).mapping
            ?: error("Selected theme artifact is not a mapping")
        return when (val result = ThemeLoader.decodeAndReport(themeId, mapping)) {
            is ThemeLoader.ThemeLoadResult.Failure -> throw result.error
            is ThemeLoader.ThemeLoadResult.Success -> ThemeLoader.withCompatibleKeyboards(result)
        }
    }

    private fun restoreImportRuntimeConfined(previous: ImportRuntime) {
        check(previous.wasReady) { "Previous input engine was not ready for runtime restoration" }
        stopRime()
        // false still asks librime to detect changes. Rollback itself changes
        // directory timestamps, so that would redeploy the restored cache.
        startRime(fullCheck = false, restoreOnly = true)
        if (previous.schema != ".default") check(selectRimeSchema(previous.schema)) { "Previous input schema could not be restored" }
        check(hasUsableRimeDictionary()) { "Previous input dictionary could not be restored" }
        previous.options.forEach { (name, enabled) -> setRimeOption(name, enabled) }
        usingExistingResources = previous.usingExistingResources
        emitResponse()
    }

    /** Caller holds the non-reentrant maintenance mutex. */
    private suspend fun deployNativeLocked() {
        val deployFinished = CompletableDeferred<Boolean>()
        val deployHandler: (RimeMessage<*>) -> Unit = { message ->
            if (message is RimeMessage.DeployMessage) {
                when (message.data) {
                    RimeMessage.DeployMessage.State.Start -> Unit

                    RimeMessage.DeployMessage.State.Success -> {
                        deployFinished.complete(true)
                    }

                    RimeMessage.DeployMessage.State.Failure -> {
                        deployFinished.complete(false)
                    }
                }
            }
        }
        registerRimeMessageHandler(deployHandler)
        try {
            withRimeContext {
                restartNative(true)
            }
            val success =
                withContext(Dispatchers.IO) {
                    withTimeout(5.minutes) {
                        deployFinished.await()
                    }
                }
            if (!success) throw RimeUnavailableException(IllegalStateException("Rime deploy failed"))
        } finally {
            unregisterRimeMessageHandler(deployHandler)
        }
    }

    override suspend fun updateConfig() = RimeMaintenanceMutex.withLock {
        withRimeContext { restartNative(false) }
    }

    override suspend fun deployConfigFile(fileName: String, versionKey: String): Boolean = RimeMaintenanceMutex.withLock {
        withRimeContext { deployRimeConfigFile(fileName, versionKey) }
    }

    override suspend fun syncUserData(): Boolean = RimeMaintenanceMutex.withLock {
        // Import configurations and portable text dumps before native merging.
        // Never replace open binary user databases with a file-by-file SAF copy.
        // Suppress import progress so synchronization does not show deployment UI.
        if (UserDataManager.usesExternalSync() && UserDataManager.hasExternalAccess()) {
            if (UserDataManager.importToLocal(showProgress = false).isFailure) return@withLock false
        }
        // RimeSyncUserData schedules maintenance asynchronously and returns once the
        // worker is started. Wait for DeployMessage so callers (e.g. export) only
        // proceed after sync/<installation_id>/ has been written.
        val syncFinished = CompletableDeferred<Boolean>()
        val syncHandler: (RimeMessage<*>) -> Unit = { message ->
            if (message is RimeMessage.DeployMessage) {
                when (message.data) {
                    RimeMessage.DeployMessage.State.Success -> syncFinished.complete(true)
                    RimeMessage.DeployMessage.State.Failure -> syncFinished.complete(false)
                    else -> {}
                }
            }
        }
        registerRimeMessageHandler(syncHandler)
        val syncOk =
            try {
                val started = withRimeContext { syncRimeUserData() }
                if (!started) {
                    false
                } else {
                    withContext(Dispatchers.IO) {
                        withTimeout(5.minutes) {
                            syncFinished.await()
                        }
                    }
                }
            } finally {
                unregisterRimeMessageHandler(syncHandler)
            }
        if (!syncOk) return@withLock false
        if (!UserDataManager.usesExternalSync()) return@withLock true
        if (!UserDataManager.hasExternalAccess()) {
            Timber.w("Export skipped: no data path selected")
            return@withLock false
        }
        UserDataManager.exportToExternal().isSuccess
    }

    override suspend fun processKey(
        value: Int,
        modifiers: UInt,
        isVirtual: Boolean,
    ): Boolean = withRimeContext {
        processKeyInner(value, modifiers.toInt(), isVirtual)
    }

    override suspend fun processKey(
        value: KeyValue,
        modifiers: KeyModifiers,
        isVirtual: Boolean,
    ): Boolean = withRimeContext {
        processKeyInner(value.value, modifiers.toInt(), isVirtual)
    }

    override suspend fun simulateKeySequence(sequence: String): Boolean = withRimeContext {
        invalidateCandidates()
        Timber.d("simulateKeySequence")
        if (simulateRimeKeySequence(sequence)) {
            val commit = getRimeCommit()
            val input = getRimeRawInput()
            if (!commit.text.isNullOrEmpty() || input.isNotEmpty()) {
                emitResponse(commit)
                true
            } else {
                val literal = literalKeySequenceFallback(sequence)
                if (literal != null) {
                    emitResponse(CommitProto(literal))
                    false
                } else {
                    emitResponse()
                    true
                }
            }
        } else {
            emitResponse()
            false
        }.also { Timber.d("simulateKeySequence ${if (it) "success" else "failed"}") }
    }

    override suspend fun selectCandidate(idx: Int, global: Boolean, expectedSnapshot: CandidateSnapshot?): Boolean = withRimeContext {
        if (idx < 0) return@withRimeContext false
        // Theme key bindings without a row identity must never select a next-word suggestion.
        if (candidateSnapshotCached.isPrediction &&
            (PrivacyModeState.active || !AppPrefs.defaultInstance().candidates.nextWordPrediction.getValue())
        ) {
            return@withRimeContext false
        }
        if (!candidateSnapshots.acceptsSelection(expectedSnapshot, editorToken)) {
            expectedSnapshot?.let(InputLatencyLog::stale)
            return@withRimeContext false
        }
        invalidateCandidates()
        selectRimeCandidate(idx, global).also { emitResponse() }
    }

    override suspend fun deleteCandidate(idx: Int, global: Boolean, expectedSnapshot: CandidateSnapshot?): Boolean = withRimeContext {
        if (idx < 0) return@withRimeContext false
        if (privacyMode || candidateSnapshotCached.isPrediction || !candidateSnapshots.accepts(expectedSnapshot, editorToken)) {
            expectedSnapshot?.let(InputLatencyLog::stale)
            return@withRimeContext false
        }
        invalidateCandidates()
        deleteRimeCandidate(idx, global).also { emitResponse() }
    }

    override suspend fun pinCandidate(idx: Int, global: Boolean, expectedSnapshot: CandidateSnapshot): Boolean = withRimeContext {
        if (privacyMode || candidateSnapshotCached.isPrediction || !candidateSnapshots.accepts(expectedSnapshot, editorToken)) return@withRimeContext false
        val identity = getRimeCandidatePinIdentity(idx, global)
        if (identity.size != 3) return@withRimeContext false
        candidatePins.pin(CandidatePin(identity[0], identity[1], identity[2]))
        invalidateCandidates()
        publishCandidatePins()
        emitResponse()
        true
    }

    override suspend fun resetCandidateRanking(expectedSnapshot: CandidateSnapshot): Boolean = withRimeContext {
        if (privacyMode || candidateSnapshotCached.isPrediction || !candidateSnapshots.accepts(expectedSnapshot, editorToken)) return@withRimeContext false
        candidatePins.reset(getCurrentRimeSchema(), getRimeRawInput())
        invalidateCandidates()
        publishCandidatePins()
        emitResponse()
        true
    }

    private fun publishCandidatePins() {
        val entries = candidatePins.entries
        setRimeCandidatePins(
            entries.map { it.schema }.toTypedArray(),
            entries.map { it.input }.toTypedArray(),
            entries.map { it.text }.toTypedArray(),
        )
    }

    override suspend fun <T> maintainUserDictionaries(block: () -> T): T = RimeMaintenanceMutex.withLock {
        withRimeContext {
            val schemaId = getCurrentRimeSchema()
            val options = (schemaCached.switches.flatMap { if (it.name.isEmpty()) it.options else listOf(it.name) } + "ascii_mode")
                .distinct().associateWith(::getRimeOption)
            clearTransientEditorState()
            suspendRimeUserDictionarySession()
            var operationFailure: Throwable? = null
            try {
                block()
            } catch (failure: Throwable) {
                operationFailure = failure
                throw failure
            } finally {
                // Levers require initialized modules, so maintenance closes sessions
                // without finalizing Rime. Always reopen after success or rollback.
                try {
                    check(selectRimeSchema(schemaId)) { "Unable to reopen the input schema" }
                    options.forEach { (name, enabled) -> setRimeOption(name, enabled) }
                    setRimePrivacyMode(privacyMode)
                    applyPredictionOptions()
                    emitResponse()
                } catch (reopenFailure: Throwable) {
                    if (operationFailure != null) operationFailure.addSuppressed(reopenFailure) else throw reopenFailure
                }
            }
        }
    }

    override suspend fun changeCandidatePage(backward: Boolean, expectedSnapshot: CandidateSnapshot?): Boolean = withRimeContext {
        if (!candidateSnapshots.accepts(expectedSnapshot, editorToken)) {
            expectedSnapshot?.let(InputLatencyLog::stale)
            return@withRimeContext false
        }
        invalidateCandidates()
        changeRimeCandidatePage(backward).also { emitResponse() }
    }

    override suspend fun moveCursorPos(position: Int, expectedPreedit: String?) = withRimeContext {
        if (expectedPreedit != null && getRimeContext().composition.preedit != expectedPreedit) return@withRimeContext
        invalidateCandidates()
        setRimeCaretPos(position)
        emitResponse()
    }

    override suspend fun availableSchemata(): Array<SchemaItem> = withRimeContext { getAvailableRimeSchemaList() }

    override suspend fun enabledSchemata(): Array<SchemaItem> = withRimeContext { getSelectedRimeSchemaList() }

    override suspend fun setEnabledSchemata(schemaIds: Array<String>) = withRimeContext { selectRimeSchemas(schemaIds) }

    override suspend fun selectedSchemata(): Array<SchemaItem> = withRimeContext { getRimeSchemaList() }

    override suspend fun selectedSchemaId(): String = withRimeContext { getCurrentRimeSchema() }

    override suspend fun selectSchema(schemaId: String) = withRimeContext {
        invalidateCandidates()
        clearRimePredictionContext()
        selectRimeSchema(schemaId).also {
            applyPredictionOptions()
            emitResponse()
        }
    }

    override suspend fun currentSchema(): RimeSchema = withRimeContext {
        RimeSchema(getCurrentRimeSchema())
    }

    override suspend fun commitComposition(): Boolean = withRimeContext {
        invalidateCandidates()
        commitRimeComposition().also { emitResponse() }
    }

    override suspend fun commitT9Digit(digit: Char): Boolean = withRimeContext {
        require(digit in '0'..'9')
        invalidateCandidates()
        clearRimePredictionContext()
        var pending = ""
        if (getRimeRawInput().isNotEmpty()) {
            // Use the same full-input Return path as T02, including a caret inside a locked syllable.
            processRimeKey(RimeKeyMapping.RimeKey_Return, 0)
            // Read the commit before any snapshot can discard its private-to-raw T9 mapping.
            pending = getRimeCommit().text.orEmpty()
            if (getRimeRawInput().isNotEmpty()) {
                emitResponse(CommitProto(pending))
                return@withRimeContext false
            }
        }
        clearRimeComposition()
        emitResponse(CommitProto(pending + digit))
        true
    }

    override suspend fun clearComposition() = withRimeContext {
        invalidateCandidates()
        clearRimeComposition()
        emitResponse()
    }

    override suspend fun getRawInput(): String = withRimeContext {
        getRimeRawInput()
    }

    override suspend fun setRuntimeOption(
        option: String,
        value: Boolean,
    ): Unit = withRimeContext {
        invalidateCandidates()
        if (option == "prediction") {
            // A schema switch and the settings screen control the same user decision.
            AppPrefs.defaultInstance().candidates.nextWordPrediction.setValue(value)
            applyPredictionOptions()
        } else {
            setRimeOption(option, value)
        }
        emitResponse()
    }

    override suspend fun getRuntimeOption(option: String): Boolean = withRimeContext {
        getRimeOption(option)
    }

    override suspend fun setNullInputType(value: Boolean) = withRimeContext {
        if (isNullInputType != value) cancelAsciiSwitchTips()
        isNullInputType = value
        applyPredictionOptions()
    }

    override suspend fun getCandidates(
        startIndex: Int,
        limit: Int,
        expectedSnapshot: CandidateSnapshot?,
    ): Array<CandidateProto> = withRimeContext {
        if (!candidateSnapshots.accepts(expectedSnapshot, editorToken)) throw StaleCandidateSnapshotException()
        require(startIndex >= 0 && limit in 1..144)
        getRimeCandidates(startIndex, limit)
    }

    override suspend fun setCandidatePagingMode(enabled: Boolean) = withRimeContext {
        invalidateCandidates()
        pagingMode = enabled
        emitResponse()
    }

    private fun startRime(fullCheck: Boolean, restoreOnly: Boolean = false) {
        // Editor ownership outlives an in-place native restart/deployment.
        invalidateCandidates()
        usingExistingResources = false
        optionCache.clear()
        val mayRecover = DataManager.hasDeployedResources()
        val resourceFailure = if (restoreOnly) {
            // Sources, compiled artifacts and native state were restored by
            // the import transaction. Do not install or deploy anything here.
            null
        } else {
            try {
                DataManager.sync()
                if (UserDataManager.usesExternalSync() && !UserDataManager.hasExternalAccess()) {
                    IllegalStateException("External sync folder permission is unavailable; using local resources")
                } else {
                    null
                }
            } catch (failure: Exception) {
                if (!mayRecover) throw failure
                failure
            }
        }
        val sharedDataDir = DataManager.sharedDataDir.absolutePath
        val userDataDir = DataManager.userDataDir.absolutePath
        Timber.d(
            """
            Starting rime with:
            sharedDataDir: $sharedDataDir
            userDataDir: $userDataDir
            fullCheck: $fullCheck
            restoreOnly: $restoreOnly
            """.trimIndent(),
        )
        val deployFailed = java.util.concurrent.atomic.AtomicBoolean(false)
        val startupHandler: (RimeMessage<*>) -> Unit = { message ->
            if (message is RimeMessage.DeployMessage && message.data == RimeMessage.DeployMessage.State.Failure) {
                deployFailed.set(true)
            }
        }
        registerRimeMessageHandler(startupHandler)
        try {
            nativeInitialized = true
            if (restoreOnly) {
                restoreRime(sharedDataDir, userDataDir, BuildConfig.BUILD_VERSION_NAME)
            } else {
                UserDictManager.recoverPendingMutation()
                startupRime(sharedDataDir, userDataDir, BuildConfig.BUILD_VERSION_NAME, fullCheck)
            }
            // Corrupt pin data must not disable ordinary input or be overwritten.
            try {
                candidatePins.load()
                publishCandidatePins()
            } catch (failure: Exception) {
                setRimeCandidatePins(emptyArray(), emptyArray(), emptyArray())
                Timber.e("Candidate pin storage is unavailable (%s)", failure.javaClass.simpleName)
            }
            setRimePrivacyMode(editorToken != 0L && privacyMode)
            applyPredictionOptions()
            if (deployFailed.get() || resourceFailure != null) {
                check(hasUsableRimeDictionary()) { "Rime configuration deployment failed; no usable current dictionary" }
                usingExistingResources = true
                Timber.w(resourceFailure, "Deployment failed; using the current schema's validated existing dictionary")
                if (resourceFailure != null) handleRimeMessage(RimeMessage.MessageType.Deploy.ordinal, arrayOf("failure"))
            }
        } finally {
            unregisterRimeMessageHandler(startupHandler)
        }
        // Create the engine session and publish restored options before any view
        // uses the schema or toggle labels for the first time.
        emitResponse()
    }

    private fun stopRime() {
        clearTransientEditorState()
        // A delayed tip belongs to the native context that displayed it. It must
        // not queue a context read behind maintenance or clear the new engine's tip.
        lastAsciiTipsText = ""
        if (!nativeInitialized) return
        try {
            exitRime()
        } finally {
            nativeInitialized = false
        }
    }

    /** Clear process-global snapshots that can contain editor-owned text. */
    private fun clearTransientEditorState() {
        cancelAsciiSwitchTips()
        invalidateCandidates()
        compositionCached = CompositionProto()
        t9Cached = T9StateProto()
        hasMenu = false
        paging = false
        statusCached = statusCached.copy(isComposing = false)
    }

    private fun restartNative(fullCheck: Boolean) {
        try {
            stopRime()
            startRime(fullCheck)
        } catch (e: Exception) {
            dispatcher.fail(e)
            throw RimeUnavailableException(e)
        } catch (e: LinkageError) {
            dispatcher.fail(e)
            throw RimeUnavailableException(e)
        }
    }

    private fun processKeyInner(value: Int, modifiers: Int, isVirtual: Boolean): Boolean {
        invalidateCandidates()
        lastAsciiTipsText = asciiTipsText(getRimeStatus())
        val handled = processRimeKey(value, modifiers)
        emitResponse()
        if (!handled) {
            publishSnapshotMessage(
                10, // RimeMessage.MessageType.Key,
                arrayOf(value, modifiers, isVirtual),
                candidateSnapshotCached,
            )
        }
        return handled
    }

    private fun asciiTipsText(status: StatusProto): String = when {
        status.isAsciiMode -> "En"

        status.schemaName.isNotEmpty() && !status.schemaName.startsWith('.') ->
            status.schemaName.take(2)

        else -> ""
    }

    private fun emitResponse(commit: CommitProto? = null) {
        val response = getRimeResponse(pagingMode)
        val t9 = getRimeT9State(AppPrefs.defaultInstance().keyboard.t9AssistMask())
        InputLatencyLog.supersede(candidateSnapshots.current)
        val snapshot = candidateSnapshots.publish(editorToken, activeRequest?.id ?: 0, response.candidates.isPrediction)
        when (val candidates = response.candidates) {
            is Candidates.Paged -> candidates.snapshot = snapshot
            is Candidates.Bulk -> candidates.snapshot = snapshot
        }
        InputLatencyLog.computed(activeRequest, snapshot)
        publishSnapshotMessage(4, arrayOf(commit ?: response.commit), snapshot)
        handlePreedit(response.composition, t9.enabled, snapshot)
        if (response.composition.length <= 0 && lastAsciiTipsText != asciiTipsText(response.status)) {
            showAsciiSwitchTips(response.status)
        }
        when (val candidates = response.candidates) {
            is Candidates.Paged -> publishSnapshotMessage(7, arrayOf(candidates), snapshot)
            is Candidates.Bulk -> publishSnapshotMessage(9, arrayOf(candidates), snapshot)
        }
        publishSnapshotMessage(8, arrayOf(response.status), snapshot)
        publishSnapshotMessage(11, arrayOf(t9), snapshot)
    }

    private fun publishSnapshotMessage(type: Int, params: Array<Any>, snapshot: CandidateSnapshot) {
        dispatchRimeMessage(RimeMessage.nativeCreate(type, params).apply { this.snapshot = snapshot })
    }

    private fun handlePreedit(composition: CompositionProto, t9: Boolean = false, snapshot: CandidateSnapshot = CandidateSnapshot.EMPTY) {
        val mode = if (isNullInputType) {
            InlinePreeditMode.DISABLE
        } else {
            inlinePreeditMode
        }
        val response = editorPreeditResponse(composition, mode, t9, snapshot.isPrediction)
        publishSnapshotMessage(5, arrayOf(response.inlinePreedit), snapshot)
        publishSnapshotMessage(6, arrayOf(response.composition), snapshot)
    }

    private fun handleRimeMessage(it: RimeMessage<*>) {
        when (it) {
            is RimeMessage.SchemaMessage -> {
                invalidateCandidates()
                t9Cached = T9StateProto()
                optionCache.clear()
                statusCached = getRimeStatus()
                schemaCached = RimeSchema(it.data.id)
            }

            is RimeMessage.OptionMessage -> {
                invalidateCandidates()
                optionCache.update(it.data.option, it.data.value)
                // Option change won't trigger response update
                val status = getRimeStatus()
                statusCached = status
                updateSchemaCached(status)
                if (it.data.option == "ascii_mode") {
                    showAsciiSwitchTips(status)
                }
            }

            is RimeMessage.DeployMessage -> {
                if (it.data == RimeMessage.DeployMessage.State.Start) {
                    OpenCCDictManager.buildOpenCCDict()
                }
            }

            is RimeMessage.CompositionMessage -> {
                val composition = it.data
                compositionCached = composition
            }

            is RimeMessage.PagedCandidatesMessage -> {
                val paged = it.data
                paging = paged.hasPrevPage
                hasMenu = paged.candidates.isNotEmpty()
            }

            is RimeMessage.BulkCandidatesMessage -> {
                hasMenu = it.data.candidates.isNotEmpty()
            }

            is RimeMessage.StatusMessage -> {
                statusCached = it.data
                updateSchemaCached(it.data)
                refreshOptionCache()
            }

            is RimeMessage.T9Message -> t9Cached = it.data

            else -> {}
        }
    }

    private fun updateSchemaCached(status: StatusProto) {
        val (schemaId, schemaName) = status
        // Engine response update won't send SchemaMessage, but usually update RimeStatus
        if (schemaId != schemaCached.schemaId) {
            optionCache.clear()
            schemaCached = RimeSchema(schemaId)
            // notify downstream consumers that schema has changed
            messageBus.publish(
                RimeMessage.SchemaMessage(
                    SchemaItem(schemaId, schemaName),
                ),
            )
        }
    }

    private fun refreshOptionCache() {
        val options = schemaCached.switches.flatMap {
            if (it.name.isEmpty()) it.options else listOf(it.name)
        }
        (options + "ascii_mode").distinct().forEach { optionCache.update(it, getRimeOption(it)) }
    }

    private fun showAsciiSwitchTips(status: StatusProto) {
        // Startup/maintenance emits the same option/status messages as typing. Without an
        // editable owner, a timer can only enqueue a pointless context read behind deployment.
        if (!showAsciiSwitchTips || candidateSnapshotCached.isPrediction || !canShowAsciiSwitchTips()) return
        val tipsText = asciiTipsText(status)
        if (tipsText.isEmpty()) return

        lastAsciiTipsText = tipsText

        cancelAsciiSwitchTips()
        val generation = asciiSwitchTipsGeneration
        val owner = editorToken
        val tips = CompositionProto(tipsText)
        handleRimeMessage(6, arrayOf(tips))
        asciiSwitchTipsJob = lifecycleScope.launch {
            delay(1000L)
            // A finished editor, native restart or import invalidates this transient UI work.
            // Check before dispatch as well as before JNI so it cannot add maintenance backlog.
            if (generation != asciiSwitchTipsGeneration || owner != editorToken) return@launch
            withRimeContext {
                if (generation != asciiSwitchTipsGeneration || owner != editorToken || !canShowAsciiSwitchTips()) return@withRimeContext
                val ctx = getRimeContext()
                val snapshot = candidateSnapshotCached
                val mode = if (isNullInputType) InlinePreeditMode.DISABLE else inlinePreeditMode
                val response = editorPreeditResponse(ctx.composition, mode, t9Cached.enabled, snapshot.isPrediction)
                publishSnapshotMessage(6, arrayOf(response.composition), snapshot)
            }
        }
    }

    private fun canShowAsciiSwitchTips(): Boolean = editorToken != 0L && !isNullInputType && !configurationImportInProgress

    private fun cancelAsciiSwitchTips() {
        asciiSwitchTipsGeneration++
        asciiSwitchTipsJob?.cancel()
        asciiSwitchTipsJob = null
    }

    /**
     * Start the native Rime dispatcher when storage is ready and the lifecycle can start.
     *
     * App-scoped storage can appear late after reboot. That is a transient condition rather
     * than an engine failure, so leave the lifecycle unchanged and let the daemon retry.
     * An unavailable external sync tree must not block an already deployed local runtime.
     *
     * @return true when startup was accepted; false when it should be retried later or the
     * lifecycle is already running.
     */
    fun startup(fullCheck: Boolean = false): Boolean {
        if (!isStorageReadyForStartup()) {
            Timber.w("Skip starting rime: storage not available!")
            return false
        }
        if (lifecycle.currentState == RimeLifecycle.State.FAILED) dispatcher.stop()
        if (lifecycle.currentState != RimeLifecycle.State.STOPPED && lifecycle.currentState != RimeLifecycle.State.FAILED) {
            Timber.w("Skip starting rime: not at stopped state!")
            return false
        }
        registerRimeMessageHandler(::handleRimeMessage)
        startupFullCheck = fullCheck
        lifecycleRegistry.emitEvent(RimeLifecycle.Event.ON_START)
        dispatcher.start()
        return true
    }

    /** The storage predicate shared by startup and the daemon's bounded late-media poll. */
    internal fun isStorageReadyForStartup(): Boolean = UserDataManager.isStorageAvailable() ||
        (UserDataManager.isRuntimeReady() && DataManager.hasDeployedResources())

    /** Publish a bounded late-storage failure so callers can offer an explicit retry. */
    internal fun failStartup(error: Throwable): Boolean = if (lifecycle.currentState in setOf(RimeLifecycle.State.STOPPED, RimeLifecycle.State.FAILED)) {
        lifecycleRegistry.fail(error)
        true
    } else {
        false
    }

    fun beginShutdown() {
        lifecycleRegistry.emitEvent(RimeLifecycle.Event.ON_STOP)
    }

    fun finishShutdown() {
        val state = lifecycle.currentState
        if (state == RimeLifecycle.State.FAILED) throw RimeUnavailableException(lifecycle.failureCause)
        check(state == RimeLifecycle.State.STOPPING)
        Timber.i("Rime finalize()")
        dispatcher.stop().let {
            if (it.isNotEmpty()) {
                Timber.w("${it.size} job(s) didn't get a chance to run!")
            }
        }
        if (lifecycle.currentState == RimeLifecycle.State.FAILED) throw RimeUnavailableException(lifecycle.failureCause)
        lifecycleRegistry.emitEvent(RimeLifecycle.Event.ON_STOPPED)
        unregisterRimeMessageHandler(::handleRimeMessage)
    }

    companion object {
        @Volatile private var editorToken = 0L

        @Volatile private var configurationImportInProgress = false
        private val messageBus = RimeMessageBus()

        private val rimeMessageHandlers = CopyOnWriteArrayList<(RimeMessage<*>) -> Unit>()

        // init
        @JvmStatic
        external fun startupRime(
            sharedDir: String,
            userDir: String,
            versionName: String,
            fullCheck: Boolean,
        )

        /** Reopen only a successfully rolled-back, previously ready native context. */
        @JvmStatic
        private external fun restoreRime(sharedDir: String, userDir: String, versionName: String)

        @JvmStatic
        external fun exitRime()

        @JvmStatic
        private external fun suspendRimeUserDictionarySession()

        @JvmStatic
        private external fun setRimeCandidatePins(schemas: Array<String>, inputs: Array<String>, texts: Array<String>)

        @JvmStatic
        private external fun getRimeCandidatePinIdentity(index: Int, global: Boolean): Array<String>

        @JvmStatic
        private external fun hasUsableRimeDictionary(): Boolean

        @JvmStatic
        external fun deployRimeSchemaFile(schemaFile: String): Boolean

        @JvmStatic
        external fun deployRimeConfigFile(
            fileName: String,
            versionKey: String,
        ): Boolean

        @JvmStatic
        external fun syncRimeUserData(): Boolean

        // input
        @JvmStatic
        external fun getRimeT9State(assistOptions: Int): T9StateProto

        @JvmStatic
        external fun performRimeT9Action(
            revision: Int,
            action: Int,
            start: Int,
            end: Int,
            spelling: String,
            assistOptions: Int,
        ): Boolean

        @JvmStatic
        external fun processRimeKey(
            keycode: Int,
            mask: Int,
        ): Boolean

        @JvmStatic
        external fun commitRimeComposition(): Boolean

        @JvmStatic
        external fun clearRimeComposition()

        @JvmStatic
        external fun setRimePrivacyMode(enabled: Boolean)

        /** Global user request; native privacy and ASCII policies still take precedence. */
        @JvmStatic
        external fun setRimePredictionEnabled(enabled: Boolean): Boolean

        /** Clears only next-word state and its preceding commit context. */
        @JvmStatic
        external fun clearRimePredictionContext(): Boolean

        @JvmStatic
        external fun getRimeCommitHistorySizeForTesting(): Int

        @JvmStatic
        external fun isRimePrivacyGuardEnabledForTesting(): Boolean

        // output
        @JvmStatic
        external fun getRimeCommit(): CommitProto

        @JvmStatic
        external fun getRimeContext(): ContextProto

        @JvmStatic
        external fun getRimeStatus(): StatusProto

        // runtime options
        @JvmStatic
        external fun setRimeOption(
            option: String,
            value: Boolean,
        )

        @JvmStatic
        external fun getRimeOption(option: String): Boolean

        @JvmStatic
        external fun getRimeSchemaList(): Array<SchemaItem>

        @JvmStatic
        external fun getCurrentRimeSchema(): String

        @JvmStatic
        external fun selectRimeSchema(schemaId: String): Boolean

        // testing
        @JvmStatic
        external fun simulateRimeKeySequence(keySequence: String): Boolean

        @JvmStatic
        external fun getRimeRawInput(): String

        @JvmStatic
        external fun getRimeCaretPos(): Int

        @JvmStatic
        external fun setRimeCaretPos(caretPos: Int)

        @JvmStatic
        external fun selectRimeCandidate(index: Int, global: Boolean): Boolean

        @JvmStatic
        external fun deleteRimeCandidate(index: Int, global: Boolean): Boolean

        @JvmStatic
        external fun changeRimeCandidatePage(backward: Boolean): Boolean

        @JvmStatic
        external fun getAvailableRimeSchemaList(): Array<SchemaItem>

        @JvmStatic
        external fun getSelectedRimeSchemaList(): Array<SchemaItem>

        @JvmStatic
        external fun selectRimeSchemas(schemaIds: Array<String>): Boolean

        @JvmStatic
        external fun getRimeCandidates(
            startIndex: Int,
            limit: Int,
        ): Array<CandidateProto>

        @JvmStatic
        external fun getRimeResponse(pagingMode: Boolean): RimeResponse

        @JvmStatic
        fun handleRimeMessage(
            type: Int,
            params: Array<Any>,
        ) {
            dispatchRimeMessage(RimeMessage.nativeCreate(type, params))
        }

        private fun dispatchRimeMessage(message: RimeMessage<*>) {
            message.editorToken = editorToken
            if (message is RimeMessage.DeployMessage) message.isConfigurationImport = configurationImportInProgress
            Timber.d("Handling ${message.javaClass.simpleName}")
            messageBus.publish(message) {
                rimeMessageHandlers.forEach { it.invoke(message) }
            }
        }

        private fun registerRimeMessageHandler(handler: (RimeMessage<*>) -> Unit) {
            if (rimeMessageHandlers.contains(handler)) return
            rimeMessageHandlers.add(handler)
        }

        private fun unregisterRimeMessageHandler(handler: (RimeMessage<*>) -> Unit) {
            rimeMessageHandlers.remove(handler)
        }
    }
}
