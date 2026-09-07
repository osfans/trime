// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.view.KeyEvent
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.LazyPreferenceDelegate
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.util.virtualKeyCharacterMap
import timber.log.Timber

/** [按鍵][Key]的各種事件（單擊、長按、滑動等）  */
class KeyAction(
    token: KeyActionToken,
    presetKeys: Map<String, PresetKey>,
) {
    var code = 0
        private set
    var modifier = 0
        private set
    var text: String = ""
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
    var isSlideCursor = false
        private set
    var isSlideDelete = false
        private set

    val isModifierKey: Boolean
        // Trime把function键消费掉了，因此键盘只处理function键以外的修饰键
        get() = KeyEvent.isModifierKey(this.code) && this.code != KeyEvent.KEYCODE_FUNCTION

    val isShiftLock: Boolean
        get() =
            when (shiftLock) {
                "long" -> false // 长按锁定
                "click" -> true // 点击锁定
                "ascii_long" -> !rime.run { statusCached }.isAsciiMode // 英文长按锁定，中文点击锁定
                else -> false
            }

    val modifierKeyOnMask: Int
        get() = getModifierKeyOnMask(this.code)

    // Display label; computed lazily because the fallbacks read the Android
    // key character map (see [ensureLabels]).
    private var label: String = ""
    private var shiftLabel = ""
    private var labelsReady = false

    private var preview: String? = null
    private var states: List<String> = listOf()

    // Resolved on first read, since the preference store needs the app context.
    private val hookShiftNum: Boolean by LazyPreferenceDelegate {
        AppPrefs.defaultInstance().keyboard.hookShiftNum
    }
    private val hookShiftSymbol: Boolean by LazyPreferenceDelegate {
        AppPrefs.defaultInstance().keyboard.hookShiftSymbol
    }

    private val rime get() = RimeDaemon.getFirstSessionOrNull()!!

    // 获取空格键的schemaName，处理初始化时可能为空的情况
    private fun getSpaceKeySchemaName(): String = rime.run {
        statusCached.schemaName.ifEmpty {
            // 如果schemaName为空，尝试使用schemaId作为显示名称
            schemaCached.schemaId.takeIf { it.isNotEmpty() && it != ".default" } ?: ""
        }
    }

    /**
     * Fills in the display label of a key that declared none, deriving it
     * from the key code and the shifted character map. The calls are
     * deferred out of the constructor so that parsing stays pure.
     */
    private fun ensureLabels() {
        if (labelsReady) return
        labelsReady = true
        label = label.ifEmpty {
            when (code) {
                KeyEvent.KEYCODE_UNKNOWN, KeyEvent.KEYCODE_SPACE -> ""
                else -> KeyCode.getDisplayLabel(code, modifier)
            }
        }
        shiftLabel = label
        if (KeyCode.isStandardKey(code) && virtualKeyCharacterMap.isPrintingKey(code)) {
            val charCode = virtualKeyCharacterMap.get(code, modifier or KeyEvent.META_SHIFT_ON)
            if (charCode != 0) {
                shiftLabel = charCode.toChar().toString()
            }
        }
    }

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
        ensureLabels()
        if (states.isNotEmpty() && toggle.isNotEmpty()) {
            return states[if (rime.run { getRuntimeOption(toggle) }) 1 else 0]
        }
        if (keyboard.isOnlyShiftOn) {
            val asciiMode = rime.run { statusCached }.isAsciiMode
            val composing = rime.run { statusCached }.isComposing
            if (!hookShiftNum && !composing && code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
                return adjustCase(shiftLabel, keyboard)
            }
            if (!hookShiftSymbol &&
                // TODO: 判断中英模式仅能正确处理已配置映射的符号，对于未配置映射的符号，即使在中文模式下也能上屏 Shift 切换的符号。
                asciiMode &&
                (
                    code in KeyEvent.KEYCODE_GRAVE..KeyEvent.KEYCODE_SLASH ||
                        code == KeyEvent.KEYCODE_COMMA ||
                        code == KeyEvent.KEYCODE_PERIOD
                    )
            ) {
                return adjustCase(shiftLabel, keyboard)
            }
        }
        // 仅在为空格键且 label 为空时才去查询 schema 名称，先检查键码以减少无谓计算
        val displayLabel = takeIf { code == KeyEvent.KEYCODE_SPACE && label.isEmpty() }?.let { getSpaceKeySchemaName() } ?: label
        return adjustCase(displayLabel, keyboard)
    }

    fun getText(keyboard: Keyboard): String {
        ensureLabels()
        return if (text.isNotEmpty()) {
            adjustCase(text, keyboard)
        } else if (keyboard.isShifted && code in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z && modifier == 0) {
            if (rime.run { statusCached.isAsciiMode }) "" else adjustCase(label, keyboard)
        } else {
            text
        }
    }

    fun getPreview(keyboard: Keyboard): String = preview ?: getLabel(keyboard)

    init {
        when (token) {
            is KeyActionToken.Plain -> {
                val label: String
                // match like: { x: BackSpace } -> preset_keys/BackSpace: {..., send: BackSpace }
                val preset = presetKeys[token.token]
                if (preset != null) {
                    command = preset.command
                    option = preset.option
                    select = preset.select
                    toggle = preset.toggle
                    preview = preset.preview
                    shiftLock = preset.shiftLock
                    commit = preset.commit
                    text = preset.text
                    isSticky = preset.sticky
                    isRepeatable = preset.repeatable
                    isFunctional = preset.functional
                    isSlideCursor = preset.slideCursor
                    isSlideDelete = preset.slideDelete
                    states = preset.states

                    label = preset.label

                    val (keycode, modifiers) = KeyCode.parse(preset.send)
                    if (keycode != 0 || modifiers != 0) {
                        code = keycode
                        modifier = modifiers
                    } else if (preset.command.isNotEmpty()) {
                        code = KeyEvent.KEYCODE_FUNCTION
                    } else if (preset.send.isNotEmpty()) {
                        // A send value that resolved to nothing leaves the key
                        // inert; report it once per token (actions are cached).
                        Timber.e("preset '%s' has an unrecognized send '%s'", token.token, preset.send)
                    }
                } else {
                    // match like: { x: "{Control+a}" }
                    val (keycode, modifiers) = KeyCode.parse(token.token)
                    if (keycode != 0 || modifiers != 0) {
                        code = keycode
                        modifier = modifiers
                        label = ""
                    } else {
                        // match like: { x: 1 } or { x: q } ...
                        code = KeyCode.nameToKeyCode(token.token)
                        // match like: { x: "(){Left}" } (key sequence to simulate)
                        if (token.token.isNotEmpty() && !KeyCode.isStandardKey(code)) {
                            text = token.token
                            label = token.token.replace(BRACED_PATTERN, "")
                        } else {
                            label = ""
                        }
                    }
                }
                this.label = label
            }
            // match: { x: { commit: a, text: b, label: c } }
            is KeyActionToken.Inline -> {
                commit = token.token.commit ?: ""
                text = token.token.text ?: ""
                label = token.token.label ?: ""
            }
        }
    }

    companion object {
        private val BRACED_PATTERN = Regex("""\{[^{}]+\}""")

        fun getModifierKeyOnMask(keycode: Int): Int = when (keycode) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> KeyEvent.META_SHIFT_ON
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> KeyEvent.META_CTRL_ON
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> KeyEvent.META_META_ON
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> KeyEvent.META_ALT_ON
            KeyEvent.KEYCODE_SYM -> KeyEvent.META_SYM_ON
            else -> 0
        }
    }
}
