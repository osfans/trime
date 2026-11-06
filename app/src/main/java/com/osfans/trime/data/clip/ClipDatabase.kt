/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.clip

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    version = 1,
    entities = [
        ClipboardEntity::class,
        FavoriteEntity::class,
    ],
)
abstract class ClipDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao

    abstract fun favoriteDao(): FavoriteDao
}
