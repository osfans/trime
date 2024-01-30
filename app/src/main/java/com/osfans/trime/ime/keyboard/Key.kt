/*
 * Copyright (C) 2015-present, osfans
 * waxaca@163.com https://github.com/osfans
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.osfans.trime.ime.keyboard

import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.osfans.trime.core.Rime.Companion.hasLeft
import com.osfans.trime.core.Rime.Companion.hasMenu
import com.osfans.trime.core.Rime.Companion.isAsciiMode
import com.osfans.trime.core.Rime.Companion.isComposing
import com.osfans.trime.core.Rime.Companion.showAsciiPunch
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.enums.KeyEventType
import com.osfans.trime.util.CollectionUtils.obtainBoolean
import com.osfans.trime.util.CollectionUtils.obtainFloat
import com.osfans.trime.util.CollectionUtils.obtainString
import com.osfans.trime.util.sp2px
import timber.log.Timber
import java.text.MessageFormat

/** [鍵盤][Keyboard]中的各個按鍵，包含單擊、長按、滑動等多種[事件][Event]  */
@Suppress(
    "ktlint:standard:property-naming",
    "ktlint:standard:value-argument-comment",
)
class Key(private val mKeyboard: Keyboard) {
    @JvmField
    var events = arrayOfNulls<Event>(EVENT_NUM)

    @JvmField
    var edgeFlags = 0
    private var send_bindings = true

    @JvmField
    var width = 0

    @JvmField
    var height = 0

    @JvmField
    var gap = 0

    @JvmField
    var row = 0

    @JvmField
    var column = 0
    private var label: String? = null
    var hint: String? = null
        private set
    private var key_back_color: Drawable? = null
    private var hilited_key_back_color: Drawable? = null

    private var key_text_color: Int? = null
    private var key_symbol_color: Int? = null
    private var hilited_key_text_color: Int? = null
    private var hilited_key_symbol_color: Int? = null
    var key_text_size: Int? = null
        private set
    var symbol_text_size: Int? = null
        private set
    var round_corner: Float? = null
        private set
    var key_text_offset_x = 0
        get() = field + key_offset_x
    var key_text_offset_y = 0
        get() = field + key_offset_y
    var key_symbol_offset_x = 0
        get() = field + key_offset_x
    var key_symbol_offset_y = 0
        get() = field + key_offset_y
    var key_hint_offset_x = 0
        get() = field + key_offset_x
    var key_hint_offset_y = 0
        get() = field + key_offset_y
    var key_press_offset_x = 0
    var key_press_offset_y = 0

    @JvmField
    var x = 0

    @JvmField
    var y = 0
    var isPressed = false
        private set
    var isOn = false
        private set

    @JvmField
    val popupCharacters: String? = null

    @JvmField
    val popupResId = 0
    private var labelSymbol: String? = null

    /**
     * Create an empty key with no attributes.
     *
     * @param parent 按鍵所在的[鍵盤][Keyboard]
     * @param mk 從YAML中解析得到的Map
     */
    constructor(parent: Keyboard, mk: Map<String, Any?>) : this(parent) {
        var s: String
        val theme = ThemeManager.activeTheme
        run {
            var hasComposingKey = false
            for (type in KeyEventType.entries) {
                val typeStr = type.toString().lowercase()
                s = obtainString(mk, typeStr, "")
                if (s.isNotEmpty()) {
                    events[type.ordinal] = Event(mKeyboard, s)
                    if (type.ordinal < KeyEventType.COMBO.ordinal) hasComposingKey = true
                } else if (type == KeyEventType.CLICK) {
                    events[type.ordinal] = Event(mKeyboard, "")
                }
            }
            if (hasComposingKey) mKeyboard.composingKeys.add(this)
            label = obtainString(mk, "label", "")
            labelSymbol = obtainString(mk, "label_symbol", "")
            hint = obtainString(mk, "hint", "")
            if (mk.containsKey("send_bindings")) {
                send_bindings = obtainBoolean(mk, "send_bindings", true)
            } else if (!hasComposingKey) {
                send_bindings = false
            }
        }
        mKeyboard.setModiferKey(this.code, this)
        key_text_size = sp2px(obtainFloat(mk, "key_text_size", 0f)).toInt()
        symbol_text_size = sp2px(obtainFloat(mk, "symbol_text_size", 0f)).toInt()
        key_text_color = theme.colors.getColor(mk, "key_text_color")
        hilited_key_text_color = theme.colors.getColor(mk, "hilited_key_text_color")
        key_back_color = theme.colors.getDrawable(mk, "key_back_color")
        hilited_key_back_color = theme.colors.getDrawable(mk, "hilited_key_back_color")
        key_symbol_color = theme.colors.getColor(mk, "key_symbol_color")
        hilited_key_symbol_color = theme.colors.getColor(mk, "hilited_key_symbol_color")
        round_corner = obtainFloat(mk, "round_corner", 0f)
    }

