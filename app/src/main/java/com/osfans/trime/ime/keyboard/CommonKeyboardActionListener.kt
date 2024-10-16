/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.app.Dialog
import android.content.Context
import android.view.KeyEvent
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeApi
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.EventManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.Speech
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.dialog.AvailableSchemaPickerDialog
import com.osfans.trime.ime.dialog.EnabledSchemaPickerDialog
import com.osfans.trime.ime.symbol.LiquidKeyboard
import com.osfans.trime.ime.symbol.SymbolBoardType
import com.osfans.trime.ime.symbol.TabManager
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ui.main.settings.ColorPickerDialog
import com.osfans.trime.ui.main.settings.KeySoundEffectPickerDialog
import com.osfans.trime.ui.main.settings.ThemePickerDialog
import com.osfans.trime.util.ShortcutUtils
import com.osfans.trime.util.isAsciiPrintable
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import splitties.systemservices.inputMethodManager

@InputScope
@Inject
class CommonKeyboardActionListener(
    private val context: Context,
    private val service: TrimeInputMethodService,
    private val rime: RimeSession,
    private val inputView: InputView,
    private val liquidKeyboard: LiquidKeyboard,
    private val windowManager: BoardWindowManager,
) {
    companion object {
        /** Pattern for braced key event like `{Left}`, `{Right}`, etc. */
        private val BRACED_KEY_EVENT = """^(\{[^{}]+\}).*$""".toRegex()

        /** Pattern for braced key event to capture `{Escape}` as group 2 */
        private val BRACED_KEY_EVENT_WITH_ESCAPE = """^((\{Escape\})?[^{}]+).*$""".toRegex()
    }

    private val prefs = AppPrefs.defaultInstance()

    var needSendUpRimeKey: Boolean = false

    private fun showDialog(dialog: suspend (RimeApi) -> Dialog) {
        rime.launchOnReady { api ->
            service.lifecycleScope.launch {
                inputView.showDialog(dialog(api))
            }
        }
    }

    private fun showThemePicker() {
        showDialog {
            ThemePickerDialog.build(service.lifecycleScope, context)
        }
    }

    private fun showColorPicker() {
        showDialog {
            ColorPickerDialog.build(service.lifecycleScope, context)
        }
    }

    private fun showSoundEffectPicker() {
        showDialog {
            KeySoundEffectPickerDialog.build(service.lifecycleScope, context)
        }
    }

    private fun showAvailableSchemaPicker() {
        showDialog { api ->
            AvailableSchemaPickerDialog.build(api, service.lifecycleScope, context)
        }
    }

    private fun showEnabledSchemaPicker() {
        showDialog { api ->
            EnabledSchemaPickerDialog.build(api, service.lifecycleScope, context) {
                setPositiveButton(R.string.enable_schemata) { _, _ ->
                    showAvailableSchemaPicker()
                }
                setNegativeButton(R.string.set_ime) { _, _ ->
                    ShortcutUtils.launchMainActivity(context)
                }
            }
        }
    }

    val listener by lazy {
        object : KeyboardActionListener {
            override fun onPress(keyEventCode: Int) {
                InputFeedbackManager.run {
                    keyPressVibrate(service.window.window!!.decorView)
                    keyPressSound(keyEventCode)
                    keyPressSpeak(keyEventCode)
                }
            }

            override fun onRelease(keyEventCode: Int) {
                if (needSendUpRimeKey) {
                    if (service.shouldUpdateRimeOption) {
                        Rime.setOption("soft_cursors", prefs.keyboard.softCursorEnabled)
                        Rime.setOption("_horizontal", ThemeManager.activeTheme.generalStyle.horizontal)
                        service.shouldUpdateRimeOption = false
                    }
                    // FIXME: 释放按键可能不对
                    val (value, modifiers) = Event.getRimeEvent(keyEventCode, Rime.META_RELEASE_ON)
                    Rime.processKey(value, modifiers)
                }
            }

            override fun onEvent(event: Event) {
                when (event.code) {
                    KeyEvent.KEYCODE_SWITCH_CHARSET -> { // Switch status
                        rime.launchOnReady { api ->
                            service.lifecycleScope.launch {
                                val option = event.getToggle()
                                val status = api.getRuntimeOption(option)
                                api.setRuntimeOption(option, !status)

                                api.commitComposition()
                            }
                        }
                    }
                    KeyEvent.KEYCODE_LANGUAGE_SWITCH -> { // Switch IME
                        if (event.select == ".next") {
                            service.switchToNextIme()
                        } else if (event.select.isNotEmpty()) {
                            service.switchToPrevIme()
                        } else {
                            inputMethodManager.showInputMethodPicker()
                        }
                    }
                    KeyEvent.KEYCODE_FUNCTION -> { // Command Express
                        // Comments from trime.yaml:
                        // %s或者%1$s爲當前字符
                        // %2$s爲當前輸入的編碼
                        // %3$s爲光標前字符
                        // %4$s爲光標前所有字符
                        var arg = event.option
                        val activeTextRegex = Regex(".*%(\\d*)\\$" + "s.*")
                        if (arg.matches(activeTextRegex)) {
                            var activeTextMode =
                                arg.replaceFirst(activeTextRegex, "$1").toDouble().toInt()
                            if (activeTextMode < 1) {
                                activeTextMode = 1
                            }
                            val activeText = service.getActiveText(activeTextMode)
                            arg =
                                String.format(
                                    arg,
                                    service.lastCommittedText,
                                    Rime.getRimeRawInput() ?: "",
                                    activeText,
                                    activeText,
                                )
                        }

                        when (event.command) {
                            "liquid_keyboard" -> {
                                val target =
                                    when {
                                        arg.matches("-?\\d+".toRegex()) -> arg.toInt()
                                        arg.matches("[A-Z]+".toRegex()) -> {
                                            val type = SymbolBoardType.valueOf(arg)
                                            TabManager.tabTags.indexOfFirst { it.type == type }
                                        }
                                        else -> TabManager.tabTags.indexOfFirst { it.text == arg }
                                    }
                                if (target >= 0) {
                                    windowManager.attachWindow(LiquidKeyboard)
                                    liquidKeyboard.select(target)
                                } else {
                                    windowManager.attachWindow(KeyboardWindow)
                                }
                            }
                            "paste_by_char" -> service.pasteByChar()
                            "set_color_scheme" -> ColorManager.setColorScheme(arg)
                            else -> {
                                ShortcutUtils.call(service, event.command, arg)?.let {
                                    service.commitText(it)
                                    service.updateComposing()
                                }
                            }
                        }
                    }
                    KeyEvent.KEYCODE_VOICE_ASSIST -> Speech(service).startListening() // Speech Recognition
                    KeyEvent.KEYCODE_SETTINGS -> { // Settings
                        when (event.option) {
                            "theme" -> showThemePicker()
                            "color" -> showColorPicker()
                            "schema" -> showAvailableSchemaPicker()
                            "sound" -> showSoundEffectPicker()
                            else -> ShortcutUtils.launchMainActivity(service)
                        }
                    }
                    KeyEvent.KEYCODE_PROG_RED -> showColorPicker()
                    KeyEvent.KEYCODE_MENU -> showEnabledSchemaPicker()
                    else -> {
                        if (event.mask == 0 && KeyboardSwitcher.currentKeyboard.isOnlyShiftOn) {
                            if (event.code == KeyEvent.KEYCODE_SPACE && prefs.keyboard.hookShiftSpace) {
                                onKey(event.code, 0)
                                return
                            } else if (event.code >= KeyEvent.KEYCODE_0 &&
                                event.code <= KeyEvent.KEYCODE_9 &&
                                prefs.keyboard.hookShiftNum
                            ) {
                                onKey(event.code, 0)
                                return
                            } else if (prefs.keyboard.hookShiftSymbol) {
                                if (event.code >= KeyEvent.KEYCODE_GRAVE &&
                                    event.code <= KeyEvent.KEYCODE_SLASH ||
                                    event.code == KeyEvent.KEYCODE_COMMA ||
                                    event.code == KeyEvent.KEYCODE_PERIOD
                                ) {
                                    onKey(event.code, 0)
                                    return
                                }
                            }
                        }
                        if (event.mask == 0) {
                            onKey(event.code, KeyboardSwitcher.currentKeyboard.modifer)
                        } else {
                            onKey(event.code, event.mask)
                        }
                    }
                }
            }

            override fun onKey(
                keyEventCode: Int,
                metaState: Int,
            ) {
                // 优先由librime处理按键事件
                if (service.handleKey(keyEventCode, metaState)) return

                needSendUpRimeKey = false

                // 小键盘自动增加锁定
                if (keyEventCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyEventCode <= KeyEvent.KEYCODE_NUMPAD_EQUALS) {
                    service.sendDownUpKeyEvent(keyEventCode, metaState or KeyEvent.META_NUM_LOCK_ON)
                    return
                }
            }

            override fun onText(text: CharSequence) {
                if (!text.first().isAsciiPrintable() && Rime.isComposing) {
                    service.postRimeJob {
                        commitComposition()
                    }
                }
                var sequence = text
                while (sequence.isNotEmpty()) {
                    var slice: String
                    when {
                        BRACED_KEY_EVENT_WITH_ESCAPE.matches(sequence) -> {
                            slice = BRACED_KEY_EVENT_WITH_ESCAPE.matchEntire(sequence)?.groupValues?.get(1) ?: ""
                            // FIXME: rime will not handle the key sequence when
                            //  ascii_mode is on, there may be a better solution
                            //  for this.
                            if (Rime.simulateKeySequence(slice)) {
                                if (Rime.isAsciiMode) {
                                    service.commitText(slice)
                                }
                            } else {
                                service.commitText(slice)
                            }
                        }
                        BRACED_KEY_EVENT.matches(sequence) -> {
                            slice = BRACED_KEY_EVENT.matchEntire(sequence)?.groupValues?.get(1) ?: ""
                            onEvent(EventManager.getEvent(slice))
                        }
                        else -> {
                            slice = sequence.first().toString()
                            onEvent(EventManager.getEvent(slice))
                        }
                    }
                    sequence = sequence.substring(slice.length)
                }
                needSendUpRimeKey = false
            }
        }
    }
}
