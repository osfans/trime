/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model.v2

import kotlinx.serialization.Serializable

/**
 * Key-press popup styling, in the V2 theme format. Groups the `popup_*` keys
 * that the legacy format spread across `style`.
 */
@Serializable
data class PopupStyle(
    val bottomMargin: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val keyHeight: Int = 0,
    val textSize: Float = 0f,
)