    fun setOn(on: Boolean): Boolean {
        isOn = if (on && isOn) false else on
        return isOn
    }

    val key_offset_x: Int
        get() = if (isPressed) key_press_offset_x else 0
    val key_offset_y: Int
        get() = if (isPressed) key_press_offset_y else 0

    fun getBackColorForState(drawableState: IntArray): Drawable? {
        if (drawableState.contentEquals(KEY_STATE_NORMAL)) {
            return key_back_color
        } else if (drawableState.contentEquals(KEY_STATE_PRESSED)) {
            return hilited_key_back_color
        }
        return null
    }

    fun getTextColorForState(drawableState: IntArray): Int? {
        if (drawableState.contentEquals(KEY_STATE_NORMAL)) {
            return key_text_color
        } else if (drawableState.contentEquals(KEY_STATE_PRESSED)) {
            return hilited_key_text_color
        }
        return null
    }

    fun getSymbolColorForState(drawableState: IntArray): Int? {
        if (drawableState.contentEquals(KEY_STATE_NORMAL)) {
            return key_symbol_color
        } else if (drawableState.contentEquals(KEY_STATE_PRESSED)) {
            return hilited_key_symbol_color
        }
        return null
    }

    /**
     * Informs the key that it has been pressed, in case it needs to change its appearance or state.
     *
     * @see .onReleased
     */
    fun onPressed() {
        isPressed = !isPressed
    }

