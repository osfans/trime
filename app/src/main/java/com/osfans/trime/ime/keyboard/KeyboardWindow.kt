// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.graphics.Point
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.broadcast.EnterKeyDisplayDelegate
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.broadcast.InputBroadcaster
import com.osfans.trime.ime.core.EditorKeyboard
import com.osfans.trime.ime.core.EditorModeOverride
import com.osfans.trime.ime.core.EditorPolicy
import com.osfans.trime.ime.core.KeyboardModeCommand
import com.osfans.trime.ime.core.KeyboardModeEffect
import com.osfans.trime.ime.core.KeyboardModeResult
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.core.applyKeyboardMode
import com.osfans.trime.ime.core.canReuseMainLayout
import com.osfans.trime.ime.core.rememberedMainKeyboard
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.ime.performance.InputLatencyLog
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.ResidentWindow
import com.osfans.trime.util.isLandscape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.kodein.di.DI
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.systemservices.windowManager
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import timber.log.Timber

class KeyboardWindow(di: DI) :
    BoardWindow.NoBarBoardWindow(di),
    ResidentWindow,
    InputBroadcastReceiver {
    private val service: TrimeInputMethodService by instance()
    private val theme: Theme by instance()
    private val rime: RimeSession by instance()
    private val commonKeyboardActionListener: CommonKeyboardActionListener by instance()
    private val popup: PopupDelegate by instance()
    private val enterKeyDisplay: EnterKeyDisplayDelegate by instance()
    private val broadcaster: InputBroadcaster by instance()

    private val cursorCapsMode: Int
        get() =
            service.currentInputEditorInfo.run {
                if (inputType != InputType.TYPE_NULL) {
                    service.currentInputConnection?.getCursorCapsMode(inputType) ?: 0
                } else {
                    0
                }
            }

    private val _currentKeyboardHeight =
        MutableSharedFlow<Int>(
            replay = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val currentKeyboardHeight = _currentKeyboardHeight.asSharedFlow()

    private lateinit var keyboardView: FrameLayout

    companion object : ResidentWindow.Key

    lateinit var currentKeyboard: Keyboard
        private set

    override val key: ResidentWindow.Key
        get() = KeyboardWindow

    private val presetKeyboardIds = theme.presetKeyboards.keys.toList()
    private val cycleKeyboardIds = presetKeyboardIds.filterNot { it in theme.fallbackKeyboards.values }
    private var currentKeyboardId = ""
    private var lastKeyboardId = ""
    private var lastLockKeyboardId = ""
    private var restrictedEditor = false
    private var desiredAsciiMode: Boolean? = null
    private var preserveNativeModeOnNextStart = false
    private var auxiliaryReturn: EditorModeOverride?
        get() = service.auxiliaryKeyboardMode
        set(value) {
            service.auxiliaryKeyboardMode = value
        }
    private val keyboardCacheId = InputLatencyLog.nextCacheId()
    private val cachedKeyboards = mutableMapOf<String, Pair<Keyboard, KeyboardView>>()
    private val activeKeyboard: Keyboard? get() = cachedKeyboards[currentKeyboardId]?.first
    private val currentKeyboardView: KeyboardView? get() = cachedKeyboards[currentKeyboardId]?.second

    private val keyboardActionListener = commonKeyboardActionListener.listener

    private var lastIsPortrait: Boolean? = null
    private var containerWidth: Int = 0
    private var allowedWidth: Int = 0
    private var t9HeightBudgetPx = 0
    private var heightRefreshPending = false
    val usesT9CompositionPanel: Boolean
        get() {
            if (!::currentKeyboard.isInitialized) return false
            val origin = auxiliaryReturn
            return T9LayoutPolicy.usesCompositionPanel(
                currentKeyboard.isT9Layout,
                currentKeyboardId == evalKeyboard("symbols"),
                restrictedEditor,
                origin?.let { selectKeyboardConfig(it.keyboardId)?.t9Layout } == true,
                origin?.schemaId == rime.run { statusCached.schemaId },
            )
        }
    val usesScrollableT9Fallback get() = usesT9CompositionPanel && t9HeightBudgetPx > 0 &&
        currentKeyboard.keyboardHeight > t9HeightBudgetPx
    val hasPendingInputGesture get() = ::currentKeyboard.isInitialized &&
        (
            currentKeyboard.firstPressedKeyIndex != -1 || currentKeyboard.keys.any { it.isPressed } ||
                currentKeyboardView?.children?.any { (it as? GestureFrame)?.hasPendingTouchGesture == true } == true
            )

    fun setT9HeightBudget(maximum: Int) {
        if (t9HeightBudgetPx == maximum) return
        t9HeightBudgetPx = maximum
        if (!::currentKeyboard.isInitialized || !usesT9CompositionPanel || !::keyboardView.isInitialized) return
        // Auxiliary pages keep their existing theme model; one parent viewport scrolls
        // all controls and keys when that complete model cannot fit.
        val modelHeight = if (currentKeyboard.isT9Layout) {
            T9LayoutPolicy.modelHeight(currentKeyboard.requestedKeyboardHeight, maximum, context.dp(192))
        } else {
            currentKeyboard.requestedKeyboardHeight
        }
        if (currentKeyboard.keyboardHeight == modelHeight) return
        requestKeyboardRefresh()
    }

    private fun requestKeyboardRefresh() {
        if (heightRefreshPending) return
        heightRefreshPending = true
        val refresh = object : Runnable {
            override fun run() {
                if (!keyboardView.isAttachedToWindow) {
                    heightRefreshPending = false
                    return
                }
                if (hasPendingInputGesture) {
                    keyboardView.postOnAnimation(this)
                    return
                }
                heightRefreshPending = false
                refreshKeyboards(isAll = true)
            }
        }
        keyboardView.postOnAnimation(refresh)
    }

    private val onKeyboardViewLayoutChangeListener =
        View.OnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val width = right - left
            if (width > 0 && allowedWidth != width) {
                val isPortrait = !context.resources.configuration.isLandscape()
                lastIsPortrait = isPortrait
                containerWidth = width
                allowedWidth = width
                requestKeyboardRefresh()
            }
        }

    override fun onCreateView(): View {
        keyboardView = context.frameLayout(R.id.keyboard_view)
        keyboardView.addOnLayoutChangeListener(onKeyboardViewLayoutChangeListener)
        attachKeyboard(evalKeyboard(".default"), updateMode = false)
        return keyboardView
    }

    private fun detachCurrentView() {
        currentKeyboardView?.also {
            it.onDetach()
            keyboardView.removeAllViews()
        }
        activeKeyboard?.takeUnless { restrictedEditor }?.let { keyboard ->
            if (!isAuxiliaryKeyboard(currentKeyboardId)) {
                keyboard.lastAsciiMode = plannedAsciiMode()
            }
        }
    }

    /** 计算键盘可用宽度：优先使用已测量的容器宽度，否则回退到系统窗口测量。 */
    private fun computeAllowedWidth(): Int {
        val isPortrait = !context.resources.configuration.isLandscape()

        if (containerWidth > 0 && lastIsPortrait == isPortrait) {
            return containerWidth
        }

        val padding = theme.style.run {
            if (context.isLandscapeMode()) keyboardPaddingLand else keyboardPadding
        }

        val safeWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = context.windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            val displayWidth = context.resources.displayMetrics.widthPixels
            val windowWidth = windowMetrics.bounds.width() - insets.left - insets.right
            minOf(displayWidth, windowWidth).coerceAtLeast(1)
        } else {
            @Suppress("DEPRECATION")
            val size = Point()
            @Suppress("DEPRECATION")
            context.windowManager.defaultDisplay.getSize(size)
            size.x
        }

        val width = (safeWidth - 2 * context.dp(padding)).coerceAtLeast(1)
        allowedWidth = width
        return width
    }

    private fun selectKeyboardConfig(name: String): TextKeyboard? = try {
        resolveKeyboardConfig(name, theme.presetKeyboards)
    } catch (e: IllegalArgumentException) {
        Timber.e(e, "Invalid keyboard import")
        theme.presetKeyboards["default"]?.takeIf { it.importPreset.isEmpty() }
    }

    private fun attachKeyboard(target: String, updateMode: Boolean = true) {
        val layoutChanged = currentKeyboardId != target
        currentKeyboardId = target
        if (!restrictedEditor && !isAuxiliaryKeyboard(target)) lastKeyboardId = target

        val config = selectKeyboardConfig(target)
        activeKeyboard?.takeIf { it.isT9Layout }?.let { cached ->
            val expected = T9LayoutPolicy.modelHeight(cached.requestedKeyboardHeight, t9HeightBudgetPx, context.dp(192))
            if (cached.keyboardHeight != expected) cachedKeyboards.remove(target)
        }
        val keyboard = activeKeyboard ?: Keyboard(context, theme, computeAllowedWidth(), config, t9HeightBudgetPx)
        val view = currentKeyboardView ?: KeyboardView(context, theme, keyboard, popup, service, keyboardActionListener, enterKeyDisplay)

        if (activeKeyboard == null) {
            cachedKeyboards[target] = keyboard to view
            recordKeyboardCache()
            keyboard.lastAsciiMode = keyboard.asciiMode
        }

        keyboard.also {
            currentKeyboard = it
            runBlocking { _currentKeyboardHeight.emit(it.keyboardHeight) }
            if (it.isLock && !restrictedEditor && !isAuxiliaryKeyboard(target)) lastLockKeyboardId = target
            dispatchCapsState(it::setShifted)

            val targetMode = when {
                restrictedEditor -> true

                !isAuxiliaryKeyboard(target) && service.keyboardLanguage.remembered.asciiMode != null ->
                    service.keyboardLanguage.remembered.asciiMode!!

                it.resetAsciiMode -> it.asciiMode

                else -> it.lastAsciiMode
            }
            if (updateMode) {
                val effect = if (target == evalKeyboard("symbols")) {
                    KeyboardModeEffect.PRESERVE_COMPOSITION
                } else {
                    KeyboardModeEffect.COMMIT_ON_CHANGE
                }
                service.acceptKeyboardMode(targetMode, rememberLanguage = false, effect)?.let { command ->
                    desiredAsciiMode = command.asciiMode
                    enqueueMode(command)
                }
            }
        }

        view.let {
            it.updateEnterLabel()
            keyboardView.apply {
                (it.parent as? android.view.ViewGroup)?.removeView(it)
                add(it, lParams(matchParent, matchParent))
            }
        }
        if (layoutChanged) broadcaster.onKeyboardLayoutChanged()
    }

    private fun smartMatchKeyboard(): String {
        val statusSchema = rime.run { statusCached }.schemaId
        val schema = rime.run { schemaCached }
        val schemaId = statusSchema.ifEmpty { schema.schemaId }
        return matchKeyboard(schemaId, schema.keyboard, schema.isPinyinT9, schema.alphabet, theme.presetKeyboards, theme.fallbackKeyboards)
    }

    private fun evalKeyboard(id: String): String {
        val currentIdx = cycleKeyboardIds.indexOfFirst { currentKeyboardId == it }
        val dot =
            when (id) {
                ".default", ".native" -> smartMatchKeyboard()

                ".prior" -> cycleKeyboardIds.getOrNull(currentIdx - 1) ?: currentKeyboardId

                ".next" -> cycleKeyboardIds.getOrNull(currentIdx + 1) ?: currentKeyboardId

                ".last" -> lastKeyboardId

                ".last_lock" -> lastLockKeyboardId

                ".ascii" -> resolveAsciiKeyboard(
                    selectKeyboardConfig(evalKeyboard(".native"))?.asciiKeyboard.orEmpty(),
                    theme.presetKeyboards,
                    theme.fallbackKeyboards,
                )

                else -> {
                    id.ifEmpty {
                        if (activeKeyboard?.isLock == true) currentKeyboardId else lastLockKeyboardId
                    }
                }
            }
        var final =
            normalizeKeyboardTarget(
                dot.ifEmpty { smartMatchKeyboard() },
                theme.presetKeyboards,
                theme.fallbackKeyboards,
            )

        // 切换到横屏布局
        if (service.isLandscapeMode()) {
            final =
                resolveLandscapeKeyboard(
                    final,
                    theme.presetKeyboards,
                    theme.fallbackKeyboards,
                )
        }
        return final
    }

    fun switchKeyboard(to: String) {
        service.lifecycleScope.launch(Dispatchers.Main.immediate) {
            when (to) {
                ".ascii" -> {
                    switchLanguage(true)
                    return@launch
                }

                ".native" -> {
                    switchLanguage(false)
                    return@launch
                }

                ".return" -> {
                    returnToMainKeyboard()
                    return@launch
                }
            }
            val target = evalKeyboard(to)
            if (!restrictedEditor && isAuxiliaryKeyboard(target) && auxiliaryReturn == null) {
                auxiliaryReturn = EditorModeOverride(rime.run { statusCached.schemaId }, currentKeyboardId, plannedAsciiMode(), System.identityHashCode(theme))
            } else if (!isAuxiliaryKeyboard(target)) {
                auxiliaryReturn = null
            }
            if (cachedKeyboards.containsKey(target)) {
                if (target == currentKeyboardId) return@launch
            }
            detachCurrentView()
            attachKeyboard(target)
            Timber.d("Switched to keyboard: $target")
        }
    }

    private fun isAuxiliaryKeyboard(id: String): Boolean = id == evalKeyboard("number") || id == evalKeyboard("symbols")

    private fun plannedAsciiMode(): Boolean = service.keyboardLanguage.plannedAscii(
        service.editorToken,
        rime.run { statusCached.schemaId },
        desiredAsciiMode ?: rime.run { statusCached.isAsciiMode },
    )

    private fun languageKeyboard(ascii: Boolean): String = when (
        service.currentInputEditorInfo?.let { EditorPolicy.keyboard(it.inputType, it.imeOptions) }
    ) {
        EditorKeyboard.NUMBER -> evalKeyboard("number")
        EditorKeyboard.ASCII -> evalKeyboard(".ascii")
        else -> evalKeyboard(if (ascii) ".ascii" else ".native")
    }

    private fun restoreKeyboard(saved: EditorModeOverride, schema: String): String? {
        if (saved.schemaId != schema) return null
        return if (canReuseMainLayout(saved.schemaId, saved.themeIdentity, saved.keyboardId, schema, System.identityHashCode(theme), presetKeyboardIds)) {
            evalKeyboard(saved.keyboardId)
        } else {
            languageKeyboard(saved.asciiMode)
        }
    }

    /** Explicit user action: synchronously expose its layout, but execute every native intent in FIFO. */
    fun switchLanguage(
        targetAscii: Boolean? = null,
        afterMode: suspend RimeApi.(Boolean) -> Unit = {},
    ) {
        val command = service.acceptKeyboardMode(targetAscii, rememberLanguage = true, KeyboardModeEffect.COMMIT) ?: return
        auxiliaryReturn = null
        desiredAsciiMode = command.asciiMode
        detachCurrentView()
        attachKeyboard(languageKeyboard(command.asciiMode), updateMode = false)
        currentKeyboard.lastAsciiMode = command.asciiMode
        enqueueMode(command, afterMode)
    }

    private fun returnToMainKeyboard() {
        val saved = auxiliaryReturn
        auxiliaryReturn = null
        val schema = rime.run { statusCached.schemaId }
        val ascii = if (restrictedEditor) {
            true
        } else {
            saved?.takeIf { it.schemaId == schema }?.asciiMode
                ?: service.keyboardLanguage.remembered.asciiMode ?: plannedAsciiMode()
        }
        val target = saved?.takeUnless { restrictedEditor }?.let { restoreKeyboard(it, schema) } ?: languageKeyboard(ascii)
        val command = service.acceptKeyboardMode(ascii, rememberLanguage = false, KeyboardModeEffect.MODE_ONLY) ?: return
        desiredAsciiMode = command.asciiMode
        detachCurrentView()
        attachKeyboard(target, updateMode = false)
        enqueueMode(command)
    }

    private fun enqueueMode(
        command: KeyboardModeCommand,
        afterMode: suspend RimeApi.(Boolean) -> Unit = {},
    ): Job {
        var result: KeyboardModeResult? = null
        var completed = false
        val job = service.postRimeJob {
            try {
                result = applyKeyboardMode(
                    command,
                    currentEditor = { if (service.isCurrentEditor(command.editorToken)) service.editorToken else 0L },
                    currentSchema = { statusCached.schemaId },
                    composing = { statusCached.isComposing },
                    asciiMode = { statusCached.isAsciiMode },
                    commitComposition = { commitComposition() },
                    setAsciiMode = { setRuntimeOption("ascii_mode", it) },
                )
                if (result.applied) afterMode(result.asciiMode)
            } finally {
                val finalResult = result ?: KeyboardModeResult(false, statusCached.isAsciiMode)
                withContext(NonCancellable + Dispatchers.Main.immediate) {
                    completeMode(command, finalResult)
                    completed = true
                }
            }
        }
        job.invokeOnCompletion { failure ->
            // The returned queue completion can be cancelled before its real coroutine exits.
            // That coroutine's finally owns settlement; retired editor cleanup needs no repaint.
            if (!completed && (failure !is CancellationException || failure is com.osfans.trime.core.RimeUnavailableException)) {
                service.lifecycleScope.launch(Dispatchers.Main.immediate) {
                    completeMode(command, KeyboardModeResult(false, rime.run { statusCached.isAsciiMode }))
                }
            }
        }
        return job
    }

    private fun completeMode(command: KeyboardModeCommand, result: KeyboardModeResult) {
        if (!service.completeKeyboardMode(command, result)) return
        desiredAsciiMode = result.asciiMode
        if (!result.applied) {
            detachCurrentView()
            attachKeyboard(languageKeyboard(result.asciiMode), updateMode = false)
        }
        if (!result.asciiMode) activeKeyboard?.resetShift()
        currentKeyboardView?.invalidateAllKeys()
    }

    fun refreshKeyboards(isAll: Boolean = false) {
        val id = currentKeyboardId.ifEmpty { return }
        detachCurrentView()
        if (isAll) {
            cachedKeyboards.clear()
        } else {
            cachedKeyboards.remove(id)
        }
        recordKeyboardCache()
        attachKeyboard(id, updateMode = false)
    }

    /** Reports existing layout/key counts only when the cache changes. */
    private fun recordKeyboardCache() {
        if (!InputLatencyLog.enabled) return
        InputLatencyLog.keyboardCache(
            keyboardCacheId,
            cachedKeyboards.size,
            cachedKeyboards.values.sumOf { (keyboard, _) -> keyboard.keys.size },
        )
    }

    /** Repaints the keyboard after a color-scheme switch; keys re-resolve their colors. */
    override fun refreshColors() {
        currentKeyboardView?.invalidateAllKeys()
    }

    override fun onStartInput(info: EditorInfo) {
        applyEditorKeyboard(info, schemaChanged = false)
    }

    private fun applyEditorKeyboard(info: EditorInfo, schemaChanged: Boolean) {
        val policy = EditorPolicy.keyboard(info.inputType, info.imeOptions)
        val schema = rime.run { statusCached.schemaId }
        val saved = service.editorModeOverride
        val restoring = saved.takeIf { policy == EditorKeyboard.USER }
        val leavingRestrictedEditor = policy == EditorKeyboard.USER && restrictedEditor
        val preserveNativeMode = preserveNativeModeOnNextStart.also { preserveNativeModeOnNextStart = false }
        val editorToken = service.editorToken
        val ascii = plannedAsciiMode()
        if (policy != EditorKeyboard.USER && saved == null) {
            service.editorModeOverride = auxiliaryReturn?.takeIf { it.schemaId == schema }
                ?: EditorModeOverride(schema, currentKeyboardId, ascii, System.identityHashCode(theme))
        }
        val target = when (policy) {
            EditorKeyboard.ASCII -> evalKeyboard(".ascii")

            EditorKeyboard.NUMBER -> evalKeyboard("number")

            EditorKeyboard.USER -> rememberedMainKeyboard(
                service.keyboardLanguage.remembered,
                nativeKeyboard = evalKeyboard(".native"),
                asciiKeyboard = evalKeyboard(".ascii"),
                legacyKeyboard = saved?.let { restoreKeyboard(it, schema) }
                    ?: auxiliaryReturn?.let { restoreKeyboard(it, schema) }
                    ?: when {
                        schemaChanged -> evalKeyboard(".default")
                        saved != null -> evalKeyboard(".default")
                        leavingRestrictedEditor -> evalKeyboard(".last_lock")
                        else -> evalKeyboard("")
                    },
            )
        }
        val auxiliaryMode = auxiliaryReturn?.takeIf { it.schemaId == schema }?.asciiMode
        detachCurrentView()
        restrictedEditor = policy != EditorKeyboard.USER
        auxiliaryReturn = null
        attachKeyboard(target, updateMode = false)
        val targetMode = when {
            restrictedEditor -> true

            service.keyboardLanguage.remembered.asciiMode != null -> service.keyboardLanguage.remembered.asciiMode!!

            saved != null && saved.schemaId == schema -> saved.asciiMode

            saved != null -> currentKeyboard.asciiMode

            auxiliaryMode != null -> auxiliaryMode

            preserveNativeMode -> ascii

            theme.style.resetAsciiModeOnFocusChange ->
                if (currentKeyboard.resetAsciiMode) currentKeyboard.asciiMode else currentKeyboard.lastAsciiMode

            else -> ascii
        }
        desiredAsciiMode = targetMode
        if (!restrictedEditor) {
            currentKeyboard.lastAsciiMode = targetMode
        }
        enqueueEditorMode(targetMode, restoring, editorToken)
    }

    /** Replays only the native mode after a privacy boundary invalidates older queued jobs. */
    fun reapplyCurrentEditorMode() {
        val targetMode = desiredAsciiMode ?: rime.run { statusCached.isAsciiMode }
        val restoring = service.editorModeOverride.takeIf { !restrictedEditor }
        enqueueEditorMode(targetMode, restoring, service.editorToken)
    }

    /** Picks up native changes made while the hidden input view was not receiving messages. */
    fun syncModeFromNative() {
        desiredAsciiMode = service.keyboardLanguage.synchronizeNative(
            service.editorToken,
            rime.run { statusCached.schemaId },
            rime.run { statusCached.isAsciiMode },
        ) ?: desiredAsciiMode ?: rime.run { statusCached.isAsciiMode }
        preserveNativeModeOnNextStart = true
    }

    /** Restores a virtual restricted editor's snapshot without showing or changing a keyboard. */
    fun restoreModeForHardwareEditor(info: EditorInfo) {
        val restoring = service.editorModeOverride ?: return
        if (EditorPolicy.keyboard(info.inputType, info.imeOptions) != EditorKeyboard.USER) return
        val schema = rime.run { statusCached.schemaId }
        val targetMode =
            if (service.keyboardLanguage.remembered.asciiMode != null) {
                service.keyboardLanguage.remembered.asciiMode!!
            } else if (restoring.schemaId == schema) {
                restoring.asciiMode
            } else {
                selectKeyboardConfig(evalKeyboard(".default"))?.asciiMode == 1
            }
        desiredAsciiMode = targetMode
        enqueueEditorMode(targetMode, restoring, service.editorToken)
    }

    private fun enqueueEditorMode(
        targetMode: Boolean,
        restoring: EditorModeOverride?,
        editorToken: Long,
    ) {
        // Always enqueue the final policy: an earlier layout switch may still be in flight. Keep
        // a restricted editor's restore snapshot until the native mode has changed. A deployment
        // can rebuild this KeyboardWindow while the queue is busy; clearing the snapshot first
        // would let the replacement initialize from the restricted ASCII cache and overwrite the
        // pending restore.
        val command = service.acceptKeyboardMode(targetMode, rememberLanguage = false, KeyboardModeEffect.MODE_ONLY) ?: return
        enqueueMode(command) {
            if (restoring != null) {
                withContext(Dispatchers.Main.immediate) {
                    if (service.isCurrentEditor(editorToken) && service.editorModeOverride === restoring) {
                        service.editorModeOverride = null
                    }
                }
            }
        }
    }

    private fun dispatchCapsState(setShift: (Boolean, Boolean) -> Unit) {
        val status = rime.run { statusCached }
        // TODO: 启用自动首句大写后，点击方向键时，保持Shift锁定状态功能将无法生效
        if (theme.style.autoCaps == true && status.isAsciiMode && currentKeyboardView?.isCapsOn == false) {
            setShift(false, cursorCapsMode != 0)
        }
    }

    override fun onKeyAppearanceUpdate(composing: Boolean, menu: Boolean, paging: Boolean) {
        if (!rime.run { statusCached }.isAsciiMode) {
            activeKeyboard?.appearanceStateKeys?.forEach { key ->
                currentKeyboardView?.invalidateKeyByIndex(key.index)
            }
        }
    }

    override fun onSelectionUpdate(
        start: Int,
        end: Int,
    ) {
        dispatchCapsState { on, shifted ->
            activeKeyboard?.setShifted(on, shifted)?.let { if (it) currentKeyboardView?.invalidateAllKeys() }
        }
    }

    override fun onEnterKeyLabelUpdate(label: String) {
        currentKeyboardView?.updateEnterLabel()
    }

    override fun onRimeSchemaUpdated(schema: SchemaItem) {
        // A lock belongs to the previous schema. Otherwise a non-locking new
        // default (for example Tongwenfeng's full pinyin) reselects the old T9
        // layout on this or the next editor-focus update.
        lastLockKeyboardId = ""
        auxiliaryReturn = null
        service.currentInputEditorInfo?.let { applyEditorKeyboard(it, schemaChanged = true) }
    }

    override fun onRimeOptionUpdated(value: RimeMessage.OptionMessage.Data) {
        val option = value.option
        when {
            option == "ascii_mode" -> {
                service.keyboardLanguage.synchronizeNative(
                    service.editorToken,
                    rime.run { statusCached.schemaId },
                    rime.run { statusCached.isAsciiMode },
                )?.let {
                    desiredAsciiMode = it
                    if (!it) activeKeyboard?.resetShift()
                }
            }

            option.startsWith("_keyboard_") -> {
                val target = option.removePrefix("_keyboard_")
                if (target.isNotEmpty()) {
                    switchKeyboard(target)
                }
            }

            option.startsWith("_key_") -> {
                val what = option.removePrefix("_key_")
                if (what.isNotEmpty() && value.value) {
                    commonKeyboardActionListener
                        .listener
                        .onAction(theme.resolveAction(what))
                }
            }
        }
        currentKeyboardView?.invalidateAllKeys()
    }

    override fun onAttached() {
    }

    override fun onDetached() {
        currentKeyboardView?.onDetach()
    }

    fun finishInput() {
        currentKeyboardView?.onDetach()
    }

    /** Clear content-bearing gesture state from active and cached keyboards. */
    fun clearEditorBoundaryState() {
        cachedKeyboards.values.forEach { (_, view) -> view.clearTransientContent() }
    }
}
