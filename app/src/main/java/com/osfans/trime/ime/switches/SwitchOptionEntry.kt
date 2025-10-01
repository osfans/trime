/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.switches

import androidx.annotation.DrawableRes
import com.osfans.trime.core.RimeSchema
import com.osfans.trime.daemon.RimeSession

sealed class SwitchOptionEntry(
    val label: String,
    @param:DrawableRes
    val icon: Int,
) {
    class Custom(
        val switch: RimeSchema.Switch,
        label: String,
        icon: Int,
    ) : SwitchOptionEntry(label, icon)

    companion object {
        fun fromSwitch(rime: RimeSession, it: RimeSchema.Switch): Custom? {
            val labels = it.states
            if (labels.size <= 1) return null
            return if (it.name.isNotEmpty()) {
                if (labels.size != 2) return null
                val (disabledText, enabledText) = labels
                val value = rime.run { getRuntimeOption(it.name) }
                val label = if (value) "$enabledText → $disabledText" else "$disabledText → $enabledText"
                Custom(it, label, 0)
            } else {
                val options = it.options
                if (options.size != labels.size) return null
                val index = options.indexOfFirst { rime.run { getRuntimeOption(it) } }
                val label = labels[if (index >= 0) index else 0]
                Custom(it, label, 0)
            }
        }
    }
}
