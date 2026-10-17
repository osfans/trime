/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.app.Dialog
import android.content.Intent
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.CandidateSnapshot
import com.osfans.trime.core.KeyModifiers
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeKeyEvent
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.ime.clipboard.ClipboardWindow
import com.osfans.trime.ime.core.PrivacyModeState
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dialog.EnabledSchemaPickerDialog
import com.osfans.trime.ime.performance.InputRequest
import com.osfans.trime.ime.switches.SwitchOptionWindow
import com.osfans.trime.ime.symbol.LiquidWindow
import com.osfans.trime.ime.window.BoardWindowManager
import com.osfans.trime.ui.main.settings.ColorPickerDialog
import com.osfans.trime.ui.main.settings.SoundEffectPickerDialog
import com.osfans.trime.ui.main.settings.ThemePickerDialog
import com.osfans.trime.util.AppUtils
import com.osfans.trime.util.InputMethodUtils
import com.osfans.trime.util.buildIntentFromAction
import com.osfans.trime.util.buildIntentFromArgument
import com.osfans.trime.util.customFormatDateTime
import com.osfans.trime.util.isAsciiPrintable
import com.osfans.trime.util.toast
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import splitties.systemservices.clipboardManager
import splitties.systemservices.inputMethodManager
import timber.log.Timber

class CommonKeyboardActionListener(override val di: DI) : DIAware {

    private val context: ContextThemeWrapper by instance()
    private val service: TrimeInputMethodService by instance()
    private val theme: Theme by instance()
    private val rime: RimeSession by instance()
    private val windowManager: BoardWindowManager by instance()
    private val keyboardWindow: KeyboardWindow by instance()
    private val liquidWindow: LiquidWindow by instance()

    private val prefs = AppPrefs.defaultInstance()

    private fun showDialog(dialog: suspend (RimeApi) -> Dialog) {
        val editorToken = service.editorToken
        service.postRimeJob {
            val view = dialog(this)
            if (service.isCurrentEditor(editorToken)) service.showDialog(view)
        }
    }

    private fun showThemePicker() {
        val editorToken = service.editorToken
        showDialog {
            ThemePickerDialog.build(service.lifecycleScope, context) {
                service.postRimeJob {
                    if (service.isCurrentEditor(editorToken)) commitComposition()
                }.join()
            }
        }
    }

    private fun showColorPicker() {
        val editorToken = service.editorToken
        showDialog {
            ColorPickerDialog.build(service.lifecycleScope, context) {
                service.postRimeJob {
                    if (service.isCurrentEditor(editorToken)) commitComposition()
                }.join()
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
                setNegativeButton(R.string.enable_schemata) { _, _ ->
                    AppUtils.launchMainToSchemaList(context)
                }
            }
        }
    }

