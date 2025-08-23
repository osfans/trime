// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.graphics.drawable.Drawable
import android.view.KeyEvent
import androidx.annotation.ColorInt
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.model.TextKeyboard
import splitties.bitflags.hasFlag

/** [鍵盤][Keyboard]中的各個按鍵，包含單擊、長按、滑動等多種[事件][KeyAction]  */
class Key(
    private val parent: Keyboard,
    private val selfConfig: TextKeyboard.TextKey? = null,
) {
    private val rime = RimeDaemon.getFirstSessionOrNull()!!

    val keyActions: Map<KeyBehavior, KeyAction> =
        buildMap {
            selfConfig?.behaviors?.forEach {
                put(it.key, KeyActionManager.getAction(it.value))
            }
        }
    var edgeFlags = 0
    private val sendBindings: Boolean

    var isPressed = false
        private set
    var isOn = false
        private set

    var x = 0
    var y = 0

    var width = 0
    var height = 0
    var gap = 0
    var row = 0
    var column = 0

    private val label = selfConfig?.label ?: ""
    private val labelSymbol = selfConfig?.labelSymbol ?: ""
    val hint: String = selfConfig?.hint ?: ""

    val keyTextSize: Float = selfConfig?.keyTextSize ?: 0f
    val symbolTextSize: Float = selfConfig?.symbolTextSize ?: 0f
    val roundCorner: Float = selfConfig?.roundCorner ?: 0f
    var keyTextOffsetX = 0f
        get() = field + keyOffsetX
    var keyTextOffsetY = 0f
        get() = field + keyOffsetY
    var keySymbolOffsetX = 0f
        get() = field + keyOffsetX
    var keySymbolOffsetY = 0f
        get() = field + keyOffsetY
    var keyHintOffsetX = 0f
        get() = field + keyOffsetX
    var keyHintOffsetY = 0f
        get() = field + keyOffsetY
    var keyPressOffsetX = 0
    var keyPressOffsetY = 0

    // get color from key customization or just fallback to specified color
    private fun getColor(
        src: TextKeyboard.TextKey.() -> String,
        fallback: String,
    ): Int =
        selfConfig?.let {
            runCatching { ColorManager.getColor(src(it)) }.getOrNull()
        } ?: ColorManager.getColor(fallback)

    // get color from common color schemes or just fallback to default color
    private fun getColor(
        key: String,
        @ColorInt default: Int,
    ): Int = runCatching { ColorManager.getColor(key) }.getOrDefault(default)

    private fun getDrawable(
        src: TextKeyboard.TextKey.() -> String,
        fallback: String,
    ) = selfConfig?.let {
        if (src(it).isEmpty()) null
        ColorManager.getDrawable(src(it))
    } ?: ColorManager.getDrawable(fallback)

    private val keyBackground by lazy { getDrawable({ keyBackColor }, "key_back_color") }
    private val offKeyBackground by lazy { ColorManager.getDrawable("off_key_back_color") }
    private val onKeyBackground by lazy { ColorManager.getDrawable("on_key_back_color") }

    private val keyTextColor by lazy { getColor({ keyTextColor }, "key_text_color") }
    private val offKeyTextColor by lazy { getColor("off_key_text_color", keyTextColor) }
    private val onKeyTextColor by lazy { getColor("on_key_text_color", keyTextColor) }
    private val keySymbolColor by lazy { getColor({ keySymbolColor }, "key_symbol_color") }
    private val offKeySymbolColor by lazy { getColor("off_key_symbol_color", keySymbolColor) }
    private val onKeySymbolColor by lazy { getColor("on_key_symbol_color", keySymbolColor) }
    private val hlKeyBackground by lazy { getDrawable({ hlKeyBackColor }, "hilited_key_back_color") }
    private val hlOffKeyBackground by lazy { ColorManager.getDrawable("hilited_off_key_back_color") }
    private val hlOnKeyBackground by lazy { ColorManager.getDrawable("hilited_on_key_back_color") }
    private val hlKeyTextColor by lazy { getColor({ hlKeyTextColor }, "hilited_key_text_color") }
    private val hlOffKeyTextColor by lazy { getColor("hilited_off_key_text_color", hlKeyTextColor) }
    private val hlOnKeyTextColor by lazy { getColor("hilited_on_key_text_color", hlKeyTextColor) }
    private val hlKeySymbolColor by lazy { getColor({ hlKeySymbolColor }, "hilited_key_symbol_color") }
    private val hlOffKeySymbolColor by lazy { getColor("hilited_off_key_symbol_color", hlKeySymbolColor) }
    private val hlOnKeySymbolColor by lazy { getColor("hilited_on_key_symbol_color", hlKeySymbolColor) }

    init {
        if (selfConfig != null) {
            val hasComposingKey = selfConfig.behaviors.keys.any { it < KeyBehavior.COMBO }
            if (hasComposingKey) parent.composingKeys.add(this)
            sendBindings = selfConfig.sendBindings || hasComposingKey
        } else {
            sendBindings = true
        }
        parent.setModiferKey(this.code, this)
    }

    fun setOn(on: Boolean): Boolean {
        isOn = if (on && isOn) false else on
        return isOn
    }

    private val keyOffsetX: Int
        get() = if (isPressed) keyPressOffsetX else 0
    private val keyOffsetY: Int
        get() = if (isPressed) keyPressOffsetY else 0

    /**
     * Informs the key that it has been pressed, in case it needs to change its appearance or state.
     *
     * @see .onReleased
     */
    fun onPressed() {
        isPressed = true
    }

    /**
     * Changes the pressed state of the key. If it is a sticky key, it will also change the toggled
     * state of the key if the finger was release inside.
     *
     * @see .onPressed
     */
    fun onReleased() {
        isPressed = false
        if (click!!.isSticky) isOn = !isOn
    }

    /**
     * Detects if a point falls inside this key.
     *
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return whether or not the point falls inside the key. If the key is attached to an edge, it
     * will assume that all points between the key and the edge are considered to be inside the
     * key.
     */
    fun isInside(
        x: Int,
        y: Int,
    ): Boolean {
        val leftEdge = edgeFlags and Keyboard.EDGE_LEFT > 0
        val rightEdge = edgeFlags and Keyboard.EDGE_RIGHT > 0
        val topEdge = edgeFlags and Keyboard.EDGE_TOP > 0
        val bottomEdge = edgeFlags and Keyboard.EDGE_BOTTOM > 0
        return (
            (x >= this.x || leftEdge && x <= this.x + width) &&
                (x < this.x + width || rightEdge && x >= this.x) &&
                (y >= this.y || topEdge && y <= this.y + height) &&
                (y < this.y + height || bottomEdge && y >= this.y)
        )
    }

    /**
     * Returns the square of the distance between the center of the key and the given point.
     *
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the square of the distance of the point from the center of the key
     */
    fun squaredDistanceFrom(
        x: Int,
        y: Int,
    ): Int {
        val xDist = this.x + width / 2 - x
        val yDist = this.y + height / 2 - y
        return xDist * xDist + yDist * yDist
    }

    val isModifierKey: Boolean
        // Trime把function键消费掉了，因此键盘只处理function键以外的修饰键
        get() = KeyEvent.isModifierKey(this.code) && this.code != KeyEvent.KEYCODE_FUNCTION

    val modifierKeyOnMask: Int
        get() = getModifierKeyOnMask(this.code)

    private fun getModifierKeyOnMask(keycode: Int): Int =
        when (keycode) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> KeyEvent.META_SHIFT_ON
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> KeyEvent.META_CTRL_ON
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> KeyEvent.META_META_ON
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> KeyEvent.META_ALT_ON
            KeyEvent.KEYCODE_SYM -> KeyEvent.META_SYM_ON
            else -> 0
        }

    val isShift: Boolean
        get() = this.code == KeyEvent.KEYCODE_SHIFT_LEFT || this.code == KeyEvent.KEYCODE_SHIFT_RIGHT

    val isShiftLock: Boolean
        // Shift、Ctrl、Alt、Meta 等修饰键在点击时是否触发锁定
        get() =
            when (click?.shiftLock) {
                "long" -> false // 长按锁定
                "click" -> true // 点击锁定
                "ascii_long" -> !rime.run { statusCached }.isAsciiMode // 英文长按锁定，中文点击锁定
                else -> false
            }

    /**
     * @param behavior 同文按键模式（点击/长按/滑动）
     * @return
     */
    fun sendBindings(behavior: KeyBehavior): Boolean =
        keyActions[behavior]?.takeIf { behavior != KeyBehavior.CLICK } != null || checkKeyAction(sendBindings) != null

    private val keyAction: KeyAction?
        get() = checkKeyAction() ?: click

    val click: KeyAction?
        get() = keyActions[KeyBehavior.CLICK]
    val longClick: KeyAction?
        get() = keyActions[KeyBehavior.LONG_CLICK]

    fun hasAction(behavior: KeyBehavior): Boolean = keyActions[behavior] != null

    fun getAction(behavior: KeyBehavior): KeyAction? =
        keyActions[behavior]?.takeIf { behavior != KeyBehavior.CLICK } ?: checkKeyAction(sendBindings) ?: click

    private fun checkKeyAction(): KeyAction? {
        val status = rime.run { statusCached }
        val menu = rime.run { menuCached }
        return keyActions[KeyBehavior.ASCII].takeIf { status.isAsciiMode }
            ?: keyActions[KeyBehavior.PAGING]?.takeIf { menu.pageNumber != 0 }
            ?: keyActions[KeyBehavior.HAS_MENU]?.takeIf { menu.candidates.isNotEmpty() }
            ?: keyActions[KeyBehavior.COMPOSING]?.takeIf { status.isComposing }
    }

    private fun checkKeyAction(sendBindings: Boolean): KeyAction? = checkKeyAction().takeIf { sendBindings }

    val code: Int
        get() = click?.code ?: KeyEvent.KEYCODE_UNKNOWN

    fun getCode(behavior: KeyBehavior): Int = getAction(behavior)!!.code

    fun getLabel(): String =
        when {
            label.isNotEmpty() &&
                keyAction == click &&
                !keyActions.containsKey(KeyBehavior.ASCII) &&
                !rime.run { statusCached }.let { it.isAsciiMode || it.isAsciiPunch } -> label
            else -> keyAction!!.getLabel(parent) // 中文狀態顯示標籤
        }

    fun getPreviewText(behavior: KeyBehavior): String =
        when (behavior) {
            KeyBehavior.CLICK -> keyAction!!.getPreview(parent)
            else -> getAction(behavior)!!.getPreview(parent)
        }

    val symbolLabel: String
        get() = labelSymbol.ifEmpty { longClick?.getLabel(parent) ?: "" }

    private val appearanceType: Int
        get() {
            return when {
                isModifierKey && parent.modifier.hasFlag(modifierKeyOnMask) || isOn -> 2
                click?.isSticky == true || click?.isFunctional == true -> 1
                else -> 0
            }
        }

    fun getBackgroundDrawable(): Drawable? =
        when (appearanceType) {
            2 -> if (isPressed) hlOnKeyBackground else onKeyBackground
            1 -> {
                if (isPressed) {
                    hlOffKeyBackground ?: hlKeyBackground
                } else {
                    selfConfig?.keyBackColor.takeIf { !it.isNullOrEmpty() }?.let { keyBackground }
                        ?: (offKeyBackground ?: keyBackground)
                }
            }
            else -> if (isPressed) hlKeyBackground else keyBackground
        }

    fun getTextColor(): Int =
        when (appearanceType) {
            2 -> if (isPressed) hlOnKeyTextColor else onKeyTextColor
            1 -> if (isPressed) hlOffKeyTextColor else getColor(selfConfig?.keyTextColor ?: "", offKeyTextColor)
            else -> if (isPressed) hlKeyTextColor else keyTextColor
        }

    fun getSymbolColor(): Int =
        when (appearanceType) {
            2 -> if (isPressed) hlOnKeySymbolColor else onKeySymbolColor
            1 -> if (isPressed) hlOffKeySymbolColor else offKeySymbolColor
            else -> if (isPressed) hlKeySymbolColor else keySymbolColor
        }
}
