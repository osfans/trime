/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model.v2

import kotlinx.serialization.Serializable

/**
 * Enter-key labelling, in the V2 theme format. Groups the `enter_label_mode`
 * and `enter_labels` keys that the legacy format spread across `style`.
 */
@Serializable
data class EnterKeyStyle(
    val mode: Int = 0,
    val labels: EnterLabel = EnterLabel(),
) {
    @Serializable
    data class EnterLabel(
        val go: String = "go",
        val done: String = "done",
        val next: String = "next",
        val pre: String = "pre",
        val search: String = "search",
        val send: String = "send",
        val default: String = "default",
    )
}
