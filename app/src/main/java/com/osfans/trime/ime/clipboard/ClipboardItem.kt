/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import com.osfans.trime.data.clip.ClipboardEntity
import com.osfans.trime.data.clip.FavoriteEntity

/**
 * Read-only row model shared by the clipboard and favorite pages.
 * Favorites never show the pin action, hence [pinned] is always false for them.
 */
data class ClipboardItem(
    val id: Int,
    val text: String,
    val pinned: Boolean,
    val mask: Boolean,
)

fun ClipboardEntity.toClipboardItem() = ClipboardItem(id = id, text = text, pinned = pinned, mask = sensitive)

fun FavoriteEntity.toClipboardItem() = ClipboardItem(id = id, text = text, pinned = false, mask = false)
