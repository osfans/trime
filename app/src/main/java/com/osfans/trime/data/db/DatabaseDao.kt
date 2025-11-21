/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface DatabaseDao {
    @Insert
    suspend fun insert(bean: DatabaseBean): Long

    @Update
    suspend fun update(bean: DatabaseBean)

    @Query("UPDATE ${DatabaseBean.TABLE_NAME} SET text=:newText WHERE id=:id")
    suspend fun updateText(
        id: Int,
        newText: String,
    )

    @Query("UPDATE ${DatabaseBean.TABLE_NAME} SET pinned=:pinned WHERE id=:id")
    suspend fun updatePinned(
        id: Int,
        pinned: Boolean,
    )

    @Query("UPDATE ${DatabaseBean.TABLE_NAME} SET time=:timestamp WHERE id=:id")
    suspend fun updateTime(
        id: Int,
        timestamp: Long,
    )

    @Delete
    suspend fun delete(bean: DatabaseBean)

    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME} WHERE text=:text")
    suspend fun delete(text: String)

    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME} WHERE id=:id")
    suspend fun delete(id: Int)

    @Delete
    suspend fun delete(beans: List<DatabaseBean>)

    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME}")
    suspend fun deleteAll()

    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME} WHERE NOT pinned")
    suspend fun deleteAllUnpinned()

    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME} WHERE time<:timestamp AND pinned=0")
    suspend fun deletedUnpinnedEarlierThan(timestamp: Long)

    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME} ORDER BY pinned DESC, time DESC")
    fun allBeans(): PagingSource<Int, DatabaseBean>

    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME} WHERE id=:id LIMIT 1")
    suspend fun get(id: Int): DatabaseBean?

    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME} WHERE rowId=:rowId LIMIT 1")
    suspend fun get(rowId: Long): DatabaseBean?

    @Query("SELECT EXISTS(SELECT 1 FROM ${DatabaseBean.TABLE_NAME} WHERE pinned=0)")
    suspend fun haveUnpinned(): Boolean

    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME} WHERE pinned=0")
    suspend fun getAllUnpinned(): List<DatabaseBean>

    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME} WHERE text=:text LIMIT 1")
    suspend fun find(text: String): DatabaseBean?

    @Query("SELECT COUNT(*) FROM ${DatabaseBean.TABLE_NAME}")
    suspend fun itemCount(): Int
}
