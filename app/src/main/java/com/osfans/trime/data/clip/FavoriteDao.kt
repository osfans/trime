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
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: FavoriteEntity): Long

    @Query("UPDATE ${FavoriteEntity.TABLE_NAME} SET text=:text WHERE id=:id")
    suspend fun updateText(
        id: Int,
        text: String,
    )

    @Query("UPDATE ${FavoriteEntity.TABLE_NAME} SET timestamp=:timestamp WHERE id=:id")
    suspend fun updateTimestamp(
        id: Int,
        timestamp: Long,
    )

    @Query("DELETE FROM ${FavoriteEntity.TABLE_NAME} WHERE id=:id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM ${FavoriteEntity.TABLE_NAME}")
    suspend fun deleteAll()

    @Query("SELECT * FROM ${FavoriteEntity.TABLE_NAME} ORDER BY timestamp DESC")
    fun all(): PagingSource<Int, FavoriteEntity>

    @Query("SELECT * FROM ${FavoriteEntity.TABLE_NAME} WHERE id=:id LIMIT 1")
    suspend fun get(id: Int): FavoriteEntity?

    @Query("SELECT * FROM ${FavoriteEntity.TABLE_NAME} WHERE id=:rowId LIMIT 1")
    suspend fun get(rowId: Long): FavoriteEntity?

    @Query("SELECT * FROM ${FavoriteEntity.TABLE_NAME} WHERE text=:text LIMIT 1")
    suspend fun find(text: String): FavoriteEntity?
}
