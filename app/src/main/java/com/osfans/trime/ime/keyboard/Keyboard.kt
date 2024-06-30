// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.view.KeyEvent
import com.osfans.trime.data.prefs.AppPrefs.Companion.defaultInstance
import com.osfans.trime.data.theme.EventManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.util.CollectionUtils.obtainBoolean
import com.osfans.trime.util.CollectionUtils.obtainFloat
import com.osfans.trime.util.CollectionUtils.obtainInt
import com.osfans.trime.util.CollectionUtils.obtainString
import com.osfans.trime.util.appContext
import com.osfans.trime.util.config.ConfigMap
import com.osfans.trime.util.sp
import splitties.dimensions.dp
import timber.log.Timber
import kotlin.math.abs

/** 從YAML中加載鍵盤配置，包含多個[按鍵][Key]。  */
@Suppress("ktlint:standard:property-naming")
class Keyboard() {
    /** 按鍵默認水平間距  */
    private var horizontalGap: Int

    /** 默認鍵寬  */
    private var keyWidth: Int

    /** 默認鍵高  */
    private var keyHeight: Int

    /** 默認行距  */
    private var verticalGap: Int

    /** 默認按鍵圓角半徑  */
    var roundCorner: Float
        private set

    // 鍵盤的Shift鍵是否按住
    // private boolean mShifted;

    /** 鍵盤的Shift鍵  */
    var mShiftKey: Key? = null
    var mCtrlKey: Key? = null
    var mAltKey: Key? = null
    var mMetaKey: Key? = null
    var mSymKey: Key? = null

    /**
     * Total height of the keyboard, including the padding and keys
     *
     * @return the total height of the keyboard
     */
    var height = 0
        private set

    /**
     * Total width of the keyboard, including left side gaps and keys, but not any gaps on the right
     * side.
     */
    var minWidth = 0
        private set

    /** List of keys in this keyboard  */
    private val mKeys: MutableList<Key>
    val composingKeys: MutableList<Key>
    var modifer = 0
        private set

    /** Width of the screen available to fit the keyboard  */
    private val mDisplayWidth: Int

    /** Keyboard default ascii mode  */
    var asciiMode = false
        private set
    var currentAsciiMode = false
    var resetAsciiMode = true
        private set

    var landscapeKeyboard: String? = null
        private set
    private var mLandscapePercent = 0

    // Variables for pre-computing nearest keys.
    private var mLabelTransform: String? = null
    private var mCellWidth = 0
    private var mCellHeight = 0
    private var mGridNeighbors: Array<IntArray?>? = null
    private var mProximityThreshold: Int
    var isLock = false // 切換程序時記憶鍵盤
        private set
    var asciiKeyboard: String? = null // 英文鍵盤
        private set

    // todo 把按下按键弹出的内容改为单独设计的view，而不是keyboard
    var keyboardHeight: Int = 0
        private set

    /**
     * Creates a blank keyboard from the given resource file and populates it with the specified
     * characters in left-to-right, top-to-bottom fashion, using the specified number of columns.
     *
     * If the specified number of columns is -1, then the keyboard will fit as many keys as
     * possible in each row.
     *
     * @param characters the list of characters to display on the keyboard. One key will be created
     * for each character.
     * @param columns the number of columns of keys to display. If this number is greater than the
     * number of keys that can fit in a row, it will be ignored. If this number is -1, the
     * keyboard will fit as many keys as possible in each row.
     * @param horizontalPadding 按鍵水平間距
     */
    constructor(characters: CharSequence, columns: Int, horizontalPadding: Int) : this() {
        var x = 0
        var y = 0
        var column = 0
        minWidth = 0
        val maxColumns = if (columns == -1) Int.MAX_VALUE else columns
        for (element in characters) {
            if (column >= maxColumns || x + keyWidth + horizontalPadding > mDisplayWidth) {
                x = 0
                y += verticalGap + keyHeight
                column = 0
            }
            val key = Key(this)
            key.x = x
            key.y = y
            key.width = keyWidth
            key.height = keyHeight
            key.gap = horizontalGap
            key.events[0] = EventManager.getEvent(element.toString())
            column++
            x += key.width + key.gap
            mKeys.add(key)
            if (x > minWidth) {
                minWidth = x
            }
        }
        height = y + keyHeight
    }

