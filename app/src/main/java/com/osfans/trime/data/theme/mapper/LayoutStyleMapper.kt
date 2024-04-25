// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme.mapper

import com.osfans.trime.data.theme.model.Layout
import com.osfans.trime.util.config.ConfigItem

class LayoutStyleMapper(
    style: Map<String, ConfigItem?>?,
) : Mapper(style) {
    fun map(): Layout {
        val position = getString("position")

        val minLength = getInt("min_length")

        val maxLength = getInt("max_length")

        val stickyLines = getInt("sticky_lines")

        val stickyLinesLand = getInt("sticky_lines_land")

        val maxEntries = getInt("max_entries")

        val minCheck = getInt("min_check")

        val allPhrases = getBoolean("all_phrases")

        val border = getInt("border")

        val maxWidth = getInt("max_width")

        val maxHeight = getInt("max_height")

        val minWidth = getInt("min_width")

        val minHeight = getInt("min_height")

        val marginX = getInt("margin_x")

        val marginY = getInt("margin_y")

        val marginBottom = getInt("margin_bottom")

        val lineSpacing = getInt("line_spacing")

        val lineSpacingMultiplier = getFloat("line_spacing_multiplier")

        val realMargin = getInt("real_margin")

        val spacing = getInt("spacing")

        val roundCorner = getInt("round_corner")

        val alpha = getInt("alpha")
        val elevation = getInt("elevation")
        val movable = getString("movable")

        return Layout(
            position,
            minLength,
            maxLength,
            stickyLines,
            stickyLinesLand,
            maxEntries,
            minCheck,
            allPhrases,
            border,
            maxWidth,
            maxHeight,
            minWidth,
            minHeight,
            marginX,
            marginY,
            marginBottom,
            lineSpacing,
            lineSpacingMultiplier,
            realMargin,
            spacing,
            roundCorner,
            alpha,
            elevation,
            movable,
        )
    }
}
