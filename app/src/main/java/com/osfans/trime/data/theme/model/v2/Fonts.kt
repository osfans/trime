/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model.v2

import com.osfans.trime.data.theme.model.MaybeStringList
import kotlinx.serialization.Serializable

/**
 * Font face declarations, in the V2 theme format. Groups the `*_font` keys that
 * the legacy format spread across `style`.
 */
@Serializable
data class Fonts(
    val candidate: MaybeStringList = MaybeStringList.Empty,
    val comment: MaybeStringList = MaybeStringList.Empty,
    val hanb: MaybeStringList = MaybeStringList.Empty,
    val key: MaybeStringList = MaybeStringList.Empty,
    val label: MaybeStringList = MaybeStringList.Empty,
    val latin: MaybeStringList = MaybeStringList.Empty,
    val popup: MaybeStringList = MaybeStringList.Empty,
    val symbol: MaybeStringList = MaybeStringList.Empty,
    val text: MaybeStringList = MaybeStringList.Empty,
)
