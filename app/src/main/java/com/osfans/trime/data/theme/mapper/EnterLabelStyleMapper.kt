// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme.mapper

import com.osfans.trime.data.theme.model.EnterLabel
import com.osfans.trime.util.config.ConfigItem

class EnterLabelStyleMapper(
    style: Map<String, ConfigItem?>?,
) : Mapper(style) {
    fun map(): EnterLabel {
        val go = getString("go", "go")
        val done = getString("done", "done")
        val next = getString("next", "next")
        val pre = getString("pre", "pre")
        val search = getString("search", "search")
        val send = getString("send", "send")
        val default = getString("default", "Enter")

        return EnterLabel(
            go,
            done,
            next,
            pre,
            search,
            send,
            default,
        )
    }
}
