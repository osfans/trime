package com.osfans.trime.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface DatabaseDao {
    @Insert
    fun insert(bean: DatabaseBean)

    @Update
    fun update(bean: DatabaseBean)

    @Delete
    fun delete(bean: DatabaseBean)

    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME} WHERE text=:text")
    fun delete(text: String)

    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME} WHERE id=:id")
    fun delete(id: Int)

    @Query("DELETE FROM ${DatabaseBean.TABLE_NAME}")
    fun deleteAll()

    @Query("SELECT * FROM ${DatabaseBean.TABLE_NAME}")
    fun getAll(): List<DatabaseBean>
}
