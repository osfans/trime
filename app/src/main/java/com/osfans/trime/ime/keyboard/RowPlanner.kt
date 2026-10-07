/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

/**
 * 键盘的行布局计算：把一串键分配到各行，并处理跨行键（[KeyMetrics.rowSpan]）。
 *
 * 这里只有纯计算，不碰 Android、不碰主题对象，方便直接单测：
 * [Keyboard] 只负责把主题里的键定义翻译成 [KeyMetrics]，再把 [RowPlan] 落成 [Key]。
 *
 * 换行规则与历史上一致：可点击键数达到 [maxColumns]，或当前 x 加上键宽超过
 * [allowedWidth]，就换到下一行。跨行键额外在下方各行预留同样的 x 区间，
 * 后续行排到该区间时自动跳过，因此下一行的键不会压在跨行键身上。
 */
internal data class KeyMetrics(
    /** 键宽权重，100 表示整行宽度。 */
    val weight: Float,
    /** 键高（dp/px 原值），> 0 时作为所在行的行高候选。 */
    val height: Float,
    /** 是否可点击；不可点击的键只占位，不计入列数。 */
    val clickable: Boolean,
    /** 纵向跨越的行数，1 为不跨行。 */
    val rowSpan: Int = 1,
)

/** 单个键的放置结果，顺序与输入一致。 */
internal data class KeyPlacement(
    /** 在输入列表中的下标，便于调用方取回原始键定义。 */
    val keyIndex: Int,
    val row: Int,
    val column: Int,
    val x: Int,
    val widthPx: Int,
    val weight: Float,
    val clickable: Boolean,
    val rowSpan: Int,
)

/** 一整块键盘的行布局结果。 */
internal class RowPlan(
    val placements: List<KeyPlacement>,
    /** 每行缩放前的原始高度。 */
    val rowRawHeight: List<Int>,
    /** 每行的权重之和（分屏中缝按行中点插入时用到）。 */
    val rowWidthTotalWeight: List<Float>,
) {
    val rowCount: Int get() = rowRawHeight.size
}

/**
 * 跨 [rowSpan] 行的键高：所跨各行缩放后高度之和。
 *
 * 跨行超出末行时会被收敛到末行，避免主题写错 `rowSpan` 时算出超高的键。
 */
internal fun spanHeight(
    row: Int,
    rowSpan: Int,
    rowHeightScaled: List<Int>,
): Int {
    if (rowHeightScaled.isEmpty()) return 0
    val first = row.coerceIn(0, rowHeightScaled.lastIndex)
    val last = (row + rowSpan - 1).coerceAtMost(rowHeightScaled.lastIndex).coerceAtLeast(first)
    return (first..last).sumOf { rowHeightScaled[it] }
}

/**
 * 把 [keys] 分配到各行。
 *
 * @param allowedWidth 键盘可用宽度（px）
 * @param widthUnitPx 一个权重对应的摆放宽度（px，已含分屏中缝）
 * @param wrapWidthUnitPx 一个权重对应的换行判定宽度（px，未含分屏中缝）
 * @param maxColumns 每行最多可点击键数，`Int.MAX_VALUE` 表示不限
 * @param defaultRowHeight 键未声明高度时的默认行高
 */
internal fun planKeyRows(
    keys: List<KeyMetrics>,
    allowedWidth: Int,
    widthUnitPx: Float,
    wrapWidthUnitPx: Float,
    maxColumns: Int,
    defaultRowHeight: Int,
): RowPlan {
    val placements = mutableListOf<KeyPlacement>()
    val rowRawHeight = mutableListOf<Int>()
    val rowWidthTotalWeight = mutableListOf<Float>()
    // 行号 -> 该行被跨行键占据的 x 区间
    val reserved = mutableMapOf<Int, MutableList<IntRange>>()

    var x = 0
    var column = 0
    var row = 0
    var rowHeight = defaultRowHeight
    var totalKeyWidth = 0f

    fun commitRow() {
        rowRawHeight.add(rowHeight)
        rowWidthTotalWeight.add(totalKeyWidth)
        row++
        x = 0
        column = 0
        totalKeyWidth = 0f
        rowHeight = defaultRowHeight
    }

    // 跳过当前行已被跨行键占据的区间
    fun skipReserved() {
        while (true) {
            val hit = reserved[row]?.firstOrNull { x >= it.first && x <= it.last } ?: break
            x = hit.last + 1
        }
    }

    keys.forEachIndexed { index, key ->
        val widthPx = (key.weight * widthUnitPx).toInt()
        val wrapWidthPx = (key.weight * wrapWidthUnitPx).toInt()

        skipReserved()
        if (column >= maxColumns || x + wrapWidthPx > allowedWidth) {
            commitRow()
            skipReserved()
        }

        // first key of a row defines the row height
        if (column == 0) rowHeight = if (key.height > 0) key.height.toInt() else defaultRowHeight

        totalKeyWidth += key.weight

        val rowSpan = if (key.clickable) key.rowSpan.coerceAtLeast(1) else 1

        placements.add(
            KeyPlacement(
                keyIndex = index,
                row = row,
                column = column,
                x = x,
                widthPx = widthPx,
                weight = key.weight,
                clickable = key.clickable,
                rowSpan = rowSpan,
            ),
        )

        if (rowSpan > 1) {
            for (r in row + 1..row + rowSpan - 1) {
                reserved.getOrPut(r) { mutableListOf() }.add(x until x + widthPx)
            }
        }

        if (key.clickable) column++

        x += widthPx
    }

    rowRawHeight.add(rowHeight)
    rowWidthTotalWeight.add(totalKeyWidth)

    return RowPlan(placements, rowRawHeight, rowWidthTotalWeight)
}