    private fun getKeyboardConfig(name: String): ConfigMap? {
        val theme = ThemeManager.activeTheme
        val keyboardConfig =
            theme.keyboards.getMap(name)
                ?: theme.keyboards.getMap("default")
        val importPreset = keyboardConfig?.getValue("import_preset")?.getString()
        if (importPreset != null) {
            return getKeyboardConfig(importPreset)
        }
        return keyboardConfig
    }

    constructor(name: String) : this() {
        val theme = ThemeManager.activeTheme
        val keyboardConfig = getKeyboardConfig(name)

        mLabelTransform = obtainString(keyboardConfig, "label_transform", "none")
        asciiMode = obtainInt(keyboardConfig, "ascii_mode", 1) == 1
        currentAsciiMode = asciiMode
        if (!asciiMode) asciiKeyboard = obtainString(keyboardConfig, "ascii_keyboard", "")
        resetAsciiMode = obtainBoolean(keyboardConfig, "reset_ascii_mode", true)
        landscapeKeyboard = obtainString(keyboardConfig, "landscape_keyboard", "")
        mLandscapePercent =
            obtainInt(
                keyboardConfig,
                "landscape_split_percent",
                defaultInstance().keyboard.splitSpacePercent,
            )
        isLock = obtainBoolean(keyboardConfig, "lock", false)
        val columns = obtainInt(keyboardConfig, "columns", 30)
        var defaultWidth = (obtainFloat(keyboardConfig, "width", 0f) * mDisplayWidth / 100).toInt()
        if (defaultWidth == 0) defaultWidth = keyWidth

        // 按键高度取值顺序： keys > keyboard/height > style/key_height
        // 考虑到key设置height_land需要对皮肤做大量修改，而当部分key设置height而部分没有设时会造成按键高度异常，故取消普通按键的height_land参数
        val height = appContext.sp(obtainFloat(keyboardConfig, "height", 0f)).toInt()
        val defaultHeight = if (height > 0) height else keyHeight
        var rowHeight = defaultHeight
        // 定义 新的键盘尺寸计算方式， 避免尺寸计算不恰当，导致切换键盘时键盘高度发生变化，UI闪烁的问题。同时可以快速调整整个键盘的尺寸
        // 1. default键盘的高度 = 其他键盘的高度
        // 2. 当键盘高度(不含padding)与keyboard_height不一致时，每行按键等比例缩放按键高度高度，行之间的间距向上取整数、padding不缩放；
        // 3. 由于高度只能取整数，缩放后仍然存在余数的，由 auto_height_index 指定的行吸收（遵循四舍五入）
        //    特别的，当值为负数时，为倒序序号（-1即倒数第一个）;当值大于按键行数时，为最后一行
        val autoHeightIndex = obtainInt(keyboardConfig, "auto_height_index", -1)
        val lm =
            keyboardConfig!!["keys"]!!.configList
                .map {
                    it!!.configMap.entries.associate { (k, v) ->
                        k to v!!
                    }
                }
        horizontalGap =
            appContext.sp(
                obtainFloat(
                    keyboardConfig,
                    "horizontal_gap",
                    theme.generalStyle.horizontalGap.toFloat(),
                ),
            ).toInt()
        verticalGap =
            appContext.sp(
                obtainFloat(
                    keyboardConfig,
                    "vertical_gap",
                    theme.generalStyle.verticalGap.toFloat(),
                ),
            ).toInt()
        roundCorner =
            obtainFloat(
                keyboardConfig,
                "round_corner",
                theme.generalStyle.roundCorner.toFloat(),
            )
        val horizontalGap = horizontalGap
        val verticalGap = verticalGap
        keyboardHeight = getKeyboardHeight(theme, keyboardConfig)
        val keyboardKeyWidth = obtainFloat(keyboardConfig, "width", 0f)
        val maxColumns = if (columns == -1) Int.MAX_VALUE else columns
        val isSplit = appContext.isLandscapeMode() && isLandscapeSplit
        val (rowWidthTotalWeight, oneWeightWidthPx, multiplier, scaledHeight, scaledVerticalGap) =
            KeyboardSizeCalculator(
                name,
                isSplit,
                mLandscapePercent,
                maxColumns,
                mDisplayWidth,
                keyboardHeight,
                keyboardKeyWidth,
                defaultHeight,
                horizontalGap,
                verticalGap,
                autoHeightIndex,
            ).calc(lm)

        var x = this.horizontalGap / 2
        var y = scaledVerticalGap
        var row = 0
        var column = 0
        minWidth = 0

        try {
            var rowWidthWeight = 0f
            for (mk in lm) {
                val gap = this.horizontalGap
                var keyWidth = obtainFloat(mk, "width", 0f)
                if (keyWidth == 0f && mk.containsKey("click")) {
                    keyWidth = keyboardKeyWidth
                }
                var widthPx = (keyWidth * oneWeightWidthPx).toInt()
                widthPx -= gap
                if (column >= maxColumns || x + widthPx > mDisplayWidth) {
                    // new row
                    rowWidthWeight = 0f
                    x = gap / 2
                    y += scaledVerticalGap + rowHeight
                    column = 0
                    row++
                    if (mKeys.size > 0) mKeys[mKeys.size - 1].edgeFlags = mKeys[mKeys.size - 1].edgeFlags or EDGE_RIGHT
                }
                rowWidthWeight += keyWidth
                if (isSplit && rowWidthWeight >= rowWidthTotalWeight[row]!! / 2 + 1) {
                    rowWidthWeight = Int.MIN_VALUE.toFloat()
                    if (keyWidth > 20) {
                        // enlarge the key if this key is a long key
                        widthPx +=
                            (
                                (
                                    rowWidthTotalWeight[row]!!
                                        * multiplier
                                ) *
                                    oneWeightWidthPx
                            ).toInt()
                    } else {
                        x +=
                            (
                                rowWidthTotalWeight[row]!!
                                    * multiplier
                                    * oneWeightWidthPx
                            ).toInt() // (10 * (defaultWidth));
                    }
                }
                if (column == 0) {
                    rowHeight =
                        if (keyboardHeight > 0) {
                            scaledHeight[row]
                        } else {
                            val heightK = appContext.sp(obtainFloat(mk, "height", 0f)).toInt()
                            if (heightK > 0) heightK else defaultHeight
                        }
                }
                if (!mk.containsKey("click")) { // 無按鍵事件
                    x += widthPx + gap
                    continue // 縮進
                }
                val defaultKeyTextOffsetX =
                    appContext.sp(
                        obtainFloat(
                            keyboardConfig,
                            "key_text_offset_x",
                            theme.generalStyle.keyTextOffsetX.toFloat(),
                        ),
                    ).toInt()
                val defaultKeyTextOffsetY =
                    appContext.sp(
                        obtainFloat(
                            keyboardConfig,
                            "key_text_offset_y",
                            theme.generalStyle.keyTextOffsetY.toFloat(),
                        ),
                    ).toInt()
                val defaultKeySymbolOffsetX =
                    appContext.sp(
                        obtainFloat(
                            keyboardConfig,
                            "key_symbol_offset_x",
                            theme.generalStyle.keySymbolOffsetX.toFloat(),
                        ),
                    ).toInt()
                val defaultKeySymbolOffsetY =
                    appContext.sp(
                        obtainFloat(
                            keyboardConfig,
                            "key_symbol_offset_y",
                            theme.generalStyle.keySymbolOffsetY.toFloat(),
                        ),
                    ).toInt()
                val defaultKeyHintOffsetX =
                    appContext.sp(
                        obtainFloat(
                            keyboardConfig,
                            "key_hint_offset_x",
                            theme.generalStyle.keyHintOffsetX.toFloat(),
                        ),
                    ).toInt()
                val defaultKeyHintOffsetY =
                    appContext.sp(
                        obtainFloat(
                            keyboardConfig,
                            "key_hint_offset_y",
                            theme.generalStyle.keyHintOffsetY.toFloat(),
                        ),
                    ).toInt()
                val defaultKeyPressOffsetX =
                    obtainInt(
                        keyboardConfig,
                        "key_press_offset_x",
                        theme.generalStyle.keyPressOffsetX,
                    )
                val defaultKeyPressOffsetY =
                    obtainInt(
                        keyboardConfig,
                        "key_press_offset_y",
                        theme.generalStyle.keyPressOffsetY,
                    )
                val key = Key(this, mk)
                key.keyTextOffsetX =
                    appContext.sp(
                        obtainFloat(mk, "key_text_offset_x", defaultKeyTextOffsetX.toFloat()),
                    ).toInt()
                key.keyTextOffsetY =
                    appContext.sp(
                        obtainFloat(mk, "key_text_offset_y", defaultKeyTextOffsetY.toFloat()),
                    ).toInt()
                key.keySymbolOffsetX =
                    appContext.sp(
                        obtainFloat(
                            mk,
                            "key_symbol_offset_x",
                            defaultKeySymbolOffsetX.toFloat(),
                        ),
                    ).toInt()
                key.keySymbolOffsetY =
                    appContext.sp(
                        obtainFloat(
                            mk,
                            "key_symbol_offset_y",
                            defaultKeySymbolOffsetY.toFloat(),
                        ),
                    ).toInt()
                key.keyHintOffsetX =
                    appContext.sp(
                        obtainFloat(mk, "key_hint_offset_x", defaultKeyHintOffsetX.toFloat()),
                    ).toInt()
                key.keyHintOffsetY =
                    appContext.sp(
                        obtainFloat(mk, "key_hint_offset_y", defaultKeyHintOffsetY.toFloat()),
                    ).toInt()
                key.keyPressOffsetX = obtainInt(mk, "key_press_offset_x", defaultKeyPressOffsetX)
                key.keyPressOffsetY = obtainInt(mk, "key_press_offset_y", defaultKeyPressOffsetY)
                key.x = x
                key.y = y
                val rightGap = abs(mDisplayWidth - x - widthPx - gap / 2)
                // 右側不留白
                key.width = if (rightGap <= mDisplayWidth / 100) mDisplayWidth - x - gap / 2 else widthPx
                key.height = rowHeight
                key.gap = gap
                key.row = row
                key.column = column
                column++
                x += key.width + key.gap
                mKeys.add(key)
                if (x > minWidth) {
                    minWidth = x
                }
            }
            if (mKeys.size > 0) mKeys[mKeys.size - 1].edgeFlags = mKeys[mKeys.size - 1].edgeFlags or EDGE_RIGHT
            this.height = y + rowHeight + scaledVerticalGap
            for (key in mKeys) {
                if (key.column == 0) key.edgeFlags = key.edgeFlags or EDGE_LEFT
                if (key.row == 0) key.edgeFlags = key.edgeFlags or EDGE_TOP
                if (key.row == row) key.edgeFlags = key.edgeFlags or EDGE_BOTTOM
            }
        } catch (e: Exception) {
            Timber.e(e, "name is %s, row: %d, column %d", name, row, column)
        }
    }

