// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme.mapper

import com.charleskorn.kaml.YamlMap
import com.osfans.trime.data.theme.model.GeneralStyle

class LayoutStyleMapper(
    node: YamlMap,
) : Mapper<GeneralStyle.Layout>(node) {
    override fun map() =
        GeneralStyle.Layout(
            border = getInt("border"),
            maxWidth = getInt("max_width"),
            maxHeight = getInt("max_height"),
            minWidth = getInt("min_width"),
            minHeight = getInt("min_height"),
            marginX = getInt("margin_x"),
            marginY = getInt("margin_y"),
            lineSpacing = getInt("line_spacing"),
            lineSpacingMultiplier = getFloat("line_spacing_multiplier"),
            spacing = getInt("spacing"),
            roundCorner = getFloat("round_corner"),
            alpha = getInt("alpha", 204),
        )
}
