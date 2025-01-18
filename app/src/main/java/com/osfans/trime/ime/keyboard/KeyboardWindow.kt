// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.content.Context
import android.content.res.Configuration
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.SchemaItem
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.ime.preview.KeyPreviewChoreographer
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ime.window.ResidentWindow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import me.tatarka.inject.annotations.Inject
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import timber.log.Timber

@InputScope
@Inject
class KeyboardWindow(
    private val context: Context,
    private val service: TrimeInputMethodService,
    private val theme: Theme,
    private val rime: RimeSession,
    private val commonKeyboardActionListener: CommonKeyboardActionListener,
    private val windowManager: BoardWindowManager,
    private val keyPreviewChoreographer: KeyPreviewChoreographer,
) : BoardWindow.NoBarBoardWindow(),
    ResidentWindow,
    InputBroadcastReceiver {
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

    override val key: ResidentWindow.Key
        get() = KeyboardWindow

    private val presetKeyboardIds = theme.presetKeyboards?.keys?.toTypedArray() ?: emptyArray()
    private var currentKeyboardId = ""
    private var lastKeyboardId = ""
    private var lastLockKeyboardId = ""
    private val cachedKeyboards = mutableMapOf<String, Pair<Keyboard, KeyboardView>>()
    private val currentKeyboard: Keyboard? get() = cachedKeyboards[currentKeyboardId]?.first
    private val currentKeyboardView: KeyboardView? get() = cachedKeyboards[currentKeyboardId]?.second

    private val keyboardActionListener = ListenerDecorator(commonKeyboardActionListener.listener)

    override fun onCreateView(): View {
        keyboardView = context.frameLayout(R.id.keyboard_view)
        attachKeyboard(evalKeyboard(rime.run { statusCached }.schemaId))
        return keyboardView
    }

    private fun detachCurrentView() {
        currentKeyboardView?.also {
            it.onDetach()
            keyboardView.removeView(it)
            it.keyboardActionListener = null
        }
    }

    private fun attachKeyboard(target: String) {
        currentKeyboardId = target
        lastKeyboardId = target
        val newKeyboard =
            (currentKeyboard ?: Keyboard(theme, target)).also {
                runBlocking {
                    _currentKeyboardHeight.emit(it.keyboardHeight)
                }
                if (it.isLock) lastLockKeyboardId = target
                dispatchCapsState(it::setShifted)
                val isAsciiMode = rime.run { statusCached }.isAsciiMode
                if (isAsciiMode != it.currentAsciiMode) {
                    service.postRimeJob { setRuntimeOption("ascii_mode", it.currentAsciiMode) }
                }
                // TODO：为避免过量重构，这里暂时将 currentKeyboard 同步到 KeyboardSwitcher
                KeyboardSwitcher.currentKeyboard = it
            }
        val newView =
            currentKeyboardView ?: KeyboardView(context, theme, newKeyboard, keyPreviewChoreographer).also {
                cachedKeyboards[target] = newKeyboard to it
            }
        newView.let {
            KeyboardSwitcher.currentKeyboardView = it
            it.keyboardActionListener = keyboardActionListener
            keyboardView.apply { add(it, lParams(matchParent, matchParent)) }
        }
    }

    private fun smartMatchKeyboard(): String {
        // 主题的布局中包含方案id，直接采用
        val currentSchema = rime.run { statusCached }.schemaId
        if (presetKeyboardIds.contains(currentSchema)) {
            return currentSchema
        }
        val alphabet = SchemaManager.activeSchema.alphabet ?: return "default"
        val layout =
            when {
                alphabet.all { it.isLetter() } -> "qwerty" // 包含 26 个字母
                alphabet.all { it.isLetter() || ",./;".any(it::equals) } -> "qwerty_" // 包含 26 个字母和,./;
                alphabet.all { it.isLetterOrDigit() } -> "qwerty0" // 包含 26 个字母和数字键
                else -> "default"
            }
        return if (presetKeyboardIds.contains(layout)) layout else "default"
    }

    private fun evalKeyboard(id: String): String {
        val currentIdx = presetKeyboardIds.indexOfFirst { currentKeyboardId == it }
        val dot =
            when (id) {
                ".default" -> smartMatchKeyboard()
                ".prior" -> presetKeyboardIds.getOrNull(currentIdx - 1) ?: currentKeyboardId
                ".next" -> presetKeyboardIds.getOrNull(currentIdx + 1) ?: currentKeyboardId
                ".last" -> lastKeyboardId
                ".last_lock" -> lastLockKeyboardId
                ".ascii" -> {
                    val ascii = currentKeyboard?.asciiKeyboard
                    if (!ascii.isNullOrEmpty() && presetKeyboardIds.contains(ascii)) ascii else currentKeyboardId
                }
                else -> {
                    id.ifEmpty {
                        if (currentKeyboard?.isLock == true) currentKeyboardId else lastLockKeyboardId
                    }
                }
            }
        var final = dot.ifEmpty { smartMatchKeyboard() }

        // 切换到 mini 键盘
        val deviceKeyboard = service.resources.configuration.keyboard
        val useMiniKeyboard = AppPrefs.defaultInstance().theme.useMiniKeyboard && deviceKeyboard != Configuration.KEYBOARD_NOKEYS
        if (useMiniKeyboard) {
            if (presetKeyboardIds.contains("mini")) final = "mini"
        }

        // 切换到横屏布局
        if (service.isLandscapeMode()) {
            val landscape =
                theme.presetKeyboards
                    ?.get(final)
                    ?.configMap
                    ?.getValue("landscape_keyboard")
                    ?.getString() ?: ""
            if (landscape.isNotEmpty() && presetKeyboardIds.contains(landscape)) final = landscape
        }
        return final
    }

    fun switchKeyboard(to: String) {
        val target = evalKeyboard(to)
        ContextCompat.getMainExecutor(service).execute {
            if (cachedKeyboards.containsKey(target)) {
                if (target == currentKeyboardId) return@execute
            }
            detachCurrentView()
            attachKeyboard(target)
        }
        Timber.d("Switched to keyboard: $target")
    }

    override fun onStartInput(info: EditorInfo) {
        var tempAsciiMode = false
        val targetKeyboard =
            when (info.imeOptions and EditorInfo.IME_FLAG_FORCE_ASCII) {
                EditorInfo.IME_FLAG_FORCE_ASCII -> {
                    tempAsciiMode = true
                    ".ascii"
                }
                else -> {
                    when (info.inputType and InputType.TYPE_MASK_CLASS) {
                        InputType.TYPE_CLASS_NUMBER,
                        InputType.TYPE_CLASS_PHONE,
                        InputType.TYPE_CLASS_DATETIME,
                        -> {
                            tempAsciiMode = true
                            "number"
                        }
                        InputType.TYPE_CLASS_TEXT -> {
                            when (info.inputType and InputType.TYPE_MASK_VARIATION) {
                                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                                -> {
                                    tempAsciiMode = true
                                    ".ascii"
                                }
                                else -> ""
                            }
                        }
                        else -> ""
                    }
                }
            }
        switchKeyboard(targetKeyboard)
        currentKeyboard?.let {
            val isAsciiMode = rime.run { statusCached }.isAsciiMode
            if (tempAsciiMode) {
                if (!isAsciiMode) {
                    service.postRimeJob { setRuntimeOption("ascii_mode", true) }
                }
            } else if (theme.generalStyle.resetASCIIMode) {
                if (it.resetAsciiMode) {
                    if (isAsciiMode != it.asciiMode) {
                        service.postRimeJob { setRuntimeOption("ascii_mode", it.asciiMode) }
                    }
                } else {
                    if (isAsciiMode) {
                        service.postRimeJob { setRuntimeOption("ascii_mode", false) }
                    }
                }
            }
        }
    }

    private fun dispatchCapsState(setShift: (Boolean, Boolean) -> Unit) {
        if (theme.generalStyle.autoCaps.toBoolean() && Rime.isAsciiMode && currentKeyboardView?.isCapsOn == false) {
            setShift(false, cursorCapsMode != 0)
        }
    }

    override fun onSelectionUpdate(
        start: Int,
        end: Int,
    ) {
        dispatchCapsState { on, shifted -> currentKeyboardView?.setShifted(on, shifted) }
    }

    override fun onRimeSchemaUpdated(schema: SchemaItem) {
        switchKeyboard(schema.id)
    }

    override fun onRimeOptionUpdated(value: RimeMessage.OptionMessage.Data) {
        when (val opt = value.option) {
            "ascii_mode" -> currentKeyboard?.currentAsciiMode = value.value
            "_hide_key_hint" -> currentKeyboardView?.showKeyHint = !value.value
            "_hide_key_symbol" -> currentKeyboardView?.showKeySymbol = !value.value
            else -> {
                when {
                    opt.startsWith("_keyboard_") -> {
                        val target = opt.removePrefix("_keyboard_")
                        if (target.isNotEmpty()) {
                            switchKeyboard(target)
                        }
                    }
                    opt.startsWith("_key_") -> {
                        val what = opt.removePrefix("_key_")
                        if (what.isNotEmpty() && value.value) {
                            commonKeyboardActionListener
                                .listener
                                .onAction(KeyActionManager.getAction(what))
                        }
                    }
                }
            }
        }
        currentKeyboardView?.invalidateAllKeys()
    }

    override fun onEnterKeyLabelUpdate(label: String) {
        currentKeyboardView?.onEnterKeyLabelUpdate(label)
    }

    override fun onAttached() {
        currentKeyboardView?.keyboardActionListener = keyboardActionListener
    }

    override fun onDetached() {
        currentKeyboardView?.let {
            it.onDetach()
            it.keyboardActionListener = null
        }
    }

    inner class ListenerDecorator(
        private val delegate: KeyboardActionListener,
    ) : KeyboardActionListener by delegate {
        override fun onAction(action: KeyAction) {
            if (action.commit.isNotEmpty()) {
                // Directly commit the text and don't dispatch to Rime
                service.commitText(action.commit, true)
                return
            }

            currentKeyboard?.let {
                if (action.getText(it).isNotEmpty()) {
                    onText(action.getText(it))
                    return
                }
            }

            delegate.onAction(action)
        }
    }
}