    private fun getKeyboardHeightFromTheme(theme: Theme): Int {
        val keyboardHeight = theme.generalStyle.keyboardHeight
        val keyboardHeightLand = theme.generalStyle.keyboardHeightLand
        val value =
            if (appContext.isLandscapeMode()) {
                keyboardHeightLand.takeIf { it > 0 } ?: keyboardHeight
            } else {
                keyboardHeight
            }
        return appContext.dp(value)
    }

    private fun getKeyboardHeightFromKeyboardConfig(keyboardConfig: Map<String, Any?>?): Int {
        var mkeyboardHeight = appContext.sp(obtainFloat(keyboardConfig, "keyboard_height", 0f)).toInt()
        if (appContext.isLandscapeMode()) {
            val mkeyBoardHeightLand =
                appContext.sp(
                    obtainFloat(keyboardConfig, "keyboard_height_land", 0f),
                ).toInt()
            if (mkeyBoardHeightLand > 0) mkeyboardHeight = mkeyBoardHeightLand
        }
        return mkeyboardHeight
    }

    private fun getKeyboardHeight(
        theme: Theme,
        keyboardConfig: Map<String, Any?>?,
    ): Int {
        val keyboardHeight = getKeyboardHeightFromKeyboardConfig(keyboardConfig)
        return if (keyboardHeight == 0) {
            getKeyboardHeightFromTheme(theme)
        } else {
            keyboardHeight
        }
    }

