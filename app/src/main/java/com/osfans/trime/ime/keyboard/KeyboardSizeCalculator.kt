// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.util.appContext
import com.osfans.trime.util.sp
import kotlin.math.abs
import kotlin.math.ceil

class KeyboardSizeCalculator(
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

    fun calc(keys: List<TextKeyboard.TextKey>): KeyboardSize {
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

        for (key in keys) {
            val keyWidthWeight =
                if (key.width == 0f && key.click.isNotEmpty()) {
                    keyboardKeyWidth
                } else {
                    key.width
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
                rowHeight = if (key.height > 0) appContext.sp(key.height).toInt() else keyHeight
            }
            totalKeyWidth += keyWidthWeight
            if (key.click.isEmpty()) { // 無按鍵事件
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

        val scaledVerticalGap = calculateScaledVerticalGap(rawSumHeight, rawHeight)
        return KeyboardSize(
            rowTotalWeight,
            calculateOneWeightWidthPx(),
            splitSpaceRatio,
            calculateAdjustedHeight(rawSumHeight, rawHeight, scaledVerticalGap),
            scaledVerticalGap.toInt(),
        )
    }

    private fun calculateOneWeightWidthPx(): Float = (mAllowedWidth / (MAX_TOTAL_WEIGHT * (1 + splitSpaceRatio)))

    private fun calculateScaledVerticalGap(
        rawSumHeight: Int,
        rawHeight: List<Int>,
    ): Double {
        val scale: Double =
            keyboardHeight.toDouble() / (rawSumHeight + mDefaultVerticalGap * (rawHeight.size + 1))

        return ceil((mDefaultVerticalGap * scale))
    }

    private fun calculateAdjustedHeight(
        rawSumHeight: Int,
        rawHeight: List<Int>,
        scaledVerticalGap: Double,
    ): List<Int> {
        var remainHeight = keyboardHeight - scaledVerticalGap * (rawHeight.size + 1)

        val scale = remainHeight / rawSumHeight

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
                remainHeight -= h
            }
        }
        if (remainHeight < 1) {
            if (rawHeight[finalAutoHeightIndex] > 0) remainHeight = 1.0
        }
        newHeight[finalAutoHeightIndex] = remainHeight.toInt()

        return newHeight
    }

    companion object {
        private const val MAX_TOTAL_WEIGHT = 100
    }
}
