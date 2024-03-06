package com.osfans.trime.ime.keyboard

import com.osfans.trime.util.CollectionUtils.obtainFloat
import com.osfans.trime.util.appContext
import com.osfans.trime.util.sp
import kotlin.math.abs

class KeyboardSizeCalculator(
    val name: String,
    isSplit: Boolean,
    splitPercent: Int,
    private val maxColumns: Int,
    private val mAllowedWidth: Int,
    private val keyboardHeight: Int,
    private val keyboardKeyWidth: Float,
    private val keyHeight: Int,
    private val mDefaultHorizontalGap: Int,
    private val mDefaultVerticalGap: Int,
    private val autoHeightIndex: Int,
) {
    private val splitSpaceRatio: Float = if (isSplit) (splitPercent / 100f) else 0f

    fun calc(lm: List<Map<String, Any>>): KeyboardSize {
        var x = mDefaultHorizontalGap / 2
        var y = 0
        var column = 0
        var row = 0

        var rowHeight = keyHeight

        var rawSumHeight = 0
        val rawHeight = ArrayList<Int>()

        var totalKeyWidth = 0f
        var maxColumn = 0
        val rowTotalWeight = HashMap<Int, Float>()

        for (mk in lm) {
            val weight = obtainFloat(mk, "width", 0f)
            val keyWidthWeight =
                if (weight == 0f && mk.contains("click")) {
                    keyboardKeyWidth
                } else {
                    weight
                }
            val widthPx =
                (keyWidthWeight * mAllowedWidth / MAX_TOTAL_WEIGHT).toInt() - mDefaultHorizontalGap
            if (column >= maxColumns || x + widthPx > mAllowedWidth) {
                maxColumn = maxOf(maxColumn, column)
                rowTotalWeight[row] = totalKeyWidth

                x = mDefaultHorizontalGap / 2
                y += mDefaultVerticalGap + rowHeight
                totalKeyWidth = 0f
                column = 0
                row++
                rawSumHeight += rowHeight
                rawHeight.add(rowHeight)
            }

            if (column == 0) {
                val heightK = appContext.sp(obtainFloat(mk, "height", 0f)).toInt()
                rowHeight = if (heightK > 0) heightK else keyHeight
            }
            totalKeyWidth += keyWidthWeight
            if (!mk.containsKey("click")) { // 無按鍵事件
                x += widthPx + mDefaultHorizontalGap
                continue // 縮進
            }
            column++
            val rightGap = abs(mAllowedWidth - x - widthPx - mDefaultHorizontalGap / 2)
            x += (
                if (rightGap <= mAllowedWidth / MAX_TOTAL_WEIGHT) {
                    mAllowedWidth - x -
                        mDefaultHorizontalGap / 2
                } else {
                    widthPx
                }
            ) + mDefaultHorizontalGap
        }
        rowTotalWeight[row] = totalKeyWidth

        rawSumHeight += rowHeight
        rawHeight.add(rowHeight)

        return KeyboardSize(
            rowTotalWeight,
            calculateOneWeightWidthPx(),
            splitSpaceRatio,
            calculateAdjustedHeight(rawSumHeight, rawHeight, autoHeightIndex),
        )
    }

    private fun calculateOneWeightWidthPx(): Float {
        return (mAllowedWidth / (MAX_TOTAL_WEIGHT * (1 + splitSpaceRatio)))
    }

    private fun calculateAdjustedHeight(
        rawSumHeight: Int,
        rawHeight: List<Int>,
        autoHeightIndex: Int,
    ): List<Int> {
        var scale: Float =
            keyboardHeight.toFloat() / (rawSumHeight + mDefaultVerticalGap * (rawHeight.size + 1))

        val verticalGap = Math.ceil((mDefaultVerticalGap * scale).toDouble()).toInt()

        var autoHeight: Int = keyboardHeight - verticalGap * (rawHeight.size + 1)

        scale = (autoHeight.toFloat()) / rawSumHeight

        var finalAutoHeightIndex = -100
        if (autoHeightIndex < 0) {
            finalAutoHeightIndex = rawHeight.size + autoHeightIndex
            if (finalAutoHeightIndex < 0) finalAutoHeightIndex = 0
        } else if (autoHeightIndex >= rawHeight.size) {
            finalAutoHeightIndex = rawHeight.size - 1
        }

        val newHeight = rawHeight.toMutableList()
        for (i in rawHeight.indices) {
            if (i != finalAutoHeightIndex) {
                val h: Int = (rawHeight[i] * scale).toInt()
                newHeight[i] = h
                autoHeight -= h
            }
        }
        if (autoHeight < 1) {
            if (rawHeight[autoHeight] > 0) autoHeight = 1
        }
        newHeight[finalAutoHeightIndex] = autoHeight

        return newHeight
    }

    companion object {
        private const val MAX_TOTAL_WEIGHT = 100
    }
}
