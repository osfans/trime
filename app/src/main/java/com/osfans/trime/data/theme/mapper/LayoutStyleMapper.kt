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
        val border = getInt("border")

        val maxWidth = getInt("max_width")

        val maxHeight = getInt("max_height")

        val minWidth = getInt("min_width")

        val minHeight = getInt("min_height")

        val marginX = getInt("margin_x")

        val marginY = getInt("margin_y")

        val lineSpacing = getInt("line_spacing")

        val lineSpacingMultiplier = getFloat("line_spacing_multiplier")

        val spacing = getInt("spacing")

        val roundCorner = getFloat("round_corner")

        val alpha = getInt("alpha", 204)

        return Layout(
            border,
            maxWidth,
            maxHeight,
            minWidth,
            minHeight,
            marginX,
            marginY,
            lineSpacing,
            lineSpacingMultiplier,
            spacing,
            roundCorner,
            alpha,
        )
    }
}
