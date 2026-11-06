/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.clip

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Owns the single [ClipDatabase] instance. Both clipboard and favorites share it,
 * which also gives the legacy migration one place to run.
 */
internal object ClipDatabaseHolder {
    private const val DATABASE_NAME = "clip.db"

    @Volatile
    private var instance: ClipDatabase? = null

    fun init(context: Context) {
        if (instance != null) return
        synchronized(this) {
            if (instance != null) return
            val database =
                Room
                    .databaseBuilder(context, ClipDatabase::class.java, DATABASE_NAME)
                    .build()
            // Old databases only exist during the first launch after the upgrade;
            // afterwards they are renamed and this becomes a cheap pair of file checks.
            runBlocking(Dispatchers.IO) {
                LegacyClipMigrator.migrate(context, database)
            }
            instance = database
        }
    }

    fun get(): ClipDatabase = instance ?: error("ClipDatabase has not been initialized yet")
}
