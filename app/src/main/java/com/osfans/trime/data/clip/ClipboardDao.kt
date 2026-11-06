/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.clip

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ClipboardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ClipboardEntity): Long

    @Query("UPDATE ${ClipboardEntity.TABLE_NAME} SET text=:text WHERE id=:id")
    suspend fun updateText(
        id: Int,
        text: String,
    )

    @Query("UPDATE ${ClipboardEntity.TABLE_NAME} SET pinned=:pinned WHERE id=:id")
    suspend fun updatePinned(
        id: Int,
        pinned: Boolean,
    )

    @Query("UPDATE ${ClipboardEntity.TABLE_NAME} SET timestamp=:timestamp WHERE id=:id")
    suspend fun updateTimestamp(
        id: Int,
        timestamp: Long,
    )

    @Query("DELETE FROM ${ClipboardEntity.TABLE_NAME} WHERE id=:id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM ${ClipboardEntity.TABLE_NAME}")
    suspend fun deleteAll()

    @Query("DELETE FROM ${ClipboardEntity.TABLE_NAME} WHERE NOT pinned")
    suspend fun deleteAllUnpinned()

    @Query("DELETE FROM ${ClipboardEntity.TABLE_NAME} WHERE timestamp<:timestamp AND pinned=0")
    suspend fun deleteUnpinnedEarlierThan(timestamp: Long)

    @Query("SELECT * FROM ${ClipboardEntity.TABLE_NAME} ORDER BY pinned DESC, timestamp DESC")
    fun all(): PagingSource<Int, ClipboardEntity>

    @Query("SELECT * FROM ${ClipboardEntity.TABLE_NAME} WHERE id=:id LIMIT 1")
    suspend fun get(id: Int): ClipboardEntity?

    @Query("SELECT * FROM ${ClipboardEntity.TABLE_NAME} WHERE id=:rowId LIMIT 1")
    suspend fun get(rowId: Long): ClipboardEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM ${ClipboardEntity.TABLE_NAME} WHERE pinned=0)")
    suspend fun haveUnpinned(): Boolean

    @Query("SELECT * FROM ${ClipboardEntity.TABLE_NAME} WHERE pinned=0")
    suspend fun getAllUnpinned(): List<ClipboardEntity>

    @Query("SELECT * FROM ${ClipboardEntity.TABLE_NAME} WHERE text=:text AND sensitive=:sensitive LIMIT 1")
    suspend fun find(text: String, sensitive: Boolean = false): ClipboardEntity?

    @Query("SELECT COUNT(*) FROM ${ClipboardEntity.TABLE_NAME}")
    suspend fun itemCount(): Int
}
