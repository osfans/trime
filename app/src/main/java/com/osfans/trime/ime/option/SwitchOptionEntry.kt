/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.option

import androidx.annotation.DrawableRes
import com.osfans.trime.data.schema.Schema

sealed class SwitchOptionEntry(
    val label: String,
    @param:DrawableRes
    val icon: Int,
) {
    class Custom(
        val switch: Schema.Switch,
        label: String,
        icon: Int,
    ) : SwitchOptionEntry(label, icon)

    companion object {
        fun fromSwitch(it: Schema.Switch): Custom =
            if (it.options.isEmpty()) {
                val enabledText = it.states[it.enabledIndex]
                val disabledText = it.states[1 - it.enabledIndex]
                Custom(it, "$enabledText → $disabledText", 0)
            } else {
                Custom(it, it.states[it.enabledIndex], 0)
            }
    }
}
