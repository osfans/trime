// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.db

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

object CollectionHelper : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {
    private lateinit var cltDb: Database
    private lateinit var cltDao: DatabaseDao

    private val mutex = Mutex()

    var lastBean: DatabaseBean? = null

    fun init(context: Context) {
        cltDb =
            Room
                .databaseBuilder(context, Database::class.java, "collection.db")
                .addMigrations(Database.MIGRATION_3_4)
                .build()
        cltDao = cltDb.databaseDao()
    }

    suspend fun insert(bean: DatabaseBean) {
        mutex.withLock {
            cltDao.find(bean.text!!)?.let {
                Timber.d("Update existing collection item: $it")
                lastBean = it.copy(time = bean.time)
                cltDao.updateTime(it.id, bean.time)
                return
            }
            Timber.d("Insert new collection item: $bean")
            val rowId = cltDao.insert(bean)
            cltDao.get(rowId)?.let { lastBean = it }
        }
    }

    suspend fun haveUnpinned() = cltDao.haveUnpinned()

    suspend fun getAll() = cltDao.getAll()

    suspend fun pin(id: Int) = cltDao.updatePinned(id, true)

    suspend fun unpin(id: Int) = cltDao.updatePinned(id, false)

    suspend fun delete(id: Int) = cltDao.delete(id)

    suspend fun deleteAll(skipPinned: Boolean = true) {
        if (skipPinned) {
            cltDao.deleteAllUnpinned()
        } else {
            cltDao.deleteAll()
        }
    }

    suspend fun updateText(
        id: Int,
        text: String,
    ) {
        lastBean?.let {
            if (id == it.id) lastBean = it.copy(text = text)
        }
        cltDao.updateText(id, text)
    }
}