    fun setModiferKey(
        c: Int,
        key: Key?,
    ) {
        when (c) {
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> {
                mShiftKey = key
            }
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> {
                mCtrlKey = key
            }
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> {
                mMetaKey = key
            }
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> {
                mAltKey = key
            }
            KeyEvent.KEYCODE_SYM -> {
                mSymKey = key
            }
        }
    }

    val keys: List<Key>
        get() = mKeys

    fun hasModifier(modifierMask: Int): Boolean {
        return modifer and modifierMask != 0
    }

    fun hasModifier(): Boolean {
        return modifer != 0
    }

    private fun setModifier(
        mask: Int,
        value: Boolean,
    ): Boolean {
        val b = hasModifier(mask)
        if (b == value) return false
        printModifierKeyState("")
        modifer = if (value) modifer or mask else modifer and mask.inv()
        printModifierKeyState("->")
        return true
    }

    fun printModifierKeyState(tag: String?) {
        Timber.d(
            "\t<TrimeInput>\tkeyState() ctrl=%s, alt=%s, shift=%s, sym=%s, meta=%s\t%s",
            hasModifier(KeyEvent.META_CTRL_ON),
            hasModifier(KeyEvent.META_ALT_ON),
            hasModifier(KeyEvent.META_SHIFT_ON),
            hasModifier(KeyEvent.META_SYM_ON),
            hasModifier(KeyEvent.META_META_ON),
            tag,
        )
    }

