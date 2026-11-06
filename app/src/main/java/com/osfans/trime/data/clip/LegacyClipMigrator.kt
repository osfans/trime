/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.clip

import android.content.ClipDescription
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import timber.log.Timber
import java.io.File

/**
 * Moves the rows of the legacy `t_data` tables from `clipboard.db` and `collection.db`
 * into the new `clip.db`, then retires the old files.
 *
 * The legacy databases are opened through [SQLiteDatabase] instead of Room on purpose:
 * Room would run the old 3 -> 4 migration and validate the old schema, while all we need
 * is a plain read. Reading by column name keeps every legacy version working, including
 * the ones that predate the `pinned` column.
 */
internal object LegacyClipMigrator {
    private const val LEGACY_TABLE = "t_data"
    private const val LEGACY_CLIPBOARD = "clipboard.db"
    private const val LEGACY_COLLECTION = "collection.db"
    private const val LEGACY_SUFFIX = ".legacy"
    private val SIDECARS = listOf("", "-wal", "-shm")

    private data class LegacyRow(
        val id: Int,
        val text: String,
        val pinned: Boolean,
        val timestamp: Long,
        val html: Boolean,
    )

    suspend fun migrate(
        context: Context,
        database: ClipDatabase,
    ) {
        val dir = context.getDatabasePath(LEGACY_CLIPBOARD).parentFile ?: return
        val legacyClipboard = File(dir, LEGACY_CLIPBOARD)
        val legacyCollection = File(dir, LEGACY_COLLECTION)
        if (!legacyClipboard.exists() && !legacyCollection.exists()) return

        // A failed read keeps its file for the next launch; only successful ones are retired.
        val clips = read(legacyClipboard)
        val favorites = read(legacyCollection)

        if (clips.orEmpty().isNotEmpty() || favorites.orEmpty().isNotEmpty()) {
            database.withTransaction {
                clips.orEmpty().forEach { row ->
                    database.clipboardDao().insert(
                        ClipboardEntity(
                            id = row.id,
                            text = row.text,
                            pinned = row.pinned,
                            timestamp = row.timestamp,
                            type = if (row.html) "text/html" else ClipDescription.MIMETYPE_TEXT_PLAIN,
                        ),
                    )
                }
                favorites.orEmpty().forEach { row ->
                    database.favoriteDao().insert(
                        FavoriteEntity(
                            id = row.id,
                            text = row.text,
                            timestamp = row.timestamp,
                        ),
                    )
                }
            }
        }

        Timber.i(
            "Migrated %d clipboard rows and %d favorites",
            clips.orEmpty().size,
            favorites.orEmpty().size,
        )
        if (clips != null) retire(legacyClipboard)
        if (favorites != null) retire(legacyCollection)
    }

    /** Returns null when the file exists but cannot be read. */
    private fun read(file: File): List<LegacyRow>? {
        if (!file.exists()) return emptyList()
        return try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { legacy ->
                if (!legacy.hasTable(LEGACY_TABLE)) return emptyList()
                legacy.rawQuery("SELECT * FROM $LEGACY_TABLE", null).use { cursor ->
                    val idColumn = cursor.getColumnIndex("id")
                    val textColumn = cursor.getColumnIndex("text")
                    if (idColumn < 0 || textColumn < 0) return emptyList()
                    val timeColumn = cursor.getColumnIndex("time")
                    val pinnedColumn = cursor.getColumnIndex("pinned")
                    val typeColumn = cursor.getColumnIndex("type")
                    buildList {
                        while (cursor.moveToNext()) {
                            val text = cursor.getString(textColumn)
                            if (text.isNullOrBlank()) continue
                            add(
                                LegacyRow(
                                    id = cursor.getInt(idColumn),
                                    text = text,
                                    // `pinned` did not exist before version 4.
                                    pinned = pinnedColumn >= 0 && cursor.getInt(pinnedColumn) != 0,
                                    timestamp = if (timeColumn >= 0) cursor.getLong(timeColumn) else System.currentTimeMillis(),
                                    html = typeColumn >= 0 && cursor.getInt(typeColumn) == 1,
                                ),
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to read ${file.name}, keeping it for the next launch")
            null
        }
    }

    private fun SQLiteDatabase.hasTable(name: String): Boolean = rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name))
        .use { it.moveToFirst() }

    private fun retire(file: File) {
        SIDECARS.forEach { suffix ->
            val target = File(file.path + suffix)
            if (target.exists() && !target.renameTo(File(target.path + LEGACY_SUFFIX))) {
                Timber.w("Failed to retire ${target.name}")
            }
        }
    }
}
