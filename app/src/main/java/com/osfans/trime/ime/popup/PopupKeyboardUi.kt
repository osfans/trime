/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.osfans.trime.ime.popup

import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.ViewOutlineProvider
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.AutoScaleTextView
import com.osfans.trime.ime.keyboard.KeyboardSwitcher
import com.osfans.trime.ime.keyboard.isIconFont
import com.osfans.trime.ime.keyboard.toIconName
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.view
import splitties.views.gravityCenter
import splitties.views.gravityEnd
import splitties.views.gravityStart
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * @param ctx [Context]
 * @param theme [Theme]
 * @param outerBounds bound [Rect] of [PopupDelegate] root view.
 * @param triggerBounds bound [Rect] of popup trigger view. Used to calculate free space of both sides and
 * determine column order. See [focusColumn] and [columnOrder].
 * @param onDismissSelf callback when popup keyboard wants to close
 * @param radius popup keyboard and key radius
 * @param keyWidth key width in popup keyboard
 * @param keyHeight key height in popup keyboard
 * @param popupHeight popup preview view height. Used to transform gesture coordinate from
 * trigger view to popup keyboard view. See [offsetX] and [offsetY].
 * @param keys character to commit when triggered
 * @param labels symbols to show on keys
 */
class PopupKeyboardUi(
    override val ctx: Context,
    theme: Theme,
    outerBounds: Rect,
    triggerBounds: Rect,
    onDismissSelf: PopupContainerUi.() -> Unit = {},
    private val radius: Float,
    private val keyWidth: Int,
    private val keyHeight: Int,
    private val popupHeight: Int,
    private val keys: List<String>,
    private val labels: List<String>,
) : PopupContainerUi(ctx, theme, outerBounds, triggerBounds, onDismissSelf) {

    class PopupKeyUi(override val ctx: Context, val theme: Theme, val text: String) : Ui {

        val textView = view(::AutoScaleTextView) {
            scaleMode = AutoScaleTextView.Mode.Proportional
            textSize = theme.generalStyle.popupTextSize
            setTextColor(ColorManager.getColor("popup_text_color"))
            typeface = FontManager.getTypeface("POPUP_FONT")
        }

        val imageView = view(::AppCompatImageView) {}

        override val root = frameLayout {
            add(
                textView,
                lParams {
                    gravity = gravityCenter
                },
            )
            add(
                imageView,
                lParams {
                    gravity = gravityCenter
                },
            )
        }

        init {
            if (text.isIconFont) {
                imageView.setImageDrawable(
                    IconicsDrawable(ctx, text.toIconName()).apply {
                        sizeDp = theme.generalStyle.popupTextSize.toInt()
                        colorFilter = PorterDuffColorFilter(ColorManager.getColor("popup_text_color"), PorterDuff.Mode.SRC_IN)
                    },
                )
                imageView.isVisible = true
                textView.isVisible = false
            } else {
                textView.text = text
                textView.isVisible = true
                imageView.isVisible = false
            }
        }
    }

    private val inactiveBackground = GradientDrawable().apply {
        cornerRadius = radius
        setColor(ColorManager.getColor("popup_back_color"))
    }

    private val focusBackground = GradientDrawable().apply {
        cornerRadius = radius
        setColor(ColorManager.getColor("hilited_popup_back_color"))
    }

    private val rowCount: Int
    private val columnCount: Int

    // those 2 variables meas initial focus row/column during initialization
    private val focusRow: Int
    private val focusColumn: Int

    init {
        val keyCount: Float = keys.size.toFloat()
        rowCount = ceil(keyCount / 5).toInt()
        columnCount = (keyCount / rowCount).roundToInt()

        focusRow = 0
        focusColumn = calcInitialFocusedColumn(columnCount, keyWidth, outerBounds, triggerBounds)
    }

    /**
     * Offset on X axis made up of 2 parts:
     *  1. from trigger view bounds left to popup entry view left
     *  2. from left-most column to initial focused column
     *
     * Offset on Y axis made up of 2 parts as well:
     *  1. from trigger view top to popup entry view top
     *  2. from top-most row to initial focused row (bottom row)
     *
     * ```
     *                    c───┬───┬───┐
     *                    │   │ 4 │ 5 │
     *                 ┌─ ├───p───┼───┤ ─┐
     *   popupKeyHeight│  │ 3 │ 1 │ 2 │  │
     *                 └─ └───┼───┼───┘  │
     *                        │   │      │popupHeight
     *                 ┌───── │o─┐│      │
     *  bounds.height()│      ││a││      │
     *                 └───── └┴─┴┘ ─────┘
     * ```
     * o: trigger view top-left origin
     *
     * p: popup preview ([PopupEntryUi]) top-left origin
     *
     * c: container view top-left origin
     *
     * Applying only `1.` parts of both X and Y offset, the origin should transform from `o` to `p`.
     * `2.` parts of both offset transform it from `p` to `c`.
     */
    override val offsetX = ((triggerBounds.width() - keyWidth) / 2) - (keyWidth * focusColumn)
    override val offsetY = (triggerBounds.height() - popupHeight) - (keyHeight * (rowCount - 1))

    private val columnOrder = createColumnOrder(columnCount, focusColumn)

    /**
     * row with smaller index displays at bottom.
     * for example, keyOrders array:
     * ```
     * [[2, 0, 1, 3], [6, 4, 5, 7]]
     * ```
     * displays as
     * ```
     * | 6 | 4 | 5 | 7 |
     * | 2 | 0 | 1 | 3 |
     * ```
     * in which `0` indicates default focus
     */
    private val keyOrders = Array(rowCount) { row ->
        IntArray(columnCount) { col -> row * columnCount + columnOrder[col] }
    }

    private var focusedIndex = keyOrders[focusRow][focusColumn]

    private val keyUis = labels.map { label ->
        val displayLabel =
            if (label.length == 1 && label[0].code < 128) {
                label
            } else {
                KeyActionManager.getAction(label).getLabel(KeyboardSwitcher.currentKeyboard).let {
                    when {
                        it.isIconFont -> it
                        it.isNotEmpty() -> String(Character.toChars(it.codePointAt(0)))
                        else -> ""
                    }
                }
            }

        PopupKeyUi(ctx, theme, displayLabel)
    }

    init {
        markFocus(focusedIndex)
    }

    override val root = verticalLayout root@{
        background = inactiveBackground
        outlineProvider = ViewOutlineProvider.BACKGROUND
        elevation = dp(2f)
        // add rows in reverse order, because newly added view shows at bottom
        for (i in rowCount - 1 downTo 0) {
            val order = keyOrders[i]
            add(
                horizontalLayout row@{
                    for (j in 0 until columnCount) {
                        val keyUi = keyUis.getOrNull(order[j])
                        if (keyUi == null) {
                            // align columns to right (end) when first column is empty, eg.
                            // |   | 6 | 5 | 4 |(no free space)
                            // | 3 | 2 | 1 | 0 |(no free space)
                            gravity = if (j == 0) gravityEnd else gravityStart
                        } else {
                            add(keyUi.root, lParams(keyWidth, keyHeight))
                        }
                    }
                },
                lParams(width = matchParent),
            )
        }
    }

    private fun markFocus(index: Int) {
        keyUis.getOrNull(index)?.apply {
            root.background = focusBackground
            val color = ColorManager.getColor("hilited_popup_text_color")
            textView.setTextColor(color)
            imageView.drawable?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
    }

    private fun markInactive(index: Int) {
        keyUis.getOrNull(index)?.apply {
            root.background = null
            val color = ColorManager.getColor("popup_text_color")
            textView.setTextColor(color)
            imageView.drawable?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
    }

    override fun onChangeFocus(x: Float, y: Float): Boolean {
        // move to next row when gesture moves above 30% from bottom of current row
        var newRow = rowCount - (y / keyHeight - 0.2).roundToInt()
        // move to next column when gesture moves out of current column
        var newColumn = floor(x / keyWidth).toInt()
        // retain focus when gesture moves between ±2 rows/columns of range
        if (newRow < -2 || newRow > rowCount + 1 || newColumn < -2 || newColumn > columnCount + 1) {
            onDismissSelf(this)
            return true
        }
        newRow = limitIndex(newRow, rowCount)
        newColumn = limitIndex(newColumn, columnCount)
        val newFocus = keyOrders[newRow][newColumn]
        if (newFocus < keyUis.size) {
            markInactive(focusedIndex)
            markFocus(newFocus)
            focusedIndex = newFocus
        }
        return false
    }

    override fun onTrigger(): String? = keys.getOrNull(focusedIndex)
}
