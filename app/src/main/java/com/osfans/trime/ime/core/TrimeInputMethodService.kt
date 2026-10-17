/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.FrameLayout
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.TrimeApplication
import com.osfans.trime.core.CandidateSnapshot
import com.osfans.trime.core.KeyModifiers
import com.osfans.trime.core.KeyValue
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeKeyMapping
import com.osfans.trime.core.RimeLifecycle
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.compositionForEditor
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.db.ClipboardHelper
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.data.prefs.PreferenceDelegateProvider
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.composition.CandidatesView
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import com.osfans.trime.ime.performance.InputLatencyLog
import com.osfans.trime.receiver.RimeIntentReceiver
import com.osfans.trime.util.any
import com.osfans.trime.util.findSectionFrom
import com.osfans.trime.util.forceShowSelf
import com.osfans.trime.util.monitorCursorAnchor
import com.osfans.trime.util.styledFloat
import com.osfans.trime.util.toast
import com.osfans.trime.util.virtualKeyCharacterMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import splitties.bitflags.hasFlag
import splitties.systemservices.clipboardManager
import splitties.systemservices.inputMethodManager
import timber.log.Timber

private const val DESTROY_EDITOR_CLEANUP_TIMEOUT_MS = 45_000L

/** [輸入法][InputMethodService]主程序  */

open class TrimeInputMethodService : LifecycleInputMethodService() {
    private lateinit var rime: RimeSession
    private lateinit var rimeSessionName: String
    private val jobs = Channel<Job>(capacity = Channel.UNLIMITED)

    private val prefs = AppPrefs.defaultInstance()
    private lateinit var decorView: View
    private lateinit var contentView: FrameLayout
    private lateinit var lastKnownConfig: Configuration
    private var inputView: InputView? = null
    private var candidatesView: CandidatesView? = null
    private var themeReady = false
    private var inputViewRequested = false
    private var themeInitialization: Job? = null
    private var loadingPanel: FrameLayout? = null
    private var startupFailed = false
    private val editorGeneration = EditorGeneration()
    internal val editorToken: Long get() = editorGeneration.token
    internal fun isCurrentEditor(token: Long): Boolean = editorActive && !replacingDirectBootProcess && editorGeneration.accepts(token)
    private val inlineSuggestionRequestGate = InlineSuggestionRequestGate(::isCurrentEditor)
    private var editorActive = false

    @Volatile private var nativeBoundEditorToken = 0L

    @Volatile private var serviceDestroying = false
    private var privacyMode = PrivacyMode()
    private var privacyOwner = 0L
    private val editorOwnership = EditorOwnership()
    val isPrivacyModeActive: Boolean
        get() = replacingDirectBootProcess || (editorActive && privacyMode.active)

    private val replacingDirectBootProcess: Boolean
        get() = TrimeApplication.getInstance().isDirectBootProcessReplacementPending
    private val cursorAnchorSubscription = CursorAnchorSubscription()
    var editorModeOverride: EditorModeOverride? = null
    internal var auxiliaryKeyboardMode: EditorModeOverride? = null
    internal val keyboardLanguage = KeyboardLanguageState(prefs.keyboard.manualLanguageMode.getValue())

    /** Main-thread acceptance; every resulting command still executes through postRimeJob. */
    internal fun acceptKeyboardMode(
        targetAscii: Boolean?,
        rememberLanguage: Boolean,
        effect: KeyboardModeEffect,
    ): KeyboardModeCommand? {
        val info = currentInputEditorInfo ?: return null
        if (!isCurrentEditor(editorToken)) return null
        val schema = rime.run { statusCached.schemaId }
        val nativeAscii = rime.run { statusCached.isAsciiMode }
        val restricted = EditorPolicy.keyboard(info.inputType, info.imeOptions) != EditorKeyboard.USER
        return keyboardLanguage.accept(editorToken, schema, nativeAscii, targetAscii, restricted, rememberLanguage, effect).also {
            if (it.remembersLanguage) prefs.keyboard.manualLanguageMode.setValue(keyboardLanguage.remembered)
        }
    }

    internal fun completeKeyboardMode(command: KeyboardModeCommand, result: KeyboardModeResult): Boolean {
        if (!isCurrentEditor(command.editorToken)) return false
        val before = keyboardLanguage.remembered
        val repaint = keyboardLanguage.complete(command, result, editorToken, rime.run { statusCached.schemaId })
        if (before != keyboardLanguage.remembered) {
            prefs.keyboard.manualLanguageMode.setValue(keyboardLanguage.remembered)
        }
        return repaint
    }
    private val navBarManager = NavigationBarManager()
    private val inputDeviceManager = InputDeviceManager { useVirtualKeyboard, useCandidatesView ->
        postRimeJob {
            setCandidatePagingMode(useCandidatesView)
        }
        updateCursorAnchorSubscription(useCandidatesView)
        val scope = themeScope
        if (!themeReady || scope == null) return@InputDeviceManager
        window.window?.let {
            navBarManager.evaluate(it, useVirtualKeyboard, scope.colors)
        }
    }
    private val rimeIntentReceiver = RimeIntentReceiver()

    private var lastCommittedText: String = ""

    private var composingText: String = ""
    private var composingCursor: Int? = null
    private var composingStart: Int? = null
    private var composingSelectionMatches = true
    private var selectionAfterCancel: Pair<Int, Int>? = null

    @Volatile private var cursorUpdateIndex = 0

    private val recreateInputViewPrefs: Array<PreferenceDelegate<*>> = arrayOf(
        prefs.keyboard.expandKeypressArea,
        prefs.keyboard.hideKeySymbol,
        prefs.keyboard.hideKeyHint,
        prefs.keyboard.hideInputBar,
        prefs.keyboard.t9OneHand,
        prefs.keyboard.t9KeyHeight,
        prefs.keyboard.keyboardHeight,
        prefs.candidates.textSize,
        prefs.advanced.ignoreSystemGestureInsets,
    )

    private val themeScope: ThemeScope?
        get() = ColorManager.currentScope()

    @Keep
    private val recreateInputViewListener =
        PreferenceDelegate.OnChangeListener<Any> { _, _ ->
            val scope = themeScope
            if (themeReady && inputViewRequested && scope != null) replaceInputView(scope)
        }

    @Keep
    private val refreshT9AssistListener =
        PreferenceDelegate.OnChangeListener<Boolean> { _, _ ->
            postRimeJob { refreshT9Options() }
        }

    @Keep
    private val refreshPredictionListener =
        PreferenceDelegate.OnChangeListener<Boolean> { _, _ ->
            // Read the latest preference in FIFO; an older callback cannot re-enable it.
            postRimeJob { refreshPredictionOptions() }
        }

    @Keep
    private val manualPrivacyModeListener =
        PreferenceDelegate.OnChangeListener<Boolean> { _, enabled ->
            PrivacyModeState.setManual(enabled, ClipboardHelper::quarantineCurrentPrimaryClip)
            inlineSuggestionRequestGate.invalidate()
            if (Looper.myLooper() == Looper.getMainLooper()) {
                applyManualPrivacyMode(enabled)
            } else {
                ContextCompat.getMainExecutor(this).execute { applyManualPrivacyMode(enabled) }
            }
        }

    @Keep
    private val recreateCandidatesViewListener =
        PreferenceDelegateProvider.OnChangeListener {
            val scope = themeScope
            if (themeReady && inputViewRequested && scope != null) replaceCandidateView(scope)
        }

