/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.KeyModifier
import com.osfans.trime.core.KeyModifiers
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeKeyEvent
import com.osfans.trime.core.RimeKeyMapping
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.dialog.EnabledSchemaPickerDialog
import com.osfans.trime.ime.enums.Keycode
import com.osfans.trime.ime.symbol.LiquidKeyboard
import com.osfans.trime.ime.symbol.SymbolBoardType
import com.osfans.trime.ime.symbol.TabManager
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ui.main.settings.ColorPickerDialog
import com.osfans.trime.ui.main.settings.SoundEffectPickerDialog
import com.osfans.trime.ui.main.settings.ThemePickerDialog
import com.osfans.trime.util.AppUtils
import com.osfans.trime.util.buildIntentFromAction
import com.osfans.trime.util.buildIntentFromArgument
import com.osfans.trime.util.customFormatDateTime
import com.osfans.trime.util.isAsciiPrintable
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import splitties.systemservices.clipboardManager
import splitties.systemservices.inputMethodManager
import timber.log.Timber

@InputScope
@Inject
class CommonKeyboardActionListener(
    private val context: Context,
    private val service: TrimeInputMethodService,
    private val rime: RimeSession,
    private val liquidKeyboard: LiquidKeyboard,
    private val windowManager: BoardWindowManager,
    private val lazyKeyboardWindow: Lazy<KeyboardWindow>,
) {
    companion object {
        /** Pattern for braced key event like `{Left}`, `{Right}`, etc. */
        private val BRACED_KEY_EVENT = """^(\{[^{}]+\}).*$""".toRegex()

        /** Pattern for unbraced characters (including {Escape}) like `abc`, `{Escape}jk` etc. */
        private val UNBRACED_CHAR = """^((\{Escape\})?[^{}]+).*$""".toRegex()

        private val PLACEHOLDER_PATTERN = Regex(".*(%([1-4]\\$)?s).*")
    }

    private val keyboardWindow by lazyKeyboardWindow

    private val prefs = AppPrefs.defaultInstance()

    private var shouldReleaseKey: Boolean = false

    private fun showDialog(dialog: suspend (RimeApi) -> Dialog) {
        rime.launchOnReady { api ->
            service.lifecycleScope.launch {
                service.showDialog(dialog(api))
            }
        }
    }

    private fun showThemePicker() {
        showDialog { api ->
            ThemePickerDialog.build(service.lifecycleScope, context) {
                api.commitComposition()
            }
        }
    }

    private fun showColorPicker() {
        showDialog { api ->
            ColorPickerDialog.build(service.lifecycleScope, context) {
                api.commitComposition()
            }
        }
    }

    private fun showSoundEffectPicker() {
        showDialog {
            SoundEffectPickerDialog.build(service.lifecycleScope, context)
        }
    }

    private fun showEnabledSchemaPicker() {
        showDialog { api ->
            EnabledSchemaPickerDialog.build(api, service.lifecycleScope, context) {
                setNegativeButton(R.string.schemata) { _, _ ->
                    AppUtils.launchMainToSchemaList(context)
                }
            }
        }
    }

    private fun expandActiveText(input: String): String =
        if (input.matches(PLACEHOLDER_PATTERN)) {
            input.format(
                service.getActiveText(1),
                service.getActiveText(2),
                service.getActiveText(3),
                service.getActiveText(4),
            )
        } else {
            input
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
                if (shouldReleaseKey) {
                    // FIXME: 释放按键可能不对
                    val value = RimeKeyMapping.keyCodeToVal(keyEventCode)
                    if (value != RimeKeyMapping.RimeKey_VoidSymbol) {
                        service.postRimeJob {
                            processKey(value, KeyModifier.Release.modifier)
                        }
                    }
                }
            }

            override fun onAction(action: KeyAction) {
                if (action.commit.isNotEmpty()) {
                    service.commitText(action.commit, true)
                    return
                }
                KeyboardSwitcher.currentKeyboard.let {
                    if (action.getText(it).isNotEmpty()) {
                        onText(action.getText(it))
                        return
                    }
                }
                when (action.code) {
                    KeyEvent.KEYCODE_SWITCH_CHARSET -> { // Switch status
                        rime.launchOnReady { api ->
                            service.lifecycleScope.launch {
                                val option = action.toggle.ifEmpty { return@launch }
                                val status = api.getRuntimeOption(option)
                                api.setRuntimeOption(option, !status)

                                api.commitComposition()
                            }
                        }
                    }
                    KeyEvent.KEYCODE_EISU -> { // Switch keyboard
                        keyboardWindow.switchKeyboard(action.select)
                    }
                    KeyEvent.KEYCODE_LANGUAGE_SWITCH -> { // Switch IME
                        if (action.select == ".next") {
                            service.switchToNextIme()
                        } else if (action.select.isNotEmpty()) {
                            service.switchToPrevIme()
                        } else {
                            inputMethodManager.showInputMethodPicker()
                        }
                    }
                    KeyEvent.KEYCODE_FUNCTION -> { // Command Express
                        val arg = expandActiveText(action.option)
                        when (action.command) {
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
                            "set_color_scheme" -> {
                                val newScheme = ThemeManager.activeTheme.colorSchemes.find { it.id == arg }
                                if (newScheme != null) ColorManager.setColorScheme(newScheme)
                            }
                            "broadcast" -> service.sendBroadcast(Intent(arg))
                            "clipboard" -> {
                                clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(service)?.let {
                                    service.commitText(it)
                                }
                            }
                            "commit" -> service.commitText(arg)
                            "date" -> service.commitText(customFormatDateTime(arg))
                            "run" -> {
                                val intent = buildIntentFromArgument(arg)
                                if (intent != null) {
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
                                    service.startActivity(intent)
                                }
                            }
                            "share_text" -> service.shareText()
                            else -> {
                                val intent = buildIntentFromAction(action.command, arg)
                                if (intent != null) {
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
                                    service.startActivity(intent)
                                }
                            }
                        }
                    }
                    KeyEvent.KEYCODE_SETTINGS -> { // Settings
                        when (action.option) {
                            "theme" -> showThemePicker()
                            "color" -> showColorPicker()
                            "schema" -> AppUtils.launchMainToSchemaList(context)
                            "sound" -> showSoundEffectPicker()
                            else -> AppUtils.launchMainActivity(service)
                        }
                    }
                    KeyEvent.KEYCODE_PROG_RED -> showColorPicker()
                    KeyEvent.KEYCODE_MENU -> showEnabledSchemaPicker()
                    else -> {
                        if (action.modifier == 0 && KeyboardSwitcher.currentKeyboard.isOnlyShiftOn) {
                            val shouldHookSpace =
                                prefs.keyboard.hookShiftSpace.getValue() && action.code == KeyEvent.KEYCODE_SPACE
                            val shouldHookNumber =
                                prefs.keyboard.hookShiftNum.getValue() && action.code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9
                            val shouldHookSymbol =
                                prefs.keyboard.hookShiftSymbol.getValue() &&
                                    (
                                        action.code in KeyEvent.KEYCODE_GRAVE..KeyEvent.KEYCODE_SLASH ||
                                            action.code == KeyEvent.KEYCODE_COMMA ||
                                            action.code == KeyEvent.KEYCODE_PERIOD
                                    )
                            if (shouldHookSpace || shouldHookNumber || shouldHookSymbol) {
                                onKey(action.code, 0)
                                return
                            }
                        }
                        val modifier =
                            when {
                                action.modifier == 0 -> KeyboardSwitcher.currentKeyboard.modifier
                                (action.modifier and KeyEvent.META_CTRL_ON) != 0 -> {
                                    when (action.code) {
                                        in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT,
                                        KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END,
                                        -> action.modifier or KeyboardSwitcher.currentKeyboard.modifier
                                        else -> action.modifier
                                    }
                                }
                                else -> action.modifier
                            }
                        onKey(action.code, modifier)
                    }
                }
            }

            override fun onKey(
                keyEventCode: Int,
                metaState: Int,
            ) {
                shouldReleaseKey = false
                val value =
                    RimeKeyMapping
                        .keyCodeToVal(keyEventCode)
                        .takeIf { it != RimeKeyMapping.RimeKey_VoidSymbol }
                        ?: RimeKeyEvent.getKeycodeByName(Keycode.keyNameOf(keyEventCode))
                val modifiers = KeyModifiers.fromMetaState(metaState).modifiers
                service.postRimeJob {
                    if (service.hookKeyboard(keyEventCode, metaState)) {
                        Timber.d("handleKey: hook")
                        return@postRimeJob
                    }
                    if (processKey(value, modifiers)) {
                        shouldReleaseKey = true
                        Timber.d("handleKey: processKey")
                        return@postRimeJob
                    }
                    if (AppUtils.launchKeyCategory(service, keyEventCode)) {
                        Timber.d("handleKey: openCategory")
                        return@postRimeJob
                    }
                    shouldReleaseKey = false

                    when (keyEventCode) {
                        KeyEvent.KEYCODE_BACK -> service.requestHideSelf(0)
                        else -> {
                            // 小键盘自动增加锁定
                            if (keyEventCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_EQUALS) {
                                service.sendDownUpKeyEvent(
                                    keyEventCode,
                                    metaState or KeyEvent.META_NUM_LOCK_ON,
                                )
                            }
                        }
                    }
                }
            }

            override fun onText(text: CharSequence) {
                val status = rime.run { statusCached }
                if (!text.first().isAsciiPrintable() && status.isComposing) {
                    service.postRimeJob { commitComposition() }
                }

                var sequence = text
                while (sequence.isNotEmpty()) {
                    val slice =
                        when {
                            UNBRACED_CHAR.matches(sequence) -> UNBRACED_CHAR.matchEntire(sequence)?.groupValues?.get(1) ?: ""
                            BRACED_KEY_EVENT.matches(sequence) -> BRACED_KEY_EVENT.matchEntire(sequence)?.groupValues?.get(1) ?: ""
                            else -> sequence.first().toString()
                        }

                    service.postRimeJob {
                        when {
                            slice.startsWith("{") && slice.endsWith("}") -> onAction(KeyActionManager.getAction(slice))
                            !Rime.simulateKeySequence(slice) -> service.commitText(slice.replace("{Escape}", ""))
                        }
                    }

                    sequence = sequence.substring(slice.length)
                }
                shouldReleaseKey = false
            }
        }
    }
}
