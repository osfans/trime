/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.clip

import android.content.ClipData
import android.content.ClipDescription
import android.os.Build
import android.os.PersistableBundle
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = ClipboardEntity.TABLE_NAME)
data class ClipboardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val text: String,
    val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "-1")
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = ClipDescription.MIMETYPE_TEXT_PLAIN)
    val type: String = ClipDescription.MIMETYPE_TEXT_PLAIN,
    @ColumnInfo(defaultValue = "0")
    val sensitive: Boolean = false,
) {
    companion object {
        const val BULLET = "•"

        const val TABLE_NAME = "clipboard"

        internal val IS_SENSITIVE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            "android.content.extra.IS_SENSITIVE"
        }

        fun fromClipData(clipData: ClipData): ClipboardEntity? {
            val desc = clipData.description
            val text = clipData.getItemAt(0).text?.toString() ?: return null
            val type = desc.getMimeType(0)
            val sensitive = desc.extras?.getBoolean(IS_SENSITIVE) ?: false
            return ClipboardEntity(text = text, type = type, sensitive = sensitive)
        }
    }
}

/**
 * Keeps the sensitive flag when a clip is put back on the system clipboard,
 * otherwise the history would treat the copy as a new, unmasked row.
 */
internal fun ClipData.markSensitive(): ClipData {
    description.extras = PersistableBundle().apply {
        putBoolean(ClipboardEntity.IS_SENSITIVE, true)
    }
    return this
}
