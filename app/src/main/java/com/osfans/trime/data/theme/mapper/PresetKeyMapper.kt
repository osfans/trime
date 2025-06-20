/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.mapper

import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.util.config.Config

class PresetKeyMapper(
    prefix: String,
    config: Config,
) : Mapper<PresetKey>(prefix, config) {
    override fun map() =
        PresetKey(
            command = getString("command"),
            option = getString("option"),
            select = getString("select"),
            toggle = getString("toggle"),
            label = getString("label"),
            preview = getString("preview"),
            shiftLock = getString("shift_lock"),
            commit = getString("commit"),
            text = getString("text"),
            sticky = getBoolean("sticky"),
            repeatable = getBoolean("repeatable"),
            functional = getBoolean("functional"),
            states = getStringList("states"),
            send = getString("send"),
        )
}