    val isAlted: Boolean
        get() = hasModifier(KeyEvent.META_ALT_ON)
    val isShifted: Boolean
        get() = hasModifier(KeyEvent.META_SHIFT_ON)

    // 需要优化
    fun needUpCase(): Boolean {
        if (mShiftKey != null) {
            if (mShiftKey!!.isOn) {
                return true
            }
        }
        return hasModifier(KeyEvent.META_SHIFT_ON)
    }

    /**
     * 設定鍵盤的Shift鍵狀態
     *
     * @param on 是否保持Shift按下狀態(锁定)
     * @param shifted 是否按下Shift
     * @return Shift鍵狀態是否改變
     */
    fun setShifted(
        on: Boolean,
        shifted: Boolean,
    ): Boolean {
        var on = on
        on = on and shifted
        if (mShiftKey != null) mShiftKey!!.setOn(on)
        return setModifier(KeyEvent.META_SHIFT_ON, on || shifted)
    }

    /**
     * 设定修饰键的状态
     *
     * @param on 是否锁定修饰键
     * @param keycode 修饰键on的keyevent mask code
     * @return
     */
    fun clikModifierKey(
        on: Boolean,
        keycode: Int,
    ): Boolean {
        val keyDown = !hasModifier(keycode)
        var keepOn = on
        if (keycode == KeyEvent.META_SHIFT_ON && mShiftKey != null) {
            keepOn = mShiftKey!!.setOn(on)
        } else if (keycode == KeyEvent.META_ALT_ON && mAltKey != null) {
            keepOn = mAltKey!!.setOn(on)
        } else if (keycode == KeyEvent.META_CTRL_ON && mCtrlKey != null) {
            keepOn = mCtrlKey!!.setOn(on)
        } else if (keycode == KeyEvent.META_META_ON && mMetaKey != null) {
            keepOn = mMetaKey!!.setOn(on)
        } else if (keycode == KeyEvent.KEYCODE_SYM && mSymKey != null) {
            keepOn = mSymKey!!.setOn(on)
        }
        return if (on) setModifier(keycode, keepOn) else setModifier(keycode, keyDown)
    }

    fun setAltOn(
        on: Boolean,
        keyDown: Boolean,
    ): Boolean {
        var on = on
        on = on and keyDown
        if (mAltKey != null) mAltKey!!.setOn(on)
        return setModifier(KeyEvent.META_ALT_ON, on || keyDown)
    }

    fun setCtrlOn(
        on: Boolean,
        keyDown: Boolean,
    ): Boolean {
        var on = on
        on = on and keyDown
        if (mCtrlKey != null) mCtrlKey!!.setOn(on)
        return setModifier(KeyEvent.META_CTRL_ON, on || keyDown)
    }

    fun setSymOn(
        on: Boolean,
        keyDown: Boolean,
    ): Boolean {
        var on = on
        on = on and keyDown
        if (mSymKey != null) mSymKey!!.setOn(on)
        return setModifier(KeyEvent.META_SYM_ON, on || keyDown)
    }

    fun setMetaOn(
        on: Boolean,
        keyDown: Boolean,
    ): Boolean {
        var on = on
        on = on and keyDown
        if (mMetaKey != null) mMetaKey!!.setOn(on)
        return setModifier(KeyEvent.META_META_ON, on || keyDown)
    }

