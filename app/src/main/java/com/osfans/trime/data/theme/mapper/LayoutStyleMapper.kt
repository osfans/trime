// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme.mapper

import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.util.config.Config

class LayoutStyleMapper(
    prefix: String,
    config: Config,
) : Mapper<GeneralStyle.Layout>(prefix, config) {
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
