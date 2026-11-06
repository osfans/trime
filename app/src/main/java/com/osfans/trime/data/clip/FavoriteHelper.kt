/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.clip

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

object FavoriteHelper : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {
    private val database: ClipDatabase get() = ClipDatabaseHolder.get()
    private val dao: FavoriteDao get() = database.favoriteDao()

    private val mutex = Mutex()

    private var lastEntity: FavoriteEntity? = null

    fun init(context: Context) {
        ClipDatabaseHolder.init(context)
    }

    suspend fun get(id: Int) = dao.get(id)

    fun allEntities() = dao.all()

    suspend fun delete(id: Int) = dao.delete(id)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun updateText(
        id: Int,
        text: String,
    ) {
        lastEntity?.let {
            if (id == it.id) lastEntity = it.copy(text = text)
        }
        dao.updateText(id, text)
    }

    /**
     * Stores [text] as a favorite.
     *
     * Favorites keep plain text only, so callers must never pass the text of a
     * sensitive clip here; hide the action instead (see `ClipboardAdapter` and
     * `SegmentsWindow`).
     */
    fun addNewEntity(text: String) {
        launch {
            mutex.withLock {
                if (text.isBlank()) return@withLock
                val timestamp = System.currentTimeMillis()
                try {
                    dao.find(text)?.let {
                        lastEntity = it.copy(timestamp = timestamp)
                        dao.updateTimestamp(it.id, timestamp)
                        return@withLock
                    }
                    val entity = FavoriteEntity(text = text)
                    val rowId = dao.insert(entity)
                    lastEntity = dao.get(rowId) ?: entity
                } catch (e: Exception) {
                    Timber.w("Failed to update favorite database: $e")
                    lastEntity = FavoriteEntity(text = text, timestamp = timestamp)
                }
            }
        }
    }
}
