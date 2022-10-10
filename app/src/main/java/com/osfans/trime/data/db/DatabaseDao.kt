package com.osfans.trime.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface DatabaseDao {
    @Insert
    fun insert(bean: DatabaseBean): Long

    @Update
    fun update(bean: DatabaseBean)

    @Query("UPDATE ${DatabaseBean.TABLE_NAME} SET text=:newText WHERE id=:id")
    fun updateText(id: Int, newText: String)

    @Query("UPDATE ${DatabaseBean.TABLE_NAME} SET pinned=:pinned WHERE id=:id")
    fun updatePinned(id: Int, pinned: Boolean)

    @Delete
    fun delete(bean: DatabaseBean)

    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME} WHERE text=:text")
    fun delete(text: String)

    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME} WHERE id=:id")
    fun delete(id: Int)

    @Delete
    fun delete(beans: List<DatabaseBean>)

    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME}")
    fun deleteAll()

    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME} WHERE NOT pinned")
    fun deleteAllUnpinned()

    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME}")
    fun getAll(): List<DatabaseBean>

    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME} WHERE id=:id LIMIT 1")
    fun get(id: Int): DatabaseBean?

    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME} WHERE rowId=:rowId LIMIT 1")
    fun get(rowId: Long): DatabaseBean?

    @Query("SELECT COUNT(*) FROM ${DatabaseBean.TABLE_NAME}")
    fun itemCount(): Int
}
