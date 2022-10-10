package com.osfans.trime.data.db

import android.content.Context
import androidx.room.Room

object CollectionHelper {
    private lateinit var cltDb: Database
    private lateinit var cltDao: DatabaseDao

    fun init(context: Context) {
        cltDb = Room
                .databaseBuilder(context, Database::class.java, "collection")
                .build()
        cltDao = cltDb.databaseDao()
    }

    fun insert(bean: DatabaseBean) = cltDao.insert(bean)

    fun getAll() = cltDao.getAll()

    fun delete(id: Int) = cltDao.delete(id)

    fun deleteAll() = cltDao.deleteAll()
}