    private fun expandActiveText(input: String): String = if (input.matches(PLACEHOLDER_PATTERN)) {
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
                    keyPressSound(keyEventCode)
                    keyPressSpeak(keyEventCode)
                }
            }

            override fun onAction(action: KeyAction) {
                val text = action.getText(keyboardWindow.currentKeyboard)
                val shouldHandle = when {
                    action.commit.isNotEmpty() -> {
                        service.commitText(action.commit)
                        false
                    }

                    text.isNotEmpty() -> {
                        onText(text)
                        false
                    }

                    else -> true
                }

                if (shouldHandle) {
                    when (action.code) {
                        KeyEvent.KEYCODE_SWITCH_CHARSET -> handleSwitchCharset(action)
                        KeyEvent.KEYCODE_EISU -> keyboardWindow.switchKeyboard(action.select)
                        KeyEvent.KEYCODE_LANGUAGE_SWITCH -> handleLanguageSwitch(action)
                        KeyEvent.KEYCODE_FUNCTION -> handleFunctionCommand(action)
                        KeyEvent.KEYCODE_SETTINGS -> handleSettings(action)
                        KeyEvent.KEYCODE_PROG_RED -> showColorPicker()
                        KeyEvent.KEYCODE_MENU -> showEnabledSchemaPicker()
                        KeyEvent.KEYCODE_VOICE_ASSIST -> switchToVoiceInputMethod()
                        else -> handleDefaultKeyAction(action)
                    }
                }
            }

            private fun handleSwitchCharset(action: KeyAction) {
                val option = action.toggle.ifEmpty { return }
                if (option == "ascii_mode") {
                    keyboardWindow.switchLanguage()
                    return
                }
                service.postRimeJob {
                    setRuntimeOption(option, !getRuntimeOption(option))
                }
            }

            private fun handleLanguageSwitch(action: KeyAction) {
                when {
                    action.select == ".next" -> service.switchToNextIme()
                    action.select.isNotEmpty() -> service.switchToPrevIme()
                    else -> inputMethodManager.showInputMethodPicker()
                }
            }

            private fun handleFunctionCommand(action: KeyAction) {
                val arg = expandActiveText(action.option)

                when (action.command) {
                    "t9_digit" -> arg.singleOrNull()?.takeIf { it in '0'..'9' }?.let { digit ->
                        service.postRimeJob { commitT9Digit(digit) }
                    }

                    "liquid_keyboard" -> handleLiquidKeyboard(arg)

                    "menu_keyboard" -> windowManager.attachWindow(SwitchOptionWindow(di))

                    "clipboard_window" -> handleClipboardWindow(arg)

                    "set_color_scheme" -> handleColorScheme(arg)

                    "set_theme" -> handleTheme(arg)

                    "broadcast" -> service.sendBroadcast(Intent(arg))

                    "clipboard" -> handleClipboard()

                    "commit" -> service.commitText(arg)

                    "date" -> service.commitText(customFormatDateTime(arg))

                    "run" -> handleRunCommand(arg)

                    "apply" -> handleApplyCommand(arg)

                    "share_text" -> service.shareText()

                    "select_candidate" -> handleSelectCandidate(arg)

                    "switch_hide_key_symbol" -> switchHideKeySymbol()

                    "switch_hide_key_hint" -> switchHideKeyHint()

                    else -> handleIntentAction(action.command, arg)
                }
            }

            private fun handleLiquidKeyboard(arg: String) {
                // for compatibility
                if (arg == "剪贴" || arg == "clipboard") {
                    if (!PrivacyModeState.active) windowManager.attachWindow(ClipboardWindow(di))
                    return
                }
                val liquidTagList = theme.liquidKeyboard.getTagList()
                val index = liquidTagList.indexOfFirst { tag ->
                    tag.label == arg || runCatching {
                        LiquidKeyboard.DataType.valueOf(arg.uppercase())
                    }.getOrNull() == tag.type
                }

                if (index >= 0) {
                    windowManager.attachWindow(LiquidWindow)
                    liquidWindow.setDataByIndex(index)
                } else {
                    windowManager.attachWindow(KeyboardWindow)
                }
            }

            private fun handleClipboardWindow(arg: String) {
                if (PrivacyModeState.active) return
                val tabIndex = arg.toIntOrNull()?.coerceIn(0, 1) ?: 0
                windowManager.attachWindow(ClipboardWindow(di, tabIndex))
            }

            private fun handleColorScheme(arg: String) {
                if (arg in ThemeManager.activeTheme.presetColorSchemes) {
                    ColorManager.setColorScheme(arg)
                }
            }

            private fun handleTheme(arg: String) {
                if (arg.isEmpty()) {
                    // 参数为空时，刷新当前主题
                    val themeId = ThemeManager.prefs.selectedTheme.getValue()
                    service.lifecycleScope.launch { ThemeManager.selectTheme(themeId, forceReload = true) }
                } else {
                    // 通过主题名称查找对应的配置ID并切换主题
                    ThemeManager.getAllThemes()
                        .find { it.name.equals(arg, ignoreCase = true) }?.let { item ->
                            service.lifecycleScope.launch { ThemeManager.selectTheme(item.configId) }
                        }
                }
            }

            private fun handleClipboard() {
                clipboardManager.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(service)
                    ?.let { service.commitText(it.toString()) }
            }

            private fun handleRunCommand(arg: String) {
                buildIntentFromArgument(arg)?.let { intent ->
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
                    service.startActivity(intent)
                }
            }

            private fun handleApplyCommand(arg: String) {
                when (arg) {
                    "DEPLOY" -> {
                        Timber.i("try to start maintenance via command ...")
                        rime.launchOnReady { api -> api.deploy() }
                    }

                    "SYNC_USER_DATA" -> {
                        Timber.i("try to sync rime user data via command ...")
                        rime.launchOnReady { api -> api.syncUserData() }
                    }

                    "UPDATE_CONFIG" -> {
                        Timber.i("try to update rime config via command ...")
                        rime.launchOnReady { api ->
                            api.updateConfig()
                            service.lifecycleScope.launch {
                                Toast.makeText(service, R.string.done, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    else -> Timber.w("Unknown apply method: $arg")
                }
            }

            private fun handleIntentAction(command: String, arg: String) {
                buildIntentFromAction(command, arg)?.let { intent ->
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
                    service.startActivity(intent)
                }
            }

            private fun handleSelectCandidate(arg: String) {
                val index = arg.toIntOrNull() ?: return
                service.postRimeJob { selectCandidate(index, false) }
            }

            private fun switchHideKeySymbol() {
                val preference = prefs.keyboard.hideKeySymbol
                preference.setValue(!preference.getValue())
            }

            private fun switchHideKeyHint() {
                val preference = prefs.keyboard.hideKeyHint
                preference.setValue(!preference.getValue())
            }

            private fun handleSettings(action: KeyAction) {
                when (action.option) {
                    "theme" -> showThemePicker()
                    "color" -> showColorPicker()
                    "schema" -> AppUtils.launchMainToSchemaList(context)
                    "sound" -> showSoundEffectPicker()
                    else -> AppUtils.launchMainActivity(service)
                }
            }

            private fun switchToVoiceInputMethod() {
                val pkgName = prefs.general.preferredVoiceInput.getValue()
                val voiceInputSubType = if (pkgName.isNotEmpty()) {
                    InputMethodUtils.voiceInputMethods().find {
                        it.first.packageName == pkgName
                    }?.let {
                        it.first.id to it.second
                    } ?: InputMethodUtils.firstVoiceInput()
                } else {
                    InputMethodUtils.firstVoiceInput()
                }
                if (voiceInputSubType != null) {
                    val (id, subType) = voiceInputSubType
                    InputMethodUtils.switchInputMethod(service, id, subType)
                } else {
                    service.toast(R.string.no_voice_input_installed)
                }
            }

            private fun handleDefaultKeyAction(action: KeyAction) {
                val shouldHookShiftKey = when {
                    prefs.keyboard.hookShiftSpace.getValue() && action.code == KeyEvent.KEYCODE_SPACE -> true
                    prefs.keyboard.hookShiftNum.getValue() && action.code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> true
                    prefs.keyboard.hookShiftSymbol.getValue() && action.code in KeyEvent.KEYCODE_GRAVE..KeyEvent.KEYCODE_SLASH -> true
                    prefs.keyboard.hookShiftSymbol.getValue() && action.code in setOf(KeyEvent.KEYCODE_COMMA, KeyEvent.KEYCODE_PERIOD) -> true
                    else -> false
                }

                if (action.modifier == 0 && keyboardWindow.currentKeyboard.isOnlyShiftOn && shouldHookShiftKey) {
                    onKey(action.code, 0)
                    return
                }

                val modifier = when {
                    action.modifier == 0 -> keyboardWindow.currentKeyboard.modifier

                    (action.modifier and KeyEvent.META_CTRL_ON) != 0 && isNavigationKey(action.code) ->
                        action.modifier or keyboardWindow.currentKeyboard.modifier

                    else -> action.modifier
                }

                onKey(action.code, modifier)
            }

            private fun isNavigationKey(keyCode: Int): Boolean = keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_MOVE_HOME ||
                keyCode == KeyEvent.KEYCODE_MOVE_END

            override fun onRepeat(action: KeyAction) = if (
                (action.code == KeyEvent.KEYCODE_DEL || action.code == KeyEvent.KEYCODE_FORWARD_DEL) &&
                action.commit.isEmpty() && action.getText(keyboardWindow.currentKeyboard).isEmpty()
            ) {
                enqueueKey(action.code, action.modifier or keyboardWindow.currentKeyboard.modifier)
            } else {
                onAction(action)
                null
            }

            override fun onKey(keyEventCode: Int, metaState: Int) {
                enqueueKey(keyEventCode, metaState)
            }

            private fun enqueueKey(
                keyEventCode: Int,
                metaState: Int,
            ): kotlinx.coroutines.Job {
                // An uppercase letter key (e.g. from `{x: A}`) is passed to
                // rime as the uppercase keysym with Shift, matching what a
                // physical keyboard reports via the unicode char, so that
                // rime commits the uppercase letter in ascii mode as well.
                // The generated reverse mapping would otherwise resolve e.g.
                // KEYCODE_A to the lowercase name "a" (XK_a).
                val value =
                    if (metaState and KeyEvent.META_SHIFT_ON != 0 &&
                        keyEventCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z
                    ) {
                        'A'.code + (keyEventCode - KeyEvent.KEYCODE_A) // XK_A..XK_Z
                    } else {
                        val name = KeyCode.codeToKeyName(keyEventCode) ?: "VoidSymbol"
                        RimeKeyEvent.getKeycodeByName(name)
                    }
                val m = if (keyEventCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_EQUALS) {
                    metaState or KeyEvent.META_NUM_LOCK_ON
                } else {
                    metaState
                }
                val modifiers = KeyModifiers.fromMetaState(m).modifiers
                return service.postRimeJob {
                    if (t9Cached.enabled && m == KeyEvent.META_CTRL_ON &&
                        (keyEventCode == KeyEvent.KEYCODE_Z || keyEventCode == KeyEvent.KEYCODE_Y)
                    ) {
                        processKey(value, modifiers)
                        return@postRimeJob
                    }
                    if (m and KeyEvent.META_CTRL_ON != 0) clearPredictionContext()
                    if (service.hookKeyboard(keyEventCode, m)) {
                        Timber.d("handleKey: hook")
                        return@postRimeJob
                    }
                    if (processKey(value, modifiers)) {
                        Timber.d("handleKey: processKey")
                        return@postRimeJob
                    }
                    if (AppUtils.launchKeyCategory(service, keyEventCode)) {
                        Timber.d("handleKey: openCategory")
                        return@postRimeJob
                    }
                    // other special cases
                    if (keyEventCode == KeyEvent.KEYCODE_BACK) {
                        service.requestHideSelf(0)
                    }
                }
            }

            private fun isLanguageMacroAction(action: KeyAction): Boolean = action.commit.isEmpty() && action.text.isEmpty() && (
                (action.code == KeyEvent.KEYCODE_SWITCH_CHARSET && action.toggle == "ascii_mode") ||
                    (action.code == KeyEvent.KEYCODE_EISU && action.select in setOf(".ascii", ".native", ".return"))
                )

            private fun isDefaultMacroKey(action: KeyAction): Boolean = action.code !in setOf(
                KeyEvent.KEYCODE_SWITCH_CHARSET,
                KeyEvent.KEYCODE_EISU,
                KeyEvent.KEYCODE_LANGUAGE_SWITCH,
                KeyEvent.KEYCODE_FUNCTION,
                KeyEvent.KEYCODE_SETTINGS,
                KeyEvent.KEYCODE_PROG_RED,
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_VOICE_ASSIST,
            )

            private suspend fun RimeApi.commitMacroText(text: String) {
                clearPredictionContext()
                val request = currentCoroutineContext()[InputRequest]
                val snapshot = request?.let { CandidateSnapshot(it.editor, 0, it.id) } ?: CandidateSnapshot.EMPTY
                service.commitTextWithoutPredictionReset(text, snapshot)
            }

            override fun onText(input: String) {
                if (input.isEmpty()) return
                Timber.d("onText")
                val editorToken = service.editorToken
                if (!service.isCurrentEditor(editorToken)) return
                val status = rime.run { statusCached }
                val schemaId = status.schemaId
                val expanded = try {
                    expandLanguageMacro(
                        input,
                        isLanguageAction = { isLanguageMacroAction(theme.resolveAction(it)) },
                        textOfAction = { token -> theme.resolveAction(token).takeIf { it.commit.isEmpty() }?.text },
                    )
                } catch (failure: IllegalStateException) {
                    Timber.w(failure, "Rejected cyclic or excessive language macro")
                    return // No partial mode/text command was accepted.
                }
                if (!input[0].isAsciiPrintable() && status.isComposing) {
                    service.postRimeJob { commitComposition() }
                }
                for (value in expanded ?: keyboardMacroParts(input)) {
                    if (!service.isCurrentEditor(editorToken) || (expanded != null && rime.run { statusCached.schemaId } != schemaId)) return
                    val braced = value.startsWith('{') && value.endsWith('}')
                    val action = if (braced) theme.resolveAction(value.removeSurrounding("{", "}")) else null
                    if (expanded != null && action != null && action.commit.isEmpty() && (
                            isLanguageMacroAction(action) || action.code == KeyEvent.KEYCODE_EISU ||
                                action.getText(keyboardWindow.currentKeyboard).isNotEmpty() || isDefaultMacroKey(action)
                            )
                    ) {
                        // Accept at the original macro position. Each mode/default-key callback
                        // already enqueues its native operation exactly once; wrapping it in a
                        // second job would append it after this macro's later text/key input.
                        onAction(action)
                        continue
                    }
                    service.postRimeJob {
                        if (!service.isCurrentEditor(editorToken) || (expanded != null && statusCached.schemaId != schemaId)) return@postRimeJob
                        if (action != null) {
                            if (expanded != null && action.commit.isNotEmpty()) {
                                commitMacroText(action.commit)
                            } else {
                                onAction(action)
                            }
                        } else if (!value[0].isAsciiPrintable()) {
                            commitMacroText(value)
                        } else {
                            simulateKeySequence(value)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val PLACEHOLDER_PATTERN = Regex(".*(%([1-4]\\$)?s).*")
    }
}