    /**
     * Changes the pressed state of the key. If it is a sticky key, it will also change the toggled
     * state of the key if the finger was release inside.
     *
     * @param inside whether the finger was released inside the key
     * @see .onPressed
     */
    fun onReleased(inside: Boolean) {
        isPressed = !isPressed
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

    private val isTrimeModifierKey: Boolean
        // Trime把function键消费掉了，因此键盘只处理function键以外的修饰键
        get() = isTrimeModifierKey(this.code)

    fun printModifierKeyState(invalidKey: String?) {
        if (isTrimeModifierKey) {
            Timber.d(
                "\t<TrimeInput>\tkeyState() key=%s, isShifted=%s, on=%s, invalidKey=%s",
                getLabel(),
                mKeyboard.hasModifier(modifierKeyOnMask),
                isOn,
                invalidKey,
            )
        }
    }

    fun printModifierKeyState() {
        if (isTrimeModifierKey) {
            Timber.d(
                "\t<TrimeInput>\tkeyState() key=%s, isShifted=%s, on=%s",
                getLabel(),
                mKeyboard.hasModifier(modifierKeyOnMask),
                isOn,
            )
        }
    }

    val currentDrawableState: IntArray
        /**
         * Returns the drawable state for the key, based on the current state and type of the key.
         *
         * @return the drawable state of the key.
         * @see android.graphics.drawable.StateListDrawable.setState
         */
        get() {
            var states = KEY_STATE_NORMAL
            val isShifted = isTrimeModifierKey && mKeyboard.hasModifier(modifierKeyOnMask)
            if (isShifted || isOn) {
                states =
                    if (isPressed) {
                        KEY_STATE_PRESSED_ON
                    } else {
                        KEY_STATE_NORMAL_ON
                    }
            } else {
                if (click!!.isSticky || click!!.isFunctional) {
                    states =
                        if (isPressed) {
                            KEY_STATE_PRESSED_OFF
                        } else {
                            KEY_STATE_NORMAL_OFF
                        }
                } else {
                    if (isPressed) {
                        states = KEY_STATE_PRESSED
                    }
                }
            }

            // only for modiferKey debug
            if (isTrimeModifierKey) {
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

    private fun getModifierKeyOnMask(keycode: Int): Int {
        if (keycode == KeyEvent.KEYCODE_SHIFT_LEFT ||
            keycode == KeyEvent.KEYCODE_SHIFT_RIGHT
        ) {
            return KeyEvent.META_SHIFT_ON
        }
        if (keycode == KeyEvent.KEYCODE_CTRL_LEFT ||
            keycode == KeyEvent.KEYCODE_CTRL_RIGHT
        ) {
            return KeyEvent.META_CTRL_ON
        }
        if (keycode == KeyEvent.KEYCODE_META_LEFT ||
            keycode == KeyEvent.KEYCODE_META_RIGHT
        ) {
            return KeyEvent.META_META_ON
        }
        if (keycode == KeyEvent.KEYCODE_ALT_LEFT ||
            keycode == KeyEvent.KEYCODE_ALT_RIGHT
        ) {
            return KeyEvent.META_ALT_ON
        }
        return if (keycode == KeyEvent.KEYCODE_SYM) KeyEvent.META_SYM_ON else 0
    }

    val isShift: Boolean
        get() {
            val c = this.code
            return c == KeyEvent.KEYCODE_SHIFT_LEFT || c == KeyEvent.KEYCODE_SHIFT_RIGHT
        }
    val isCtrl: Boolean
        get() {
            val c = this.code
            return c == KeyEvent.KEYCODE_CTRL_LEFT || c == KeyEvent.KEYCODE_CTRL_RIGHT
        }
    val isMeta: Boolean
        get() {
            val c = this.code
            return c == KeyEvent.KEYCODE_META_LEFT || c == KeyEvent.KEYCODE_META_RIGHT
        }
    val isAlt: Boolean
        get() {
            val c = this.code
            return c == KeyEvent.KEYCODE_ALT_LEFT || c == KeyEvent.KEYCODE_ALT_RIGHT
        }
    val isSys: Boolean
        get() {
            val c = this.code
            return c == KeyEvent.KEYCODE_SYM
        }
    val isShiftLock: Boolean
        // Shift、Ctrl、Alt、Meta等修饰键在点击时是否触发锁定
        get() {
            val s = click!!.shiftLock
            // shift_lock #ascii_long: 英文長按中文單按鎖定, long: 長按鎖定, click: 單按鎖定
            if ("long" == s) return false
            if ("click" == s) return true
            return if ("ascii_long" == s) !isAsciiMode else false
        }

    /**
     * @param type 同文按键模式（点击/长按/滑动）
     * @return
     */
    fun sendBindings(type: Int): Boolean {
        var e: Event? = null
        if (type != KeyEventType.CLICK.ordinal && type >= 0 && type <= EVENT_NUM) e = events[type]
        if (e != null) return true
        if (events[KeyEventType.ASCII.ordinal] != null && isAsciiMode) return false
        if (send_bindings) {
            if (events[KeyEventType.PAGING.ordinal] != null && hasLeft()) return true
            if (events[KeyEventType.HAS_MENU.ordinal] != null && hasMenu()) return true
            if (events[KeyEventType.COMPOSING.ordinal] != null && isComposing) return true
        }
        return false
    }

    private val event: Event?
        get() {
            if (events[KeyEventType.ASCII.ordinal] != null && isAsciiMode) {
                return events[KeyEventType.ASCII.ordinal]
            }
            if (events[KeyEventType.PAGING.ordinal] != null && hasLeft()) {
                return events[KeyEventType.PAGING.ordinal]
            }
            if (events[KeyEventType.HAS_MENU.ordinal] != null && hasMenu()) {
                return events[KeyEventType.HAS_MENU.ordinal]
            }
            return if (events[KeyEventType.COMPOSING.ordinal] != null && isComposing) {
                events[KeyEventType.COMPOSING.ordinal]
            } else {
                click
            }
        }
    val click: Event?
        get() = events[KeyEventType.CLICK.ordinal]
    val longClick: Event?
        get() = events[KeyEventType.LONG_CLICK.ordinal]

    fun hasEvent(i: Int): Boolean {
        return events[i] != null
    }

    fun getEvent(i: Int): Event? {
        var e: Event? = null
        if (i != KeyEventType.CLICK.ordinal && i >= 0 && i <= EVENT_NUM) e = events[i]
        if (e != null) return e
        if (events[KeyEventType.ASCII.ordinal] != null && isAsciiMode) {
            return events[KeyEventType.ASCII.ordinal]
        }
        if (send_bindings) {
            if (events[KeyEventType.PAGING.ordinal] != null && hasLeft()) {
                return events[KeyEventType.PAGING.ordinal]
            }
            if (events[KeyEventType.HAS_MENU.ordinal] != null && hasMenu()) {
                return events[KeyEventType.HAS_MENU.ordinal]
            }
            if (events[KeyEventType.COMPOSING.ordinal] != null && isComposing) {
                return events[KeyEventType.COMPOSING.ordinal]
            }
        }
        return click
    }

    val code: Int
        get() = click!!.code

    fun getCode(type: Int): Int {
        return getEvent(type)!!.code
    }

    fun getLabel(): String? {
        val event = event
        return if (!TextUtils.isEmpty(label) && event === click &&
            events[KeyEventType.ASCII.ordinal] == null && !showAsciiPunch()
        ) {
            label
        } else {
            event!!.label // 中文狀態顯示標籤
        }
    }

    fun getPreviewText(type: Int): String {
        return if (type == KeyEventType.CLICK.ordinal) {
            event!!.previewText
        } else {
            getEvent(type)!!.previewText
        }
    }

    val symbolLabel: String?
        get() {
            if (labelSymbol!!.isEmpty()) {
                val longClick = longClick
                if (longClick != null) return longClick.label
            }
            return labelSymbol
        }

    companion object {
        @JvmField
        val KEY_STATE_NORMAL_ON =
            intArrayOf(
                android.R.attr.state_checkable,
                android.R.attr.state_checked,
            )

        @JvmField
        val KEY_STATE_PRESSED_ON =
            intArrayOf(
                android.R.attr.state_pressed,
                android.R.attr.state_checkable,
                android.R.attr.state_checked,
            )

        @JvmField
        val KEY_STATE_NORMAL_OFF = intArrayOf(android.R.attr.state_checkable)

        @JvmField
        val KEY_STATE_PRESSED_OFF =
            intArrayOf(
                android.R.attr.state_pressed,
                android.R.attr.state_checkable,
            )

        @JvmField
        val KEY_STATE_NORMAL = intArrayOf()

        @JvmField
        val KEY_STATE_PRESSED = intArrayOf(android.R.attr.state_pressed)

        @JvmField
        val KEY_STATES =
            arrayOf(
                KEY_STATE_PRESSED_ON, // 0    "hilited_on_key_back_color"   锁定时按下的背景
                KEY_STATE_PRESSED_OFF, // 1   "hilited_off_key_back_color"  功能键按下的背景
                KEY_STATE_NORMAL_ON, // 2     "on_key_back_color"           锁定时背景
                KEY_STATE_NORMAL_OFF, // 3    "off_key_back_color"          功能键背景
                KEY_STATE_PRESSED, // 4       "hilited_key_back_color"      按键按下的背景
                KEY_STATE_NORMAL, // 5         "key_back_color"              按键背景
            )

        @JvmField
        var presetKeys: Map<String, Map<String, Any?>?>? = null
        private val EVENT_NUM = KeyEventType.entries.size

        @JvmField
        val kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)

        @JvmStatic
        fun isTrimeModifierKey(keycode: Int): Boolean {
            return if (keycode == KeyEvent.KEYCODE_FUNCTION) false else KeyEvent.isModifierKey(keycode)
        }
    }
}
