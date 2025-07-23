// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.view.KeyEvent
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.enums.Keycode
import com.osfans.trime.util.virtualKeyCharacterMap

/** [按鍵][Key]的各種事件（單擊、長按、滑動等）  */
class KeyAction(
    raw: String,
) {
    var code = 0
        private set
    var modifier = 0
        private set
    var command: String = ""
        private set
    var option: String = ""
        private set
    var select: String = ""
        private set
    var toggle: String = ""
        private set
    var commit: String = ""
        private set
    var shiftLock: String = ""
        private set
    var isFunctional = false
        private set
    var isRepeatable = false
        private set
    var isSticky = false
        private set

    private var text: String = ""
    private var label: String = ""
    private var shiftLabel = ""
    private var preview: String = ""
    private var states: List<String> = listOf()

    private val hookShiftNum by AppPrefs.defaultInstance().keyboard.hookShiftNum
    private val hookShiftSymbol by AppPrefs.defaultInstance().keyboard.hookShiftSymbol

    private val rime = RimeDaemon.getFirstSessionOrNull()!!

    private fun adjustCase(
        str: String,
        keyboard: Keyboard,
    ): String {
        val status = rime.run { statusCached }
        return if (str.length == 1 && (keyboard.isShifted || (!status.isAsciiMode && keyboard.isLabelUppercase))) {
            str.uppercase()
        } else {
            str
        }
    }

    fun getLabel(keyboard: Keyboard): String {
        if (states.isNotEmpty() && toggle.isNotEmpty()) {
            return states[if (rime.run { getRuntimeOption(toggle) }) 1 else 0]
        }
        if (keyboard.isOnlyShiftOn) {
            val status = rime.run { statusCached }
            if (!hookShiftNum && !status.isComposing && code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                return adjustCase(shiftLabel, keyboard)
            }
            if (!hookShiftSymbol &&
                // TODO: 判断中英模式仅能正确处理已配置映射的符号，对于未配置映射的符号，即使在中文模式下也能上屏 Shift 切换的符号。
                status.isAsciiMode &&
                (
                    code in KeyEvent.KEYCODE_GRAVE..KeyEvent.KEYCODE_SLASH ||
                        code == KeyEvent.KEYCODE_COMMA ||
                        code == KeyEvent.KEYCODE_PERIOD
                )
            ) {
                return adjustCase(shiftLabel, keyboard)
            }
        }
        return adjustCase(label, keyboard)
    }

    fun getText(keyboard: Keyboard): String =
        if (text.isNotEmpty()) {
            adjustCase(text, keyboard)
        } else if (keyboard.isShifted && code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z && modifier == 0) {
            adjustCase(label, keyboard)
        } else {
            text
        }

    fun getPreview(keyboard: Keyboard): String = preview.ifEmpty { getLabel(keyboard) }

    init {
        val unbraced = raw.removeSurrounding("{", "}")
        val presetKey = ThemeManager.activeTheme.presetKeys[unbraced]
        when {
            // match like: { x: BackSpace } -> preset_keys/BackSpace: {..., send: BackSpace }
            presetKey != null -> {
                command = presetKey.command
                option = presetKey.option
                select = presetKey.select
                toggle = presetKey.toggle
                label = presetKey.label
                preview = presetKey.preview
                shiftLock = presetKey.shiftLock
                commit = presetKey.commit
                text = presetKey.text
                isSticky = presetKey.sticky
                isRepeatable = presetKey.repeatable
                isFunctional = presetKey.functional
                states = presetKey.states

                val send = presetKey.send
                if (send.isNotEmpty()) {
                    val (c, m) = Keycode.parseSend(send)
                    code = c
                    modifier = m
                } else if (command.isNotEmpty()) {
                    code = KeyEvent.KEYCODE_FUNCTION
                }

                if (label.isEmpty()) {
                    label =
                        when (code) {
                            KeyEvent.KEYCODE_SPACE -> rime.run { statusCached }.schemaName
                            KeyEvent.KEYCODE_UNKNOWN -> ""
                            else -> Keycode.getDisplayLabel(code, modifier)
                        }
                }
            }
            // match like: { x: "{Control+a}" }
            raw.matches(BRACED_STR) -> {
                val (c, m) = Keycode.parseSend(unbraced)
                if (c != KeyEvent.KEYCODE_UNKNOWN || m > 0) {
                    code = c
                    modifier = m
                }
                // match: { x: { commit: a, text: b, label: c } }
                decodeMapFromString(raw).takeIf { it.isNotEmpty() }?.let {
                    commit = it["commit"] ?: ""
                    text = it["text"] ?: ""
                    label = it["label"] ?: ""
                }
            }
            else -> {
                // match like: { x: 1 } or { x: q } ...
                code = Keycode.keyCodeOf(unbraced)
                // match like: { x: "(){Left}" } (key sequence to simulate)
                if (unbraced.isNotEmpty() && !Keycode.isStdKey(code)) {
                    text = raw
                    label = raw.replace(BRACED_STR, "")
                } else if (label.isEmpty()) {
                    label =
                        when (code) {
                            KeyEvent.KEYCODE_SPACE -> rime.run { statusCached }.schemaName
                            KeyEvent.KEYCODE_UNKNOWN -> ""
                            else -> Keycode.getDisplayLabel(code, modifier)
                        }
                }
            }
        }
        shiftLabel = label
        if (Keycode.isStdKey(code) && virtualKeyCharacterMap.isPrintingKey(code)) {
            virtualKeyCharacterMap.get(code, modifier or KeyEvent.META_SHIFT_ON).takeIf { it > 0 }?.let { charCode ->
                shiftLabel = charCode.toChar().toString()
            }
        }
    }

    companion object {
        private val BRACED_STR = Regex("""\{[^{}]+\}""")

        private fun decodeMapFromString(str: String): Map<String, String> =
            str
                .removeSurrounding("{", "}")
                .split(", ")
                .mapNotNull {
                    it.split("=").takeIf { it.size == 2 }?.let { (key, value) -> key to value }
                }.toMap()
    }
}
