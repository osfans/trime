// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.graphics.drawable.Drawable
import android.view.KeyEvent
import com.osfans.trime.core.Rime.Companion.hasLeft
import com.osfans.trime.core.Rime.Companion.hasMenu
import com.osfans.trime.core.Rime.Companion.isAsciiMode
import com.osfans.trime.core.Rime.Companion.isComposing
import com.osfans.trime.core.Rime.Companion.showAsciiPunch
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.model.TextKeyboard
import java.text.MessageFormat

/** [鍵盤][Keyboard]中的各個按鍵，包含單擊、長按、滑動等多種[事件][KeyAction]  */
@Suppress(
    "ktlint:standard:property-naming",
    "ktlint:standard:value-argument-comment",
)
class Key(
    private val mKeyboard: Keyboard,
) {
    val keyActions = hashMapOf<KeyBehavior, KeyAction>()
    var edgeFlags = 0
    private var sendBindings = true
    private lateinit var textKey: TextKeyboard.TextKey

    var isPressed = false
        private set
    var isOn = false
        private set

    @JvmField
    var x = 0

    @JvmField
    var y = 0

    var width = 0
    var height = 0
    var gap = 0
    var row = 0
    var column = 0

    private var label = ""
    private var labelSymbol = ""
    var hint: String = ""
        private set

    var keyTextSize: Float = 0f
        private set
    var symbolTextSize: Float = 0f
        private set
    var roundCorner: Float = 0f
        private set
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

    private fun getColor(src: String): Int = ColorManager.resolveColor(src)

    private fun getDrawable(src: String) = ColorManager.resolveDrawable(src)

    private val keyBackColor by lazy { getDrawable(textKey.keyBackColor) }
    private val keyTextColor by lazy { getColor(textKey.keyTextColor) }
    private val keySymbolColor by lazy { getColor(textKey.keySymbolColor) }
    private val hilitedKeyBackColor by lazy { getDrawable(textKey.hlKeyBackColor) }
    private val hilitedKeyTextColor by lazy { getColor(textKey.hlKeyTextColor) }
    private val hilitedKeySymbolColor by lazy { getColor(textKey.hlKeySymbolColor) }

    /**
     * Create an empty key with no attributes.
     *
     * @param parent 按鍵所在的[鍵盤][Keyboard]
     * @param textKey 從YAML中解析得到的按键定义
     */
    constructor(parent: Keyboard, textKey: TextKeyboard.TextKey) : this(parent) {
        this.textKey = textKey
        textKey.behaviors.forEach {
            keyActions[it.key] = KeyActionManager.getAction(it.value)
        }
        val hasComposingKey = textKey.behaviors.keys.any { it < KeyBehavior.COMBO }
        if (hasComposingKey) mKeyboard.composingKeys.add(this)
        label = textKey.label
        labelSymbol = textKey.labelSymbol
        hint = textKey.hint
        keyTextSize = textKey.keyTextSize
        symbolTextSize = textKey.symbolTextSize
        roundCorner = textKey.roundCorner
        sendBindings = textKey.sendBindings || hasComposingKey
        mKeyboard.setModiferKey(this.code, this)
    }

    fun setOn(on: Boolean): Boolean {
        isOn = if (on && isOn) false else on
        return isOn
    }

    private val keyOffsetX: Int
        get() = if (isPressed) keyPressOffsetX else 0
    private val keyOffsetY: Int
        get() = if (isPressed) keyPressOffsetY else 0

    fun getBackColorForState(drawableState: IntArray): Drawable? =
        when (drawableState) {
            KEY_STATE_NORMAL, KEY_STATE_OFF_NORMAL -> keyBackColor
            KEY_STATE_PRESSED, KEY_STATE_OFF_PRESSED -> hilitedKeyBackColor
            else -> null
        }

    fun getTextColorForState(drawableState: IntArray): Int? =
        when (drawableState) {
            KEY_STATE_NORMAL, KEY_STATE_OFF_NORMAL -> keyTextColor
            KEY_STATE_PRESSED, KEY_STATE_OFF_PRESSED -> hilitedKeyTextColor
            else -> null
        }

    fun getSymbolColorForState(drawableState: IntArray): Int? =
        when (drawableState) {
            KEY_STATE_NORMAL, KEY_STATE_OFF_NORMAL -> keySymbolColor
            KEY_STATE_PRESSED, KEY_STATE_OFF_PRESSED -> hilitedKeySymbolColor
            else -> null
        }

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

    val currentDrawableState: IntArray
        /**
         * Returns the drawable state for the key, based on the current state and type of the key.
         *
         * @return the drawable state of the key.
         * @see android.graphics.drawable.StateListDrawable.setState
         */
        get() {
            val isShifted = isModifierKey && mKeyboard.hasModifier(modifierKeyOnMask)
            val states =
                when {
                    isShifted || isOn -> if (isPressed) KEY_STATE_ON_PRESSED else KEY_STATE_ON_NORMAL
                    click!!.isSticky || click!!.isFunctional -> if (isPressed) KEY_STATE_OFF_PRESSED else KEY_STATE_OFF_NORMAL
                    else -> if (isPressed) KEY_STATE_PRESSED else KEY_STATE_NORMAL
                }

            // only for modiferKey debug
            if (isModifierKey) {
                mKeyboard.printModifierKeyState(
                    MessageFormat.format(
                        "getCurrentDrawableState() Key={0} states={1} on={2} isShifted={3} pressed={4} sticky={5}",
                        getLabel(),
                        listOf(*KEY_STATES).indexOf(states),
                        isOn,
                        isShifted,
                        isPressed,
                        click!!.isSticky,
                    ),
                )
            }
            return states
        }

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
                "ascii_long" -> !isAsciiMode // 英文长按锁定，中文点击锁定
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

    private fun checkKeyAction(): KeyAction? =
        keyActions[KeyBehavior.ASCII].takeIf { isAsciiMode }
            ?: keyActions[KeyBehavior.PAGING]?.takeIf { hasLeft() }
            ?: keyActions[KeyBehavior.HAS_MENU]?.takeIf { hasMenu() }
            ?: keyActions[KeyBehavior.COMPOSING]?.takeIf { isComposing }

    private fun checkKeyAction(sendBindings: Boolean): KeyAction? = checkKeyAction().takeIf { sendBindings }

    val code: Int
        get() = click?.code ?: KeyEvent.KEYCODE_UNKNOWN

    fun getCode(behavior: KeyBehavior): Int = getAction(behavior)!!.code

    fun getLabel(): String =
        when {
            label.isNotEmpty() &&
                keyAction == click &&
                keyActions[KeyBehavior.ASCII] == null &&
                !showAsciiPunch() -> label
            else -> keyAction!!.getLabel(mKeyboard) // 中文狀態顯示標籤
        }

    fun getPreviewText(behavior: KeyBehavior): String =
        when (behavior) {
            KeyBehavior.CLICK -> keyAction!!.getPreview(mKeyboard)
            else -> getAction(behavior)!!.getPreview(mKeyboard)
        }

    val symbolLabel: String
        get() = labelSymbol.takeIf { it.isNotEmpty() } ?: longClick?.getLabel(mKeyboard) ?: ""

    companion object {
        val KEY_STATE_ON_NORMAL =
            intArrayOf(
                android.R.attr.state_checkable,
                android.R.attr.state_checked,
            )

        val KEY_STATE_ON_PRESSED =
            intArrayOf(
                android.R.attr.state_pressed,
                android.R.attr.state_checkable,
                android.R.attr.state_checked,
            )

        val KEY_STATE_OFF_NORMAL = intArrayOf(android.R.attr.state_checkable)

        val KEY_STATE_OFF_PRESSED =
            intArrayOf(
                android.R.attr.state_pressed,
                android.R.attr.state_checkable,
            )

        val KEY_STATE_NORMAL = intArrayOf()

        val KEY_STATE_PRESSED = intArrayOf(android.R.attr.state_pressed)

        val KEY_STATES =
            arrayOf(
                KEY_STATE_ON_PRESSED, // 0    "hilited_on_key_back_color"   锁定时按下的背景
                KEY_STATE_ON_NORMAL, // 1     "on_key_back_color"           锁定时背景
                KEY_STATE_OFF_PRESSED, // 2   "hilited_off_key_back_color"  功能键按下的背景
                KEY_STATE_OFF_NORMAL, // 3    "off_key_back_color"          功能键背景
                KEY_STATE_PRESSED, // 4       "hilited_key_back_color"      按键按下的背景
                KEY_STATE_NORMAL, // 5        "key_back_color"              按键背景
            )
    }
}
