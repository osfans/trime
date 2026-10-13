/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core.data

import kotlinx.serialization.Serializable

@Serializable
data class SyncManifest(
    val sha256: String,
    val files: Map<String, Entry>,
) {
    @Serializable
    data class Entry(
        val localMtime: Long,
        val remoteMtime: Long,
        val length: Long,
        val isDir: Boolean,
        val hash: String? = null,
    )
}
