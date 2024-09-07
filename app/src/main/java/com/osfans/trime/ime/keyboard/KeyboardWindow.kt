// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.content.Context
import android.content.res.Configuration
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeNotification.OptionNotification
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ime.window.ResidentWindow
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

    val mainKeyboardView by lazy { KeyboardView(context) }

    private lateinit var keyboardView: FrameLayout

    companion object : ResidentWindow.Key

    override val key: ResidentWindow.Key
        get() = KeyboardWindow

    private val presetKeyboardIds = theme.presetKeyboards?.keys?.toTypedArray() ?: emptyArray()
    private val keyboardsCached: HashMap<String, Keyboard> by lazy {
        hashMapOf(
            "default" to Keyboard("default"),
            "number" to Keyboard("number"),
        )
    }
    private var currentKeyboardId = ""
    private var lastKeyboardId = ""
    private var lastLockKeyboardId = ""
    private val currentKeyboard: Keyboard? get() = keyboardsCached[currentKeyboardId]

    override fun onCreateView(): View {
        keyboardView = context.frameLayout()
        keyboardView.apply { add(mainKeyboardView, lParams(matchParent, matchParent)) }
        attachKeyboard(evalKeyboard(".default"))
        return keyboardView
    }

    private fun attachKeyboard(target: String) {
        currentKeyboardId = target
        lastKeyboardId = target
        currentKeyboard?.let {
            if (it.isLock) lastLockKeyboardId = target
            dispatchCapsState(it::setShifted)
            if (Rime.isAsciiMode != it.currentAsciiMode) {
                Rime.setOption("ascii_mode", it.currentAsciiMode)
            }
            // TODO：为避免过量重构，这里暂时将 currentKeyboard 同步到 KeyboardSwitcher
            KeyboardSwitcher.currentKeyboard = it
            mainKeyboardView.keyboard = it
        }
    }

    private fun smartMatchKeyboard(): String {
        // 主题的布局中包含方案id，直接采用
        val currentSchema = rime.run { schemaItemCached }
        if (presetKeyboardIds.contains(currentSchema.id)) {
            return currentSchema.id
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
            if (keyboardsCached.containsKey(target)) {
                if (target == currentKeyboardId) return@execute
            } else {
                keyboardsCached[target] = Keyboard(target)
            }
            attachKeyboard(target)
            if (windowManager.isAttached(this)) {
                service.updateComposing()
            }
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
            if (tempAsciiMode) {
                if (!Rime.isAsciiMode) Rime.setOption("ascii_mode", true)
            } else if (theme.generalStyle.resetASCIIMode) {
                if (it.resetAsciiMode) {
                    if (Rime.isAsciiMode != it.asciiMode) Rime.setOption("ascii_mode", it.asciiMode)
                } else {
                    if (Rime.isAsciiMode) Rime.setOption("ascii_mode", false)
                }
            }
        }
    }

    private fun dispatchCapsState(setShift: (Boolean, Boolean) -> Unit) {
        if (theme.generalStyle.autoCaps.toBoolean() && Rime.isAsciiMode && !mainKeyboardView.isCapsOn) {
            setShift(false, cursorCapsMode != 0)
        }
    }

    override fun onSelectionUpdate(
        start: Int,
        end: Int,
    ) {
        dispatchCapsState(mainKeyboardView::setShifted)
    }

    override fun onRimeOptionUpdated(value: OptionNotification.Value) {
        when (val opt = value.option) {
            "ascii_mode" -> currentKeyboard?.currentAsciiMode = value.value
            "_hide_key_hint" -> mainKeyboardView.showKeyHint = !value.value
            "_hide_key_symbol" -> mainKeyboardView.showKeySymbol = !value.value
            else -> {
                if (opt.matches("^_keyboard_.+".toRegex())) {
                    switchKeyboard(opt.removePrefix("_keyboard_"))
                    return
                }
            }
        }
        mainKeyboardView.invalidateAllKeys()
    }

    override fun onEnterKeyLabelUpdate(label: String) {
        mainKeyboardView.onEnterKeyLabelUpdate(label)
    }

    override fun onAttached() {
        mainKeyboardView.keyboardActionListener = ListenerDecorator(commonKeyboardActionListener.listener)
    }

    override fun onDetached() {
        mainKeyboardView.keyboardActionListener = null
    }

    inner class ListenerDecorator(
        private val delegate: KeyboardActionListener,
    ) : KeyboardActionListener by delegate {
        override fun onEvent(event: Event) {
            if (event.commit.isNotEmpty()) {
                // Directly commit the text and don't dispatch to Rime
                service.commitCharSequence(event.commit, true)
                return
            }

            if (event.getText(currentKeyboard).isNotEmpty()) {
                onText(event.getText(currentKeyboard))
                return
            }
            if (event.code == KeyEvent.KEYCODE_EISU) { // Switch keyboard
                switchKeyboard(event.select)
            } else {
                delegate.onEvent(event)
            }
        }
    }
}