    //  public boolean setFunctionOn(boolean on, boolean keyDown) {
    //    on = on & keyDown;
    //    if (mFunctionKey != null) mFunctionKey.setOn(on);
    //    return setModifier(KeyEvent.META_FUNCTION_ON, on || keyDown);
    //  }

    private val MASK_META_WITHOUT_SHIFT = KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_SYM_ON or KeyEvent.META_META_ON
    private val MASK_META_WITHOUT_CTRL = KeyEvent.META_SHIFT_ON or KeyEvent.META_ALT_ON or KeyEvent.META_SYM_ON or KeyEvent.META_META_ON
    private val MASK_META_WITHOUT_ALT = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_SYM_ON or KeyEvent.META_META_ON
    private val MASK_META_WITHOUT_SYS = KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_META_ON
    private val MASK_META_WITHOUT_META = KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_SYM_ON or KeyEvent.META_SHIFT_ON
    private val MASK_META = (
        KeyEvent.META_CTRL_ON
            or KeyEvent.META_ALT_ON
            or KeyEvent.META_SYM_ON
            or KeyEvent.META_META_ON
            or KeyEvent.META_SHIFT_ON
    )

    /** Creates a keyboard from the given xml key layout file.  */
    init {

        // 橫屏模式下，键盘左右两侧到屏幕边缘的距离
        val theme = ThemeManager.activeTheme
        val keyboardSidePadding = theme.generalStyle.keyboardPadding
        val keyboardSidePaddingLandscape = theme.generalStyle.keyboardPaddingLand

        val keyboardSidePaddingPx =
            appContext.dp(
                if (appContext.isLandscapeMode()) keyboardSidePaddingLandscape else keyboardSidePadding,
            )

        mDisplayWidth = appContext.resources.displayMetrics.widthPixels - 2 * keyboardSidePaddingPx

        // Height of the screen
        // final int mDisplayHeight = dm.heightPixels;
        // Log.v(TAG, "keyboard's display metrics:" + dm);
        horizontalGap = appContext.dp(theme.generalStyle.horizontalGap)
        verticalGap = appContext.dp(theme.generalStyle.verticalGap)
        keyWidth = (mDisplayWidth * theme.generalStyle.keyWidth / 100).toInt()
        keyHeight = appContext.dp(theme.generalStyle.keyHeight)
        mProximityThreshold = (keyWidth * SEARCH_DISTANCE).toInt()
        mProximityThreshold *= mProximityThreshold // Square it for comparison
        roundCorner = theme.generalStyle.roundCorner.toFloat()
        mKeys = ArrayList()
        composingKeys = ArrayList()
    }

    val isOnlyShiftOn: Boolean
        get() = mShiftKey != null && mShiftKey!!.isOn && modifer and MASK_META_WITHOUT_SHIFT == 0

    fun resetShifted(): Boolean {
        return if (mShiftKey != null && !mShiftKey!!.isOn) setModifier(KeyEvent.META_SHIFT_ON, false) else false
    }

    fun resetModifer(): Boolean {
        // 这里改为了一次性重置全部修饰键状态并返回TRUE刷新UI，可能有bug
        modifer = 0
        if (mShiftKey != null && mShiftKey!!.isOn) modifer = KeyEvent.META_SHIFT_ON
        if (mAltKey != null && mAltKey!!.isOn) modifer = modifer or KeyEvent.META_ALT_ON
        if (mCtrlKey != null && mCtrlKey!!.isOn) modifer = modifer or KeyEvent.META_CTRL_ON
        if (mMetaKey != null && mMetaKey!!.isOn) modifer = modifer or KeyEvent.META_META_ON
        if (mSymKey != null && mSymKey!!.isOn) modifer = modifer or KeyEvent.KEYCODE_SYM
        return true
    }

    fun refreshModifier(): Boolean {
        // 这里改为了一次性重置全部修饰键状态并返回TRUE刷新UI，可能有bug
        var result = false
        if (mShiftKey != null && !mShiftKey!!.isOn) result = result || setModifier(KeyEvent.META_SHIFT_ON, false)
        if (mAltKey != null && !mAltKey!!.isOn) result = result || setModifier(KeyEvent.META_ALT_ON, false)
        if (mCtrlKey != null && !mCtrlKey!!.isOn) result = result || setModifier(KeyEvent.META_CTRL_ON, false)
        if (mMetaKey != null && !mMetaKey!!.isOn) result = result || setModifier(KeyEvent.META_META_ON, false)
        if (mSymKey != null && !mSymKey!!.isOn) result = result || setModifier(KeyEvent.KEYCODE_SYM, false)
        return result
    }