    @Keep
    private val onThemeChangeListener =
        ThemeManager.OnThemeChangeListener {
            val scope = themeScope
            if (themeReady && inputViewRequested && scope != null) replaceInputViews(scope)
        }

    @Keep
    private val onColorChangeListener =
        ColorManager.OnColorChangeListener {
            ContextCompat.getMainExecutor(this).execute {
                if (!themeReady) return@execute
                // A scheme-only change restyles the tree in place; theme
                // switches rebuild it through onThemeChangeListener.
                val scope = themeScope ?: return@execute
                inputView?.refreshColors()
                candidatesView?.refreshColors()
                window.window?.let {
                    navBarManager.evaluate(it, inputDeviceManager.useVirtualKeyboard, scope.colors)
                }
            }
        }

    private fun postJob(
        scope: CoroutineScope,
        block: suspend () -> Unit,
    ): Job {
        // Joining a LAZY coroutine starts it. Return a separate completion handle
        // so callers cannot accidentally bypass the serial queue.
        val completion = Job()
        val job = scope.launch(start = CoroutineStart.LAZY) { block() }
        job.invokeOnCompletion { failure ->
            if (failure == null) completion.complete() else completion.completeExceptionally(failure)
        }
        completion.invokeOnCompletion { failure ->
            if (failure is CancellationException) job.cancel(failure)
        }
        if (jobs.trySend(job).isFailure) job.cancel()
        return completion
    }

    /**
     * Post a rime operation to [jobs] to be executed
     *
     * Unlike `rime.runOnReady` or `rime.launchOnReady` where
     * subsequent operations can start if the prior operation is not finished (suspended),
     * [postRimeJob] ensures that operations are executed sequentially.
     */
    private var scopedFeedbackNanos = 0L

    /** Scope a specific KeyView gesture to synchronous queue submissions, never the last global touch. */
    fun <T> withInputFeedback(feedbackNanos: Long, action: () -> T): T {
        val previous = scopedFeedbackNanos
        scopedFeedbackNanos = feedbackNanos
        try {
            return action()
        } finally {
            scopedFeedbackNanos = previous
        }
    }

    fun postRimeJob(block: suspend RimeApi.() -> Unit): Job {
        val origin = editorGeneration.token
        val request = if (scopedFeedbackNanos > 0 || (editorActive && nativeBoundEditorToken == origin)) {
            InputLatencyLog.request(origin, scopedFeedbackNanos)
        } else {
            null // Startup, rebind and teardown can still belong to the previous native editor.
        }
        val operation: suspend () -> Unit = {
            if (!replacingDirectBootProcess && editorGeneration.accepts(origin)) {
                rime.runOnReady {
                    if (!replacingDirectBootProcess && editorGeneration.accepts(origin)) block()
                }
            }
        }
        if (request == null) return postJob(lifecycleScope, operation)
        return postJob(lifecycleScope) { withContext(request) { operation() } }.also { job ->
            job.invokeOnCompletion { failure ->
                val failed = failure != null && (failure !is CancellationException || failure is com.osfans.trime.core.RimeUnavailableException)
                InputLatencyLog.finish(request, failed)
            }
        }
    }

    private fun bindEditor() {
        val info = currentInputEditorInfo ?: return
        val origin = editorGeneration.token
        val targetMode = privacyMode
        // Rime caches still belong to the preceding editor until beginEditor returns.
        nativeBoundEditorToken = 0L
        postRimeJob {
            beginEditor(
                origin,
                info.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL,
                targetMode.active,
            )
            if (isCurrentEditor(origin)) nativeBoundEditorToken = origin
            // Releasing a previous private policy is part of the serialized native boundary.
            // The owner check keeps a stale bind from changing a newer editor's process policy.
            completeProcessPrivacyUpdate(origin, targetMode)
        }.invokeOnCompletion { failure ->
            // The fallback keyboard remains usable while Rime is unavailable. Do not leave a
            // transitional private snapshot stuck after a failed native bind.
            if (failure != null && !serviceDestroying) {
                if (nativeBoundEditorToken == origin) nativeBoundEditorToken = 0L
                completeProcessPrivacyUpdate(origin, targetMode)
            }
        }
    }

    private fun completeProcessPrivacyUpdate(
        owner: Long,
        mode: PrivacyMode,
    ) {
        PrivacyModeState.updateInput(owner, mode, ClipboardHelper::quarantineCurrentPrimaryClip)
    }

    private fun completeProcessPrivacyEnd(owner: Long) {
        PrivacyModeState.endInput(owner, ClipboardHelper::quarantineCurrentPrimaryClip)
    }

    private fun applyManualPrivacyMode(enabled: Boolean) {
        if (editorActive) {
            updatePrivacyMode(enabled)
        } else {
            ClipboardHelper.clearTransientSuggestion()
        }
    }

    private fun updatePrivacyMode(manual: Boolean = prefs.general.manualPrivacyMode.getValue()) {
        if (replacingDirectBootProcess) {
            deferInputUntilProcessReplacement()
            return
        }
        val info = currentInputEditorInfo ?: return
        val updated = EditorPolicy.privacy(info, manual)
        if (updated == privacyMode) return
        inlineSuggestionRequestGate.invalidate()
        InputLatencyLog.reset()
        editorGeneration.advance()
        nativeBoundEditorToken = 0L
        privacyOwner = editorGeneration.token
        editorOwnership.add(privacyOwner)
        privacyMode = updated
        val retainPrivacyUntilUpdate = PrivacyModeState.active && !updated.active
        PrivacyModeState.beginInput(
            privacyOwner,
            updated,
            retainPrivacyUntilUpdate = retainPrivacyUntilUpdate,
        )
        lastCommittedText = ""
        composingText = ""
        composingCursor = null
        composingStart = null
        selectionAfterCancel = null
        inputView?.clearEditorBoundaryState(updated.active)
        inputView?.resetEditorUi()
        candidatesView?.resetEditorUi()
        ClipboardHelper.clearTransientSuggestion()
        InputFeedbackManager.startInput(sensitive = updated.active)
        bindEditor()
        // Advancing the editor generation invalidates queued operations from the preceding
        // privacy boundary, including a restricted editor's pending ASCII-mode restoration.
        // Reapply only the current native mode after the new bind so that a manual privacy toggle
        // cannot strand a normal editor in restricted ASCII mode or change its visible layout.
        if (themeReady) {
            if (inputDeviceManager.useVirtualKeyboard) {
                inputView?.reapplyCurrentEditorMode()
            } else {
                inputView?.restoreModeForHardwareEditor(info)
            }
        }
    }

