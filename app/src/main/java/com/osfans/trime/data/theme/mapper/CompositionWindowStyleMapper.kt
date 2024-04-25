// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme.mapper

import com.osfans.trime.data.theme.model.CompositionComponent
import com.osfans.trime.util.config.ConfigItem

class CompositionWindowStyleMapper(
    style: Map<String, ConfigItem?>?,
) : Mapper(style) {
    fun map(): CompositionComponent {
        val start = getString("start")
        val move = getString("move")
        val end = getString("end")
        val composition = getString("composition")
        val letterSpacing = getInt("letter_spacing")
        val label = getString("label")
        val candidate = getString("candidate")
        val comment = getString("comment")
        val sep = getString("sep")
        val align = getString("align")
        val whenStr = getString("when")
        val click = getString("click")

        return CompositionComponent(
            start,
            move,
            end,
            composition,
            letterSpacing,
            label,
            candidate,
            comment,
            sep,
            align,
            whenStr,
            click,
        )
    }
}