    private fun computeNearestNeighbors() {
        // Round-up so we don't have any pixels outside the grid
        mCellWidth = (minWidth + GRID_WIDTH - 1) / GRID_WIDTH
        mCellHeight = (height + GRID_HEIGHT - 1) / GRID_HEIGHT
        mGridNeighbors = arrayOfNulls(GRID_SIZE)
        val indices = IntArray(mKeys.size)
        val gridWidth = GRID_WIDTH * mCellWidth
        val gridHeight = GRID_HEIGHT * mCellHeight
        var x = 0
        while (x < gridWidth) {
            var y = 0
            while (y < gridHeight) {
                var count = 0
                for (i in mKeys.indices) {
                    val key = mKeys[i]
                    if (key.squaredDistanceFrom(x, y) < mProximityThreshold ||
                        key.squaredDistanceFrom(x + mCellWidth - 1, y) < mProximityThreshold || (
                            key.squaredDistanceFrom(x + mCellWidth - 1, y + mCellHeight - 1)
                                < mProximityThreshold
                        ) || key.squaredDistanceFrom(x, y + mCellHeight - 1) < mProximityThreshold || key.isInside(x, y) ||
                        key.isInside(x + mCellWidth - 1, y) ||
                        key.isInside(x + mCellWidth - 1, y + mCellHeight - 1) ||
                        key.isInside(x, y + mCellHeight - 1)
                    ) {
                        indices[count++] = i
                    }
                }
                val cell = IntArray(count)
                System.arraycopy(indices, 0, cell, 0, count)
                mGridNeighbors!![y / mCellHeight * GRID_WIDTH + x / mCellWidth] = cell
                y += mCellHeight
            }
            x += mCellWidth
        }
    }

    /**
     * Returns the indices of the keys that are closest to the given point.
     *
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the array of integer indices for the nearest keys to the given point. If the given
     * point is out of range, then an array of size zero is returned.
     */
    fun getNearestKeys(
        x: Int,
        y: Int,
    ): IntArray? {
        if (mGridNeighbors == null) computeNearestNeighbors()
        if (x in 0..<minWidth && y >= 0 && y < height) {
            val index = y / mCellHeight * GRID_WIDTH + x / mCellWidth
            if (index < GRID_SIZE) {
                return mGridNeighbors!![index]
            }
        }
        return IntArray(0)
    }

    val isLandscapeSplit: Boolean
        get() = mLandscapePercent > 0
    val isLabelUppercase: Boolean
        get() = mLabelTransform.contentEquals("uppercase")

    companion object {
        const val EDGE_LEFT = 0x01
        const val EDGE_RIGHT = 0x02
        const val EDGE_TOP = 0x04
        const val EDGE_BOTTOM = 0x08
        private const val GRID_WIDTH = 10
        private const val GRID_HEIGHT = 5
        private const val GRID_SIZE = GRID_WIDTH * GRID_HEIGHT

        // private static final String TAG = Keyboard.class.getSimpleName();

        /** Number of key widths from current touch point to search for nearest keys.  */
        @JvmField
        var SEARCH_DISTANCE = 1.4f

        private fun hasModifier(
            modifierMask: Int,
            mModifierState: Int,
        ): Boolean {
            return mModifierState and modifierMask != 0
        }

        fun printModifierKeyState(
            state: Int,
            tag: String?,
        ) {
            Timber.d(
                "\t<TrimeInput>\tkeyState() ctrl=%s, alt=%s, shift=%s, sym=%s, meta=%s, state=%d\t%s",
                hasModifier(KeyEvent.META_CTRL_ON, state),
                hasModifier(KeyEvent.META_ALT_ON, state),
                hasModifier(KeyEvent.META_SHIFT_ON, state),
                hasModifier(KeyEvent.META_SYM_ON, state),
                hasModifier(KeyEvent.META_META_ON, state),
                state,
                tag,
            )
        }
    }
}