    private suspend fun updateRimeOption(api: RimeApi) {
        try {
            api.setRuntimeOption("soft_cursor", prefs.keyboard.useSoftCursor.getValue()) // 軟光標
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun registerReceiver() {
        val intentFilter =
            IntentFilter().apply {
                addAction(RimeIntentReceiver.ACTION_DEPLOY)
                addAction(RimeIntentReceiver.ACTION_SYNC_USER_DATA)
            }
        ContextCompat.registerReceiver(
            this,
            rimeIntentReceiver,
            intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onCreate() {
        rimeSessionName = imeRimeSessionName(javaClass.name, editorGeneration.token)
        rime = RimeDaemon.createSession(rimeSessionName)
        lifecycleScope.launch {
            jobs.consumeEach { it.join() }
        }
        lifecycleScope.launch {
            rime.run { messageFlow }.collect {
                handleRimeMessage(it)
            }
        }
        recreateInputViewPrefs.forEach {
            it.registerOnChangeListener(recreateInputViewListener)
        }
        prefs.keyboard.t9AssistOptions.forEach { it.registerOnChangeListener(refreshT9AssistListener) }
        prefs.candidates.nextWordPrediction.registerOnChangeListener(refreshPredictionListener)
        prefs.general.manualPrivacyMode.registerOnChangeListener(manualPrivacyModeListener)
        prefs.candidates.registerOnChangeListener(recreateCandidatesViewListener)
        // ensure theme and color managers are initialized after rime is ready
        prepareTheme()
        lifecycleScope.launch {
            RimeDaemon.engineState.collect { state ->
                when (state) {
                    RimeLifecycle.State.READY -> if (!themeReady) prepareTheme()

                    RimeLifecycle.State.FAILED, RimeLifecycle.State.STOPPING -> {
                        nativeBoundEditorToken = 0L
                        startupFailed = state == RimeLifecycle.State.FAILED
                        themeReady = false
                        inputView?.finishInput()
                        if (inputViewRequested) setInputView(createUnavailableInputView())
                    }

                    else -> Unit
                }
            }
        }
        InputFeedbackManager.init()
        registerReceiver()
        super.onCreate()
        Timber.d("onCreate")
        decorView = window.window!!.decorView
        contentView = decorView.findViewById(android.R.id.content)
        lastKnownConfig = Configuration(resources.configuration)
    }

    private fun prepareTheme(retry: Boolean = false) {
        if (themeInitialization?.isActive == true) return
        themeInitialization = lifecycleScope.launch {
            try {
                if (retry) RimeDaemon.retryFailedStartup()
                rime.runOnReady { ThemeManager.init(resources.configuration) }
                val scope = themeScope
                if (scope == null) {
                    themeReady = false
                    startupFailed = false
                    Timber.w("ThemeScope is not ready after theme initialization; keeping fallback input view")
                    showThemeLoading()
                    return@launch
                }
                themeReady = true
                startupFailed = false
                if (rime.run { usingExistingResources }) toast(R.string.ime_existing_resources)
                ThemeManager.addOnChangedListener(onThemeChangeListener)
                ColorManager.addOnChangedListener(onColorChangeListener)
                if (inputViewRequested) {
                    replaceInputViews(scope)
                }
            } catch (e: Exception) {
                if (e is CancellationException && e !is com.osfans.trime.core.RimeUnavailableException) throw e
                Timber.e(e, "Failed to initialize keyboard theme")
                startupFailed = true
                showThemeLoading()
            }
        }
    }

    private fun showThemeLoading() {
        loadingPanel?.apply {
            removeAllViews()
            addView(
                EngineUnavailableView(
                    context,
                    failed = startupFailed,
                    retry = {
                        startupFailed = false
                        showThemeLoading()
                        prepareTheme(retry = true)
                    },
                    commit = { if (editorActive) commitText(it) },
                    delete = { if (editorActive) sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) },
                    enter = { if (editorActive) handleReturnKey() },
                ),
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            updateUnavailableEditor()
        }
    }

    private fun updateUnavailableEditor() {
        val view = loadingPanel?.getChildAt(0) as? EngineUnavailableView ?: return
        val info = currentInputEditorInfo
        val action = info?.let(EditorPolicy::enterAction)
        val label = if (action != null && action == info.actionId && !info.actionLabel.isNullOrEmpty()) {
            info.actionLabel
        } else {
            action?.let(::getTextForImeAction)
        }
        view.updateEnterAction(action, label ?: getString(R.string.ime_enter))
    }

    private fun createUnavailableInputView(): View = FrameLayout(this).apply {
        inputView = null
        inputDeviceManager.setInputView(null)
        contentView.removeView(candidatesView)
        candidatesView = null
        inputDeviceManager.setCandidatesView(null)
        val panel = FrameLayout(context)
        loadingPanel = panel
        addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        ViewCompat.setOnApplyWindowInsetsListener(panel) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.mandatorySystemGestures())
            view.setPadding(safe.left, 0, safe.right, safe.bottom)
            insets
        }
        panel.doOnAttach { it.requestApplyInsets() }
        showThemeLoading()
    }

    private fun handleRimeMessage(it: RimeMessage<*>) {
        if (it.isEditorResponse && !isCurrentEditor(it.editorToken)) return
        when (it) {
            is RimeMessage.CommitTextMessage -> {
                if (!it.data.text.isNullOrEmpty()) {
                    commitText(it.data.text, it.snapshot)
                }
            }

            is RimeMessage.InlinePreeditMessage -> {
                updateComposingText(it.data.text, it.data.cursor)
            }

            is RimeMessage.KeyMessage -> {
                val snapshot = it.snapshot
                it.data.let msg@{
                    if (it.isVirtual) {
                        val keyCode = it.value.keyCode
                        val metaState = it.modifiers.metaState or meta(
                            alt = it.modifiers.alt,
                            shift = it.modifiers.shift,
                            ctrl = it.modifiers.ctrl,
                            meta = it.modifiers.meta,
                        )
                        val text = virtualKeyText(
                            inputType = currentInputEditorInfo?.inputType,
                            isVirtual = it.isVirtual,
                            value = it.value.value,
                            keyCode = keyCode,
                            modifiers = it.modifiers,
                            metaState = metaState,
                            resolveCharacter = { code, state -> virtualKeyCharacterMap.get(code, state) },
                        )
                        if (text != null) {
                            // Keep printable text and following composition updates on the same
                            // InputConnection path; sendKeyEvent delivers through ViewRoot later.
                            commitText(text, snapshot, updateCommittedContent = keyCode == KeyEvent.KEYCODE_UNKNOWN)
                            return@msg
                        }
                        when (it.value.value) {
                            RimeKeyMapping.RimeKey_Return ->
                                if (it.modifiers.isReturnChord()) {
                                    sendDownUpKeyEvent(
                                        it.value.keyCode,
                                        it.modifiers.metaState or meta(
                                            alt = it.modifiers.alt,
                                            shift = it.modifiers.shift,
                                            ctrl = it.modifiers.ctrl,
                                            meta = it.modifiers.meta,
                                        ),
                                    )
                                } else {
                                    handleReturnKey()
                                }

                            else -> {
                                if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                                    // recognized keyCode
                                    sendDownUpKeyEvent(keyCode, metaState)
                                    if (it.modifiers.ctrl && keyCode == KeyEvent.KEYCODE_C) clearTextSelection()
                                } else {
                                    if (it.value.value > 0) {
                                        runCatching {
                                            commitText(Character.toString(it.value.value), snapshot)
                                        }.getOrElse { t -> Timber.w(t, "Unhandled Virtual KeyEvent") }
                                    } else {
                                        Timber.w("Unhandled Virtual KeyEvent")
                                    }
                                }
                            }
                        }
                    } else {
                        val keyCode = it.value.keyCode
                        if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                            // recognized keyCode
                            val eventTime = SystemClock.uptimeMillis()
                            if (it.modifiers.release) {
                                sendUpKeyEvent(eventTime, keyCode, it.modifiers.metaState)
                            } else {
                                sendDownKeyEvent(eventTime, keyCode, it.modifiers.metaState)
                            }
                        } else {
                            if (!it.modifiers.release && it.value.value > 0) {
                                runCatching {
                                    commitText(Character.toString(it.value.value), snapshot)
                                }.getOrElse { t -> Timber.w(t, "Unhandled Rime KeyEvent") }
                            } else {
                                Timber.w("Unhandled Rime KeyEvent")
                            }
                        }
                    }
                }
            }

            is RimeMessage.DeployMessage -> {
                if (!themeReady && it.data == RimeMessage.DeployMessage.State.Success) prepareTheme()
                if (themeReady && it.data == RimeMessage.DeployMessage.State.Success && !it.isConfigurationImport) {
                    // The deployment may have refreshed the current theme's artifact.
                    val themeId = ThemeManager.prefs.selectedTheme.getValue()
                    lifecycleScope.launch {
                        try {
                            ThemeManager.selectTheme(themeId, forceReload = true)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to reload keyboard theme after deployment")
                        }
                    }
                }
            }

            else -> {}
        }
    }

