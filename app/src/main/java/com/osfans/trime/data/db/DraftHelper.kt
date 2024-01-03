package com.osfans.trime.data.db

import android.content.Context
import androidx.room.Room
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.util.StringUtils.matches
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

object DraftHelper : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {
    private lateinit var dftDb: Database
    private lateinit var dftDao: DatabaseDao

    private val mutex = Mutex()

    var itemCount: Int = 0
        private set

    private suspend fun updateItemCount() {
        itemCount = dftDao.itemCount()
    }

    private val limit get() = AppPrefs.defaultInstance().clipboard.draftLimit
    private val output get() = AppPrefs.defaultInstance().clipboard.draftOutputRules

    var lastBean: DatabaseBean? = null

    fun init(context: Context) {
        dftDb =
            Room
                .databaseBuilder(context, Database::class.java, "draft.db")
                .addMigrations(Database.MIGRATION_3_4)
                .build()
        dftDao = dftDb.databaseDao()
        launch { updateItemCount() }
    }

    suspend fun get(id: Int) = dftDao.get(id)

    suspend fun getAll() = dftDao.getAll()

    suspend fun pin(id: Int) = dftDao.updatePinned(id, true)

    suspend fun unpin(id: Int) = dftDao.updatePinned(id, false)

    suspend fun updateText(
        id: Int,
        text: String,
    ) = dftDao.updateText(id, text)

    suspend fun delete(id: Int) {
        dftDao.delete(id)
        updateItemCount()
    }

    suspend fun deleteAll(skipUnpinned: Boolean = true) {
        if (skipUnpinned) {
            dftDao.deleteAllUnpinned()
        } else {
            dftDao.deleteAll()
        }
    }

    fun onInputEventChanged() {
        if (!(limit != 0 && this::dftDao.isInitialized)) return

        Trime.getService()
            .currentInputConnection
            ?.let { DatabaseBean.fromInputConnection(it) }
            ?.takeIf {
                it.text!!.isNotBlank() &&
                    !it.text.matches(output.toTypedArray())
            }
            ?.let { b ->
                Timber.d("Accept $b")
                launch {
                    mutex.withLock {
                        val all = dftDao.getAll()
                        var pinned = false
                        all.find { b.text == it.text }?.let {
                            dftDao.delete(it.id)
                            pinned = it.pinned
                        }
                        val rowId = dftDao.insert(b.copy(pinned = pinned))
                        removeOutdated()
                        updateItemCount()
                        dftDao.get(rowId)?.let { lastBean = it }
                    }
                }
            }
    }

    private suspend fun removeOutdated() {
        val all = dftDao.getAll()
        if (all.size > limit) {
            val outdated =
                all
                    .map {
                        if (it.pinned) {
                            it.copy(id = Int.MAX_VALUE)
                        } else {
                            it
                        }
                    }
                    .sortedBy { it.id }
                    .subList(0, all.size - limit)
            dftDao.delete(outdated)
        }
    }
}
