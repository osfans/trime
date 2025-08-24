// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.view.KeyEvent
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.util.appContext
import com.osfans.trime.util.sp
import splitties.bitflags.hasFlag
import splitties.dimensions.dp
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.pow

/** 從YAML中加載鍵盤配置，包含多個[按鍵][Key]。  */
@Suppress("ktlint:standard:property-naming")
class Keyboard(
    private val theme: Theme,
    selfConfig: TextKeyboard? = null,
) {
    /** 按鍵默認水平間距  */
    private val horizontalGap: Int =
        (
            intArrayOf(
                selfConfig?.horizontalGap ?: 0,
                theme.generalStyle.horizontalGap,
            ).firstOrNull { it > 0 } ?: 0
        ).also { appContext.dp(it) }

    /** 默認鍵寬  */
    private val keyWidth: Int = (allowedWidth * theme.generalStyle.keyWidth / 100).toInt()

    /** 默認鍵高  */
    private val keyHeight: Int =
        (
            intArrayOf(
                selfConfig?.height?.toInt() ?: 0,
                theme.generalStyle.keyHeight,
            ).firstOrNull { it > 0 } ?: 0
        ).also { appContext.dp(it) }

    /** 默認行距  */
    private val verticalGap: Int =
        (
            intArrayOf(
                selfConfig?.verticalGap ?: 0,
                theme.generalStyle.verticalGap,
            ).firstOrNull { it > 0 } ?: 0
        ).also { appContext.dp(it) }

    /** 默認按鍵圓角半徑  */
    val roundCorner: Float =
        floatArrayOf(
            selfConfig?.roundCorner ?: 0f,
            theme.generalStyle.roundCorner,
        ).firstOrNull { it > 0 } ?: 0f

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
    private val mKeys = mutableListOf<Key>()
    val composingKeys = mutableListOf<Key>()
    var modifier = 0
        private set

    /** Width of the screen available to fit the keyboard  */
    private val allowedWidth: Int
        get() {
            val keyboardSidePadding = theme.generalStyle.keyboardPadding
            val keyboardSidePaddingLandscape = theme.generalStyle.keyboardPaddingLand
            val sidePaddingPx = if (appContext.isLandscapeMode()) keyboardSidePaddingLandscape else keyboardSidePadding
            return appContext.resources.displayMetrics.widthPixels - 2 * appContext.dp(sidePaddingPx)
        }

    /** Keyboard default ascii mode  */
    val asciiMode = selfConfig?.asciiMode ?: false
    val resetAsciiMode = selfConfig?.resetAsciiMode ?: true

    val landscapeKeyboard: String? = selfConfig?.landscapeKeyboard
    private val preferredSplitPercent by AppPrefs.defaultInstance().keyboard.splitSpacePercent
    private val landscapePercent =
        intArrayOf(
            selfConfig?.landscapeSplitPercent ?: 0,
            preferredSplitPercent,
        ).firstOrNull { it > 0 } ?: 0

    // Variables for pre-computing nearest keys.
    private val labelTransform = selfConfig?.labelTransform ?: TextKeyboard.LabelTransform.NONE
    private var mCellWidth = 0
    private var mCellHeight = 0
    private var gridNeighbors: Array<IntArray?>? = null

    private val proximityThreshold: Int =
        (keyWidth * SEARCH_DISTANCE).pow(2).toInt() // Square it for comparison
    val isLock = selfConfig?.lock ?: false // 切換程序時記憶鍵盤
    val asciiKeyboard: String? = selfConfig?.asciiKeyboard // 英文鍵盤

    // todo 把按下按键弹出的内容改为单独设计的view，而不是keyboard
    val keyboardHeight: Int =
        intArrayOf(
            selfConfig?.let { getKeyboardHeightFromKeyboardConfig(it) } ?: 0,
            getKeyboardHeightFromTheme(theme),
        ).firstOrNull { it > 0 } ?: 0

    init {
        if (selfConfig != null) {
            val columns = selfConfig.columns
            // 按键高度取值顺序： keys > keyboard/height > style/key_height
            // 考虑到key设置height_land需要对皮肤做大量修改，而当部分key设置height而部分没有设时会造成按键高度异常，故取消普通按键的height_land参数
            var rowHeight = keyHeight
            // 定义 新的键盘尺寸计算方式， 避免尺寸计算不恰当，导致切换键盘时键盘高度发生变化，UI闪烁的问题。同时可以快速调整整个键盘的尺寸
            // 1. default键盘的高度 = 其他键盘的高度
            // 2. 当键盘高度(不含padding)与keyboard_height不一致时，每行按键等比例缩放按键高度高度，行之间的间距向上取整数、padding不缩放；
            // 3. 由于高度只能取整数，缩放后仍然存在余数的，由 auto_height_index 指定的行吸收（遵循四舍五入）
            //    特别的，当值为负数时，为倒序序号（-1即倒数第一个）;当值大于按键行数时，为最后一行
            val autoHeightIndex = selfConfig.autoHeightIndex
            val keys = selfConfig.keys
            val keyboardKeyWidth = selfConfig.width
            val maxColumns = if (columns == -1) Int.MAX_VALUE else columns
            val isSplit = appContext.isLandscapeMode() && landscapePercent > 0
            val (rowWidthTotalWeight, oneWeightWidthPx, multiplier, scaledHeight, scaledVerticalGap) =
                KeyboardSizeCalculator(
                    isSplit,
                    landscapePercent,
                    maxColumns,
                    allowedWidth,
                    keyboardHeight,
                    keyboardKeyWidth,
                    keyHeight,
                    horizontalGap,
                    verticalGap,
                    autoHeightIndex,
                ).calc(keys)

            var x = this.horizontalGap / 2
            var y = scaledVerticalGap
            var row = 0
            var column = 0
            minWidth = 0

            try {
                var rowWidthWeight = 0f
                for (textKey in keys) {
                    val gap = this.horizontalGap
                    val keyWidth =
                        if (textKey.width == 0f && textKey.click.isNotEmpty()) {
                            keyboardKeyWidth
                        } else {
                            textKey.width
                        }
                    var widthPx = (keyWidth * oneWeightWidthPx).toInt()
                    widthPx -= gap
                    if (column >= maxColumns || x + widthPx > allowedWidth) {
                        // new row
                        rowWidthWeight = 0f
                        x = gap / 2
                        y += scaledVerticalGap + rowHeight
                        column = 0
                        row++
                        if (mKeys.isNotEmpty()) {
                            mKeys[mKeys.size - 1].edgeFlags =
                                mKeys[mKeys.size - 1].edgeFlags or EDGE_RIGHT
                        }
                    }
                    rowWidthWeight += keyWidth
                    val totalWeightOfThisRow = rowWidthTotalWeight[row] ?: 0f
                    if (isSplit && rowWidthWeight >= totalWeightOfThisRow / 2 + 1) {
                        rowWidthWeight = Int.MIN_VALUE.toFloat()
                        val weight = (totalWeightOfThisRow * multiplier * oneWeightWidthPx).toInt()
                        if (keyWidth > 20) {
                            // enlarge the key if this key is a long key
                            widthPx += weight
                        } else {
                            x += weight // (10 * (defaultWidth));
                        }
                    }
                    if (column == 0) {
                        rowHeight =
                            if (keyboardHeight > 0) {
                                scaledHeight[row]
                            } else {
                                if (textKey.height > 0) {
                                    appContext.sp(textKey.height).toInt()
                                } else {
                                    keyHeight
                                }
                            }
                    }
                    if (textKey.click.isEmpty()) { // 無按鍵事件
                        x += widthPx + gap
                        continue // 縮進
                    }
                    val key = Key(this, textKey)
                    key.keyTextOffsetX =
                        floatArrayOf(
                            textKey.keyTextOffsetX,
                            selfConfig.keyTextOffsetX,
                            theme.generalStyle.keyTextOffsetX,
                        ).firstOrNull { it != 0f } ?: 0f
                    key.keyTextOffsetY =
                        floatArrayOf(
                            textKey.keyTextOffsetY,
                            selfConfig.keyTextOffsetY,
                            theme.generalStyle.keyTextOffsetY,
                        ).firstOrNull { it != 0f } ?: 0f
                    key.keySymbolOffsetX =
                        floatArrayOf(
                            textKey.keySymbolOffsetX,
                            selfConfig.keySymbolOffsetX,
                            theme.generalStyle.keySymbolOffsetX,
                        ).firstOrNull { it != 0f } ?: 0f
                    key.keySymbolOffsetY =
                        floatArrayOf(
                            textKey.keySymbolOffsetY,
                            selfConfig.keySymbolOffsetY,
                            theme.generalStyle.keySymbolOffsetY,
                        ).firstOrNull { it != 0f } ?: 0f
                    key.keyHintOffsetX =
                        floatArrayOf(
                            textKey.keyHintOffsetX,
                            selfConfig.keyHintOffsetX,
                            theme.generalStyle.keyHintOffsetX,
                        ).firstOrNull { it != 0f } ?: 0f
                    key.keyHintOffsetY =
                        floatArrayOf(
                            textKey.keyHintOffsetY,
                            selfConfig.keyHintOffsetY,
                            theme.generalStyle.keyHintOffsetY,
                        ).firstOrNull { it != 0f } ?: 0f
                    key.keyPressOffsetX =
                        intArrayOf(
                            textKey.keyPressOffsetX,
                            selfConfig.keyPressOffsetX,
                            theme.generalStyle.keyPressOffsetX,
                        ).firstOrNull { it != 0 } ?: 0
                    key.keyPressOffsetY =
                        intArrayOf(
                            textKey.keyPressOffsetY,
                            selfConfig.keyPressOffsetY,
                            theme.generalStyle.keyPressOffsetY,
                        ).firstOrNull { it != 0 } ?: 0
                    key.x = x
                    key.y = y
                    val rightGap = abs(allowedWidth - x - widthPx - gap / 2)
                    // 右側不留白
                    key.width =
                        if (rightGap <= allowedWidth / 100) allowedWidth - x - gap / 2 else widthPx
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
                if (mKeys.isNotEmpty()) {
                    mKeys[mKeys.size - 1].edgeFlags =
                        mKeys[mKeys.size - 1].edgeFlags or EDGE_RIGHT
                }
                this.height = y + rowHeight + scaledVerticalGap
                for (key in mKeys) {
                    if (key.column == 0) key.edgeFlags = key.edgeFlags or EDGE_LEFT
                    if (key.row == 0) key.edgeFlags = key.edgeFlags or EDGE_TOP
                    if (key.row == row) key.edgeFlags = key.edgeFlags or EDGE_BOTTOM
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to create keyboard")
            }
        }
    }

    private fun getKeyboardHeightFromTheme(theme: Theme): Int {
        var keyboardHeight = theme.generalStyle.keyboardHeight
        if (appContext.isLandscapeMode()) {
            val keyboardHeightLand = theme.generalStyle.keyboardHeightLand
            if (keyboardHeightLand > 0) keyboardHeight = keyboardHeightLand
        }
        return appContext.dp(keyboardHeight)
    }

    private fun getKeyboardHeightFromKeyboardConfig(textKeyboard: TextKeyboard): Int {
        var keyboardHeight = textKeyboard.keyboardHeight
        if (appContext.isLandscapeMode()) {
            val keyboardHeightLand = textKeyboard.keyboardHeightLand
            if (keyboardHeightLand > 0) keyboardHeight = keyboardHeightLand
        }
        return appContext.dp(keyboardHeight)
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

    private fun setModifier(
        mask: Int,
        value: Boolean,
    ): Boolean {
        if (modifier.hasFlag(mask) == value) return false
        modifier = if (value) modifier or mask else modifier and mask.inv()
        return true
    }

    val isShifted: Boolean
        get() = modifier.hasFlag(KeyEvent.META_SHIFT_ON) || mShiftKey?.isOn == true

    val isOnlyShiftOn: Boolean
        get() =
            isShifted &&
                !modifier.hasFlag(KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_SYM_ON or KeyEvent.META_META_ON)

    /**
     * 设置Shift键状态（用于自动大写）
     *
     * @param on 是否锁定Shift键
     * @param shifted 是否按下Shift键
     * @return Shift键状态是否改变
     */
    fun setShifted(
        on: Boolean,
        shifted: Boolean,
    ): Boolean {
        mShiftKey?.setOn(on)
        return setModifier(KeyEvent.META_SHIFT_ON, shifted)
    }

    /**
     * 设置修饰键的状态
     *
     * @param on 是否锁定修饰键
     * @param keycode 修饰键的 KeyEvent 掩码
     * @return 修饰键状态是否改变
     */
    fun clikModifierKey(
        on: Boolean,
        keycode: Int,
    ): Boolean {
        val keyDown = !modifier.hasFlag(keycode)
        val modifierKey =
            when (keycode) {
                KeyEvent.META_SHIFT_ON -> mShiftKey
                KeyEvent.META_ALT_ON -> mAltKey
                KeyEvent.META_CTRL_ON -> mCtrlKey
                KeyEvent.META_META_ON -> mMetaKey
                KeyEvent.KEYCODE_SYM -> mSymKey
                else -> null
            }
        val keepOn = modifierKey?.setOn(on) ?: on
        return if (on) setModifier(keycode, keepOn) else setModifier(keycode, keyDown)
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
        gridNeighbors = arrayOfNulls(GRID_SIZE)
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
                    if (key.squaredDistanceFrom(x, y) < proximityThreshold ||
                        key.squaredDistanceFrom(x + mCellWidth - 1, y) < proximityThreshold ||
                        (
                            key.squaredDistanceFrom(x + mCellWidth - 1, y + mCellHeight - 1)
                                < proximityThreshold
                        ) ||
                        key.squaredDistanceFrom(x, y + mCellHeight - 1) < proximityThreshold ||
                        key.isInside(x, y) ||
                        key.isInside(x + mCellWidth - 1, y) ||
                        key.isInside(x + mCellWidth - 1, y + mCellHeight - 1) ||
                        key.isInside(x, y + mCellHeight - 1)
                    ) {
                        indices[count++] = i
                    }
                }
                val cell = IntArray(count)
                System.arraycopy(indices, 0, cell, 0, count)
                gridNeighbors?.set(y / mCellHeight * GRID_WIDTH + x / mCellWidth, cell)
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
        if (gridNeighbors == null) computeNearestNeighbors()
        if (x in 0 until minWidth && y in 0 until height) {
            val index = y / mCellHeight * GRID_WIDTH + x / mCellWidth
            if (index < GRID_SIZE) {
                return gridNeighbors!![index]
            }
        }
        return IntArray(0)
    }

    val isLabelUppercase: Boolean
        get() = labelTransform == TextKeyboard.LabelTransform.UPPERCASE

    companion object {
        const val EDGE_LEFT = 0x01
        const val EDGE_RIGHT = 0x02
        const val EDGE_TOP = 0x04
        const val EDGE_BOTTOM = 0x08
        private const val GRID_WIDTH = 10
        private const val GRID_HEIGHT = 5
        private const val GRID_SIZE = GRID_WIDTH * GRID_HEIGHT

        /** Number of key widths from current touch point to search for nearest keys.  */
        const val SEARCH_DISTANCE = 1.4f
    }
}