    private fun replaceInputView(scope: ThemeScope): InputView {
        inputView?.finishInput()
        val newInputView = InputView(this, rime, scope)
        setInputView(newInputView)
        inputDeviceManager.setInputView(newInputView)
        inputView = newInputView
        if (editorActive) {
            newInputView.startInput(currentInputEditorInfo, false)
            // The replacement has no candidate history. Redraw the current snapshot
            // without committing or clearing the unfinished native input.
            postRimeJob { refreshT9Options() }
        }
        return newInputView
    }

    private fun replaceCandidateView(scope: ThemeScope): CandidatesView {
        val newCandidatesView = CandidatesView(this, rime, scope)
        contentView.removeView(candidatesView)
        contentView.addView(newCandidatesView)
        inputDeviceManager.setCandidatesView(newCandidatesView)
        candidatesView = newCandidatesView
        if (decorLocationUpdated) {
            candidatesView?.updateCursorAnchor(anchorPosition, contentSize)
        } else {
            candidatesView?.updateCursorAnchor(contentSize)
        }
        return newCandidatesView
    }

    private fun replaceInputViews(scope: ThemeScope) {
        loadingPanel = null
        navBarManager.evaluate(window.window!!, inputDeviceManager.useVirtualKeyboard, scope.colors)
        replaceInputView(scope)
        replaceCandidateView(scope)
        if (editorActive) bindEditor()
    }

    override fun onDestroy() {
        val destroyedEditors = editorOwnership.snapshot()
        val destroyedOwner = privacyOwner
        serviceDestroying = true
        inlineSuggestionRequestGate.invalidate()
        nativeBoundEditorToken = 0L
        privacyOwner = 0
        editorActive = false
        InputLatencyLog.reset()
        editorGeneration.advance()
        inputView?.finishInput()
        themeReady = false
        inputViewRequested = false
        themeInitialization?.cancel()
        loadingPanel = null
        InputFeedbackManager.destroy()
        inputView = null
        recreateInputViewPrefs.forEach {
            it.unregisterOnChangeListener(recreateInputViewListener)
        }
        prefs.keyboard.t9AssistOptions.forEach { it.unregisterOnChangeListener(refreshT9AssistListener) }
        prefs.candidates.nextWordPrediction.unregisterOnChangeListener(refreshPredictionListener)
        prefs.general.manualPrivacyMode.unregisterOnChangeListener(manualPrivacyModeListener)
        prefs.candidates.unregisterOnChangeListener(recreateCandidatesViewListener)
        ThemeManager.removeOnChangedListener(onThemeChangeListener)
        ColorManager.removeOnChangedListener(onColorChangeListener)
        super.onDestroy()
        unregisterReceiver(rimeIntentReceiver)
        if (::rime.isInitialized && ::rimeSessionName.isInitialized && destroyedEditors.isNotEmpty()) {
            val destroyedSession = rime
            val destroyedSessionName = rimeSessionName
            TrimeApplication.getInstance().coroutineScope.launch {
                try {
                    withTimeout(DESTROY_EDITOR_CLEANUP_TIMEOUT_MS) {
                        destroyedSession.runOnReady { finishOwnedEditors(destroyedEditors) }
                    }
                } catch (failure: Exception) {
                    Timber.w(failure, "Unable to finish editors owned by the destroyed IME")
                } catch (failure: LinkageError) {
                    Timber.w(failure, "Unable to finish editors owned by the destroyed IME")
                } finally {
                    completeProcessPrivacyEnd(destroyedOwner)
                    RimeDaemon.destroySession(destroyedSessionName)
                }
            }
        } else if (::rimeSessionName.isInitialized) {
            completeProcessPrivacyEnd(destroyedOwner)
            RimeDaemon.destroySession(rimeSessionName)
        } else {
            completeProcessPrivacyEnd(destroyedOwner)
        }
    }

