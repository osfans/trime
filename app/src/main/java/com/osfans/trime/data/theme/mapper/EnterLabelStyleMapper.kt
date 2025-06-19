// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme.mapper

import com.osfans.trime.core.RimeConfig
import com.osfans.trime.data.theme.model.GeneralStyle

class EnterLabelStyleMapper(
    prefix: String,
    config: RimeConfig,
) : Mapper(prefix, config) {
    fun map() =
        GeneralStyle.EnterLabel(
            go = getString("go", "go"),
            done = getString("done", "done"),
            next = getString("next", "next"),
            pre = getString("pre", "pre"),
            search = getString("search", "search"),
            send = getString("send", "send"),
            default = getString("default", "Enter"),
        )
}