    private fun handleReturnKey() {
        val info = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        val action = EditorPolicy.enterAction(info)
        if (action == null) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) else ic.performEditorAction(action)
    }

    /**
     * https://github.com/fcitx5-android/fcitx5-android/blob/fe3a618c8fd18842305d2f8ec2880fcc67ec1679/app/src/main/java/org/fcitx/fcitx5/android/input/FcitxInputMethodService.kt#L523-#L547
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        postRimeJob { clearComposition() }
        val keyboardUiModeMask = ActivityInfo.CONFIG_KEYBOARD or
            ActivityInfo.CONFIG_KEYBOARD_HIDDEN or
            ActivityInfo.CONFIG_UI_MODE
        val diff = lastKnownConfig.diff(newConfig)
        Timber.d("onConfigurationChanged diff=$diff")
        if (diff and keyboardUiModeMask != diff) {
            super.onConfigurationChanged(newConfig)
        }
        lastKnownConfig.setTo(newConfig)
    }

    private val contentSize = floatArrayOf(0f, 0f)
    private val decorLocation = floatArrayOf(0f, 0f)
    private val decorLocationInt = intArrayOf(0, 0)
    private var decorLocationUpdated = false

    private fun updateDecorLocation() {
        contentSize[0] = contentView.width.toFloat()
        contentSize[1] =
            if (inputDeviceManager.useVirtualKeyboard) {
                inputViewLocation[1].toFloat()
            } else {
                contentView.height.toFloat()
            }
        decorView.getLocationOnScreen(decorLocationInt)
        decorLocation[0] = decorLocationInt[0].toFloat()
        decorLocation[1] = decorLocationInt[1].toFloat()
        // contentSize and decorLocation can be completely wrong,
        // when measuring right after the very first onStartInputView() of an IMS' lifecycle
        if (contentSize[0] > 0 && contentSize[1] > 0) {
            decorLocationUpdated = true
        }
    }

    private val anchorPosition = RectF()

    override fun onUpdateCursorAnchorInfo(info: CursorAnchorInfo) {
        if (!isInputViewShown || !inputDeviceManager.useCandidatesView) return
        val bounds = info.getCharacterBounds(0)
        // update anchorPosition
        if (bounds == null) {
            // composing is disabled in target app or trime settings
            // use the position of the insertion marker instead
            anchorPosition.top = info.insertionMarkerTop
            anchorPosition.left = info.insertionMarkerHorizontal
            anchorPosition.bottom = info.insertionMarkerBottom
            anchorPosition.right = info.insertionMarkerHorizontal
        } else {
            // for different writing system (e.g. right to left languages),
            // we have to calculate the correct RectF
            val horizontal = if (candidatesView?.layoutDirection == View.LAYOUT_DIRECTION_RTL) bounds.right else bounds.left
            anchorPosition.top = bounds.top
            anchorPosition.left = horizontal
            anchorPosition.bottom = bounds.bottom
            anchorPosition.right = horizontal
        }
        if (!decorLocationUpdated) {
            updateDecorLocation()
        }
        if (anchorPosition.any(Float::isNaN)) {
            candidatesView?.updateCursorAnchor(contentSize)
            return
        }
        info.matrix.mapRect(anchorPosition)
        val (dX, dY) = decorLocation
        anchorPosition.offset(-dX, -dY)
        candidatesView?.updateCursorAnchor(anchorPosition, contentSize)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd,
        )
        cursorUpdateIndex += 1
        handleCursorUpdate(newSelStart, newSelEnd, candidatesStart, candidatesEnd, cursorUpdateIndex)
        inputView?.updateSelection(newSelStart, newSelEnd)
    }

    private fun handleCursorUpdate(
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int,
        updateIndex: Int,
    ) {
        if (composingText.isEmpty()) return
        if (newSelStart < 0 || newSelEnd < 0) return
        if (candidatesStart < 0 || candidatesEnd - candidatesStart != composingText.length) return
        composingStart = candidatesStart
        if (newSelStart in candidatesStart..candidatesEnd && newSelEnd in candidatesStart..candidatesEnd) {
            selectionAfterCancel = null
            if (composingCursor == null) return
            val position = newSelEnd - candidatesStart
            composingSelectionMatches = newSelStart == newSelEnd && position == composingCursor
            if (newSelStart != newSelEnd) {
                // Rime has one editing caret, not a range. Keep the active end.
                currentInputConnection?.setSelection(newSelEnd, newSelEnd)
            }
            if (position != composingCursor) {
                val expectedPreedit = composingText
                val bytes = expectedPreedit.substring(0, position).toByteArray(Charsets.UTF_8).size
                postRimeJob {
                    if (updateIndex != cursorUpdateIndex) return@postRimeJob
                    Timber.d("handleCursorUpdate: move rime cursor to $position")
                    moveCursorPos(bytes, expectedPreedit)
                }
            }
        } else {
            fun afterRemoval(position: Int) = when {
                position <= candidatesStart -> position
                position >= candidatesEnd -> position - composingText.length
                else -> candidatesStart
            }
            selectionAfterCancel = afterRemoval(newSelStart) to afterRemoval(newSelEnd)
            Timber.d("handleCursorUpdate: clear composition")
            postRimeJob {
                if (updateIndex != cursorUpdateIndex) return@postRimeJob
                clearComposition()
            }
        }
    }

    private val inputViewLocation = intArrayOf(0, 0)
    private val touchableViewLocation = intArrayOf(0, 0)
    private val touchableViewBounds = Rect()

    private fun includeTouchableView(outInsets: Insets, view: View?) {
        if (view == null || !view.isAttachedToWindow || !view.isShown ||
            view.windowVisibility != View.VISIBLE || view.windowToken != decorView.windowToken ||
            !view.getLocalVisibleRect(touchableViewBounds)
        ) {
            return
        }
        view.getLocationInWindow(touchableViewLocation)
        // Local visible bounds are in content coordinates and include the view's own scroll.
        touchableViewBounds.offset(touchableViewLocation[0] - view.scrollX, touchableViewLocation[1] - view.scrollY)
        if (touchableViewBounds.intersect(0, 0, decorView.width, decorView.height)) {
            outInsets.touchableRegion.op(touchableViewBounds, Region.Op.UNION)
        }
    }

    override fun onComputeInsets(outInsets: Insets) {
        if (inputDeviceManager.useVirtualKeyboard || !themeReady) {
            // The panel content moves inside its viewport when the whole panel scrolls.
            // Only the stable viewport defines the editor and touchable-area boundary.
            val visibleView = inputView?.keyboardViewport ?: loadingPanel
            if (visibleView == null) {
                inputViewLocation[1] = decorView.height
            } else {
                visibleView.getLocationInWindow(inputViewLocation)
            }
            outInsets.apply {
                contentTopInsets = inputViewLocation[1]
                visibleTopInsets = inputViewLocation[1]
            }
        } else {
            val n = decorView.findViewById<View>(android.R.id.navigationBarBackground)?.height ?: 0
            val h = decorView.height - n
            outInsets.apply {
                contentTopInsets = h
                visibleTopInsets = h
            }
        }
        // Preedit and floating candidates already belong to this IME's view tree.
        // Include their exact visible bounds instead of creating touch-only PopupWindows:
        // rapid composition changes must not synchronously destroy another HWUI surface.
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(
            0,
            outInsets.visibleTopInsets.coerceIn(0, decorView.height),
            decorView.width,
            decorView.height,
        )
        if (editorActive) {
            includeTouchableView(outInsets, inputView?.touchablePreeditView)
            includeTouchableView(outInsets, candidatesView)
        }
    }

    // always show InputView since we delegate CandidatesView's visibility to it
    @SuppressLint("MissingSuperCall")
    override fun onEvaluateInputViewShown() = true

    fun superEvaluateInputViewShown() = super.onEvaluateInputViewShown()

    override fun onCreateInputView(): View? {
        Timber.d("onCreateInputView")
        inputViewRequested = true
        val scope = themeScope
        if (!themeReady || scope == null) {
            if (scope == null && themeReady) {
                Timber.w("ThemeScope disappeared; returning fallback input view")
                themeReady = false
            }
            if (!startupFailed) prepareTheme()
            return createUnavailableInputView()
        }
        replaceInputViews(scope)
        // We will call `setInputView` by ourselves. This is fine.
        return null
    }

    override fun setInputView(view: View) {
        super.setInputView(view)
        val inputArea = contentView.findViewById<FrameLayout>(android.R.id.inputArea)
        inputArea.updateLayoutParams<ViewGroup.LayoutParams> {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        view.updateLayoutParams<ViewGroup.LayoutParams> {
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    override fun onConfigureWindow(
        win: Window,
        isFullscreen: Boolean,
        isCandidatesOnly: Boolean,
    ) {
        win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onStartInput(
        attribute: EditorInfo,
        restarting: Boolean,
    ) {
        val replacementPending = TrimeApplication.getInstance().requestDirectBootProcessReplacementIfUnlocked()
        inlineSuggestionRequestGate.invalidate()
        super.onStartInput(attribute, restarting)
        if (replacementPending) {
            deferInputUntilProcessReplacement()
            Timber.d("onStartInput: restarting=$restarting; waiting for direct-boot process replacement")
            return
        }
        cursorAnchorSubscription.reset()
        InputLatencyLog.reset()
        editorGeneration.advance()
        nativeBoundEditorToken = 0L
        editorActive = true
        keyboardLanguage.reloadRemembered(prefs.keyboard.manualLanguageMode.getValue())
        privacyOwner = editorGeneration.token
        editorOwnership.add(privacyOwner)
        privacyMode = EditorPolicy.privacy(attribute, prefs.general.manualPrivacyMode.getValue())
        val retainPrivacyUntilUpdate = PrivacyModeState.active && !privacyMode.active
        PrivacyModeState.beginInput(
            privacyOwner,
            privacyMode,
            retainPrivacyUntilUpdate = retainPrivacyUntilUpdate,
        )
        // Publish the editor policy before any view teardown or asynchronous clipboard callback
        // can observe content that belongs to the new editor.
        inputView?.finishInput()
        inputView?.clearEditorBoundaryState(privacyMode.active)
        inputView?.resetEditorUi()
        candidatesView?.resetEditorUi()
        if (privacyMode.active) ClipboardHelper.clearTransientSuggestion()
        lastCommittedText = ""
        InputFeedbackManager.startInput(sensitive = privacyMode.active)
        composingText = ""
        composingCursor = null
        composingStart = null
        selectionAfterCancel = null
        cursorUpdateIndex += 1
        Timber.d("onStartInput: restarting=$restarting")
        bindEditor()
    }

    private fun deferInputUntilProcessReplacement() {
        inlineSuggestionRequestGate.invalidate()
        InputLatencyLog.reset()
        editorGeneration.advance()
        nativeBoundEditorToken = 0L
        editorActive = false
        cursorAnchorSubscription.reset()
        privacyMode = PrivacyMode(appRequested = true)
        inputView?.finishInput()
        inputView?.clearEditorBoundaryState(privateInput = true)
        inputView?.resetEditorUi()
        candidatesView?.resetEditorUi()
        ClipboardHelper.clearTransientSuggestion()
        InputFeedbackManager.startInput(sensitive = true)
        lastCommittedText = ""
        composingText = ""
        composingCursor = null
        composingStart = null
        selectionAfterCancel = null
        cursorUpdateIndex += 1
    }

    override fun onFinishInput() {
        inlineSuggestionRequestGate.invalidate()
        val finishedEditor = privacyOwner
        nativeBoundEditorToken = 0L
        editorActive = false
        cursorAnchorSubscription.reset()
        showingDialog?.dismiss()
        lastCommittedText = ""
        InputFeedbackManager.finishInput()
        InputLatencyLog.reset()
        editorGeneration.advance()
        postRimeJob { finishEditor(finishedEditor) }
            .invokeOnCompletion {
                // Engine failure must not strand the Java fallback keyboard in privacy mode.
                // Service destruction is cleaned from the application scope in onDestroy.
                // A newer editor has a different owner, so stale cleanup cannot clear it.
                if (!serviceDestroying) completeProcessPrivacyEnd(finishedEditor)
            }
        inputView?.finishInput()
        inputView?.clearEditorBoundaryState(privateInput = replacingDirectBootProcess)
        inputView?.resetEditorUi()
        candidatesView?.resetEditorUi()
        composingText = ""
        composingCursor = null
        composingStart = null
        selectionAfterCancel = null
        privacyMode = PrivacyMode(
            appRequested = replacingDirectBootProcess,
            manual = prefs.general.manualPrivacyMode.getValue(),
        )
        cursorUpdateIndex += 1
        super.onFinishInput()
    }

    private val inlineSuggestions by prefs.general.inlineSuggestions

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        inlineSuggestionRequestGate.invalidate()
        if (PrivacyModeState.active || !themeReady || !inlineSuggestions || !inputDeviceManager.useVirtualKeyboard) return null
        val colors = themeScope?.colors ?: return null
        val request = InlineSuggestions.createRequest(this, colors)
        if (!inlineSuggestionRequestGate.record(editorToken, privacyMode.active || PrivacyModeState.active)) return null
        return request
    }

    @SuppressLint("NewApi")
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        if (PrivacyModeState.active) {
            inlineSuggestionRequestGate.invalidate()
            return false
        }
        if (!inputDeviceManager.useVirtualKeyboard) return false
        val requestEditorToken = inlineSuggestionRequestGate.acceptedEditorToken() ?: return false
        return inputView?.handleInlineSuggestions(response, requestEditorToken) == true
    }

    override fun onStartInputView(
        attribute: EditorInfo,
        restarting: Boolean,
    ) {
        Timber.d("onStartInputView: restarting=$restarting")
        if (TrimeApplication.getInstance().requestDirectBootProcessReplacementIfUnlocked()) {
            deferInputUntilProcessReplacement()
            return
        }
        if (!themeReady) updateUnavailableEditor()
        InputFeedbackManager.startInput(sensitive = privacyMode.active)
        postRimeJob {
            updateRimeOption(this)
        }
        val (useVirtualKeyboard, useCandidatesView) =
            inputDeviceManager.evaluateOnStartInputView(attribute, this)
        if (useVirtualKeyboard) {
            inputView?.startInput(attribute, restarting)
        } else {
            inputView?.restoreModeForHardwareEditor(attribute)
        }
        val cursorUpdatesScheduled = updateCursorAnchorSubscription(useCandidatesView)
        if (useCandidatesView && !cursorUpdatesScheduled) {
            if (!decorLocationUpdated) {
                updateDecorLocation()
            }
            // anchor CandidatesView to bottom-left corner in case InputConnection does not
            // support monitoring CursorAnchorInfo
            candidatesView?.updateCursorAnchor(contentSize)
        }
    }

    private fun updateCursorAnchorSubscription(enable: Boolean): Boolean {
        if (!editorActive) return false
        val connection = currentInputConnection ?: return false
        return cursorAnchorSubscription.update(enable) { connection.monitorCursorAnchor(it) }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        Timber.d("onFinishInputView: finishingInput=$finishingInput")
        decorLocationUpdated = false
        inputView?.dismissCandidateActionMenu()
        inputView?.finishInput()
        candidatesView?.dismissCandidateActionMenu()
        inputDeviceManager.onFinishInputView()
        // A hidden keyboard retains unfinished spelling, but not a next-word chain.
        postRimeJob { clearPredictionContext() }
        // Hiding the window is not leaving the editor. Keep its composition so
        // showing it again cannot silently submit raw T9 digits or lose a lock.
        if (finishingInput) {
            composingText = ""
            composingCursor = null
            composingStart = null
            selectionAfterCancel = null
            postRimeJob { clearComposition() }
        }
        InputFeedbackManager.finishInput()
    }

    fun commitText(text: String, snapshot: CandidateSnapshot = CandidateSnapshot.EMPTY) {
        if (!snapshot.isValid && rime.run { isReady } &&
            (prefs.candidates.nextWordPrediction.getValue() || rime.run { candidateSnapshotCached.isPrediction })
        ) {
            // Clipboard, symbols and literal theme actions do not establish a Rime word context.
            // The fallback keyboard must not enqueue one recovery waiter for every literal key.
            postRimeJob { clearPredictionContext() }
        }
        commitText(text, snapshot, updateCommittedContent = true)
    }

    /** Literal macro caller already cleared prediction at its own position in the native FIFO. */
    internal fun commitTextWithoutPredictionReset(text: String, snapshot: CandidateSnapshot) {
        commitText(text, snapshot, updateCommittedContent = true)
    }

    private fun commitText(text: String, snapshot: CandidateSnapshot, updateCommittedContent: Boolean) {
        val ic = currentInputConnection ?: return
        val directRequest = if (snapshot.requestId == 0L && scopedFeedbackNanos > 0) {
            InputLatencyLog.request(editorGeneration.token, scopedFeedbackNanos)
        } else {
            null
        }

        // Finishing in place preserves the caret, so it is only safe at the end.
        val committed = if (composingText.isNotEmpty() && composingText == text &&
            composingCursor == text.length && composingSelectionMatches
        ) {
            ic.finishComposingText()
        } else {
            ic.commitText(text, 1)
        }
        if (committed) {
            if (snapshot.requestId != 0L) {
                InputLatencyLog.editorCommit(snapshot)
            } else {
                directRequest?.let { InputLatencyLog.editorCommit(CandidateSnapshot(it.editor, 0, it.id)) }
            }
        } else {
            InputLatencyLog.finish(directRequest, failed = true)
            InputLatencyLog.failedCommit(snapshot)
        }
        val contentAllowed = privacyMode.allowContentAccess && !PrivacyModeState.active
        if (!contentAllowed) {
            lastCommittedText = ""
        } else if (updateCommittedContent) {
            lastCommittedText = text
        }
        composingText = ""
        composingCursor = null
        composingStart = null
        selectionAfterCancel = null
        if (contentAllowed && updateCommittedContent) InputFeedbackManager.textCommitSpeak(text)
    }

    /**
     * Constructs a meta state integer flag which can be used for setting the `metaState` field when sending a KeyEvent
     * to the input connection. If this method is called without a meta modifier set to true, the default value `0` is
     * returned.
     *
     * @param ctrl Set to true to enable the CTRL meta modifier. Defaults to false.
     * @param alt Set to true to enable the ALT meta modifier. Defaults to false.
     * @param shift Set to true to enable the SHIFT meta modifier. Defaults to false.
     *
     * @return An integer containing all meta flags passed and formatted for use in a [KeyEvent].
     */
    fun meta(
        alt: Boolean = false,
        ctrl: Boolean = false,
        shift: Boolean = false,
        meta: Boolean = false,
        sym: Boolean = false,
    ): Int {
        var metaState = 0
        if (alt) metaState = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (ctrl) metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (shift) metaState = metaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (meta) metaState = metaState or KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        if (sym) metaState = metaState or KeyEvent.META_SYM_ON
        return metaState
    }

    private fun sendDownKeyEvent(
        eventTime: Long,
        keyEventCode: Int,
        metaState: Int = 0,
    ): Boolean {
        val ic = currentInputConnection ?: return false
        return ic.sendKeyEvent(
            KeyEvent(
                eventTime,
                eventTime,
                KeyEvent.ACTION_DOWN,
                keyEventCode,
                0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
                InputDevice.SOURCE_KEYBOARD,
            ),
        )
    }

    private fun sendUpKeyEvent(
        eventTime: Long,
        keyEventCode: Int,
        metaState: Int = 0,
    ): Boolean {
        val ic = currentInputConnection ?: return false
        return ic.sendKeyEvent(
            KeyEvent(
                eventTime,
                SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP,
                keyEventCode,
                0,
                metaState,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
                InputDevice.SOURCE_KEYBOARD,
            ),
        )
    }

    /**
     * Same as [InputMethodService.sendDownUpKeyEvents] but also allows to set meta state.
     *
     * @param keyEventCode The key code to send, use a key code defined in Android's [KeyEvent].
     * @param metaState Flags indicating which meta keys are currently pressed.
     *
     * @return True on success, false if an error occurred or the input connection is invalid.
     */
    fun sendDownUpKeyEvent(
        keyEventCode: Int,
        metaState: Int = meta(),
    ): Boolean {
        val eventTime = SystemClock.uptimeMillis()
        if (metaState and KeyEvent.META_ALT_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_ALT_LEFT)
        }
        if (metaState and KeyEvent.META_CTRL_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_CTRL_LEFT)
        }
        if (metaState and KeyEvent.META_SHIFT_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT)
        }
        if (metaState and KeyEvent.META_META_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_META_LEFT)
        }
        if (metaState and KeyEvent.META_SYM_ON != 0) {
            sendDownKeyEvent(eventTime, KeyEvent.KEYCODE_SYM)
        }
        sendDownKeyEvent(eventTime, keyEventCode, metaState)
        sendUpKeyEvent(eventTime, keyEventCode, metaState)
        if (metaState and KeyEvent.META_SYM_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_SYM)
        }
        if (metaState and KeyEvent.META_META_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_META_LEFT)
        }
        if (metaState and KeyEvent.META_SHIFT_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_SHIFT_LEFT)
        }
        if (metaState and KeyEvent.META_CTRL_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_CTRL_LEFT)
        }
        if (metaState and KeyEvent.META_ALT_ON != 0) {
            sendUpKeyEvent(eventTime, KeyEvent.KEYCODE_ALT_LEFT)
        }
        return true
    }

    private fun forwardKeyEvent(event: KeyEvent): Boolean {
        if (!themeReady || !editorActive) return false
        val info = currentInputEditorInfo ?: return false
        // A physical keyboard can start an editor without creating/showing InputView.
        // Restricted editors must receive its keys directly, independently of the
        // soft keyboard's temporary ASCII mode and layout lifecycle.
        if (EditorPolicy.keyboard(info.inputType, info.imeOptions) != EditorKeyboard.USER) return false
        val keyVal = KeyValue.fromKeyEvent(event)
        if (keyVal.value != RimeKeyMapping.RimeKey_VoidSymbol) {
            val modifiers = KeyModifiers.fromKeyEvent(event)
            postRimeJob {
                processKey(keyVal, modifiers, isVirtual = false)
            }
            return true
        }
        Timber.d("Skipped KeyEvent")
        return false
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (inputDeviceManager.evaluateOnKeyDown(event, this)) {
            decorLocationUpdated = false
            forceShowSelf()
        }
        return forwardKeyEvent(event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean = forwardKeyEvent(event) || super.onKeyUp(keyCode, event)

    // Added in API level 14, deprecated in 29
    // it's needed because editors still use it even on API 36
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onViewClicked(focusChanged: Boolean) {
        super.onViewClicked(focusChanged)
        val wasVirtual = inputDeviceManager.useVirtualKeyboard
        inputDeviceManager.evaluateOnViewClicked(this)
        restartInputViewAfterDeviceTransition(wasVirtual)
    }

    @RequiresApi(34)
    override fun onUpdateEditorToolType(toolType: Int) {
        super.onUpdateEditorToolType(toolType)
        val wasVirtual = inputDeviceManager.useVirtualKeyboard
        inputDeviceManager.evaluateOnUpdateEditorToolType(toolType, this)
        restartInputViewAfterDeviceTransition(wasVirtual)
    }

    private fun restartInputViewAfterDeviceTransition(wasVirtual: Boolean) {
        if (wasVirtual || !inputDeviceManager.useVirtualKeyboard || !themeReady || !editorActive) return
        currentInputEditorInfo?.let { inputView?.resumeInputAfterHardware(it) }
    }

    fun switchToPrevIme() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                switchToPreviousInputMethod()
            } else {
                @Suppress("DEPRECATION")
                inputMethodManager.switchToLastInputMethod(window.window!!.attributes.token)
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to switch to the previous IME.")
            inputMethodManager.showInputMethodPicker()
        }
    }

    fun switchToNextIme() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                switchToNextInputMethod(false)
            } else {
                @Suppress("DEPRECATION")
                inputMethodManager.switchToNextInputMethod(window.window!!.attributes.token, false)
            }
        } catch (e: Exception) {
            Timber.e(e, "Unable to switch to the next IME.")
            inputMethodManager.showInputMethodPicker()
        }
    }

    fun shareText(): Boolean {
        if (!canReadEditorContent()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val ic = currentInputConnection ?: return false
            val cs = ic.getSelectedText(0)
            if (cs == null) ic.performContextMenuAction(android.R.id.selectAll)
            return ic.performContextMenuAction(android.R.id.shareText)
        }
        return false
    }

    /** 編輯操作 */
    fun hookKeyboard(
        code: Int,
        mask: Int,
    ): Boolean {
        val ic = currentInputConnection ?: return false
        // 没按下 Ctrl 键
        if (mask != KeyEvent.META_CTRL_ON) {
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (prefs.keyboard.hookCtrlZY.getValue()) {
                when (code) {
                    KeyEvent.KEYCODE_Y -> return ic.performContextMenuAction(android.R.id.redo)
                    KeyEvent.KEYCODE_Z -> return ic.performContextMenuAction(android.R.id.undo)
                }
            }
        }

        when (code) {
            KeyEvent.KEYCODE_A -> {
                // 全选
                return if (prefs.keyboard.hookCtrlA.getValue()) {
                    ic.performContextMenuAction(android.R.id.selectAll)
                } else {
                    false
                }
            }

            KeyEvent.KEYCODE_X -> {
                // 剪切
                if (prefs.keyboard.hookCtrlCV.getValue()) {
                    val etr = ExtractedTextRequest()
                    etr.token = 0
                    val et = ic.getExtractedText(etr, 0)
                    if (et != null) {
                        if (et.selectionStart != et.selectionEnd) return ic.performContextMenuAction(android.R.id.cut)
                    }
                }
                Timber.w("hookKeyboard cut fail")
                return false
            }

            KeyEvent.KEYCODE_C -> {
                // 复制
                if (prefs.keyboard.hookCtrlCV.getValue()) {
                    val etr = ExtractedTextRequest()
                    etr.token = 0
                    val et = ic.getExtractedText(etr, 0)
                    if (et != null) {
                        if (et.selectionStart != et.selectionEnd) {
                            ic.performContextMenuAction(android.R.id.copy).also { result ->
                                if (result) {
                                    clearTextSelection()
                                }
                                return result
                            }
                        }
                    }
                }
                Timber.w("hookKeyboard copy fail")
                return false
            }

            KeyEvent.KEYCODE_V -> {
                // 粘贴
                if (prefs.keyboard.hookCtrlCV.getValue()) {
                    val etr = ExtractedTextRequest()
                    etr.token = 0
                    val et = ic.getExtractedText(etr, 0)
                    if (et == null) {
                        Timber.d("hookKeyboard paste, et == null, try commitText")
                        val clipboardText = clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(this)
                        if (ic.commitText(clipboardText, 1)) {
                            return true
                        }
                    } else if (ic.performContextMenuAction(android.R.id.paste)) {
                        return true
                    }
                    Timber.w("hookKeyboard paste fail")
                }
                return false
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (prefs.keyboard.hookCtrlLR.getValue()) {
                    val etr = ExtractedTextRequest()
                    etr.token = 0
                    val et = ic.getExtractedText(etr, 0)
                    if (et != null) {
                        val moveTo = et.text.findSectionFrom(et.startOffset + et.selectionEnd)
                        ic.setSelection(moveTo, moveTo)
                        return true
                    }
                }
            }

            KeyEvent.KEYCODE_DPAD_LEFT ->
                if (prefs.keyboard.hookCtrlLR.getValue()) {
                    val etr = ExtractedTextRequest()
                    etr.token = 0
                    val et = ic.getExtractedText(etr, 0)
                    if (et != null) {
                        val moveTo = et.text.findSectionFrom(et.startOffset + et.selectionStart, true)
                        ic.setSelection(moveTo, moveTo)
                        return true
                    }
                }
        }
        return false
    }

    fun clearTextSelection() {
        val ic = currentInputConnection ?: return
        val etr = ExtractedTextRequest().apply { token = 0 }
        val et = currentInputConnection.getExtractedText(etr, 0)
        et?.let {
            if (it.selectionStart != it.selectionEnd) {
                ic.setSelection(it.selectionEnd, it.selectionEnd)
            }
        }
    }

    internal fun updateComposingText(text: String, cursor: Int? = null) {
        val ic = currentInputConnection ?: return
        val position = cursor?.coerceIn(0, text.length)
        if (text == composingText && position == composingCursor && (position == null || composingSelectionMatches)) return
        cursorUpdateIndex += 1
        if (composingText.isEmpty()) {
            composingStart = ic.getExtractedText(ExtractedTextRequest(), 0)?.let {
                it.startOffset + minOf(it.selectionStart, it.selectionEnd)
            }
        }
        composingCursor = position
        composingSelectionMatches = true
        if (text.isNotEmpty()) selectionAfterCancel = null
        ic.beginBatchEdit()
        if (composingText.isNotEmpty() || text.isNotEmpty()) {
            if (text.isEmpty()) {
                // Replace the composing range without attaching spans to empty text.
                ic.commitText("", 1)
                ic.finishComposingText()
                composingStart = null
                selectionAfterCancel?.let { ic.setSelection(it.first, it.second) }
                selectionAfterCancel = null
            } else {
                ic.setComposingText(text, 1)
                if (position != null) {
                    composingStart?.let { start -> ic.setSelection(start + position, start + position) }
                }
            }
        }
        composingText = text
        ic.endBatchEdit()
    }

    fun getActiveText(type: Int): String {
        if (!canReadEditorContent()) return ""
        val rimeComposition = rime.run { compositionForEditor(compositionCached, candidateSnapshotCached.isPrediction) }
        val selected = currentInputConnection?.getSelectedText(0)?.toString()
        val commitPreview = rimeComposition.commitTextPreview
        val preedit = rimeComposition.preedit ?: ""
        val beforeCursor = getTextAroundCursor(1024, before = true) ?: ""
        val afterCursor = getTextAroundCursor(before = false) ?: ""
        val lastCommitted = lastCommittedText

        return sequenceOf(
            when (type) {
                2 -> preedit
                3 -> selected
                4 -> beforeCursor
                1 -> lastCommitted
                else -> null
            },
            commitPreview,
            selected,
            lastCommitted,
            beforeCursor,
            afterCursor,
        )
            .firstOrNull { it?.isNotEmpty() == true } ?: ""
    }

    private fun canReadEditorContent(): Boolean = canAccessEditorContent(
        editorActive = editorActive,
        policyAllowsContent = privacyMode.allowContentAccess,
        processPrivacyActive = PrivacyModeState.active,
        currentEditorToken = editorGeneration.token,
        nativeBoundEditorToken = nativeBoundEditorToken,
    )

    private fun getTextAroundCursor(
        initialStep: Int = 1024,
        before: Boolean,
    ): String? {
        val ic = currentInputConnection ?: return null
        var step = initialStep
        while (true) {
            val text = (if (before) ic.getTextBeforeCursor(step, 0) else ic.getTextAfterCursor(step, 0)) ?: return ""
            if (text.length < step) return text.toString()
            step *= 2
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    private var showingDialog: Dialog? = null

    fun showDialog(dialog: Dialog) {
        showingDialog?.dismiss()
        dialog.window?.also {
            it.attributes.apply {
                token = decorView.windowToken
                type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            }
            it.addFlags(
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            )
            it.setDimAmount(styledFloat(android.R.attr.backgroundDimAmount))
        }
        dialog.setOnDismissListener {
            showingDialog = null
        }
        dialog.show()
        showingDialog = dialog
    }
}

/**
 * A virtual Return carrying shift, control, alt, or meta is a key chord and is
 * forwarded with the modifier keys. Caps Lock or Num Lock alone is not.
 */
internal fun KeyModifiers.isReturnChord(): Boolean = shift || ctrl || alt || meta
