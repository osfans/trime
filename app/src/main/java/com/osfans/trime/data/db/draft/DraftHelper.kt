package com.osfans.trime.data.db.draft

import android.content.Context
import androidx.room.Room
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.db.Database
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.db.DatabaseDao
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.util.StringUtils.mismatch
import timber.log.Timber

object DraftHelper {
    private lateinit var dftDb: Database
    private lateinit var dftDao: DatabaseDao

    var itemCount: Int = 0
        private set

    private fun updateItemCount() {
        itemCount = dftDao.itemCount()
    }

    private val limit get() = AppPrefs.defaultInstance().other.draftLimit.toInt()
    private val output get() = AppPrefs.defaultInstance().other.draftOutputRules
        .trim().split('\n')

    var lastBean: DatabaseBean? = null

    fun init(context: Context) {
        dftDb = Room
            .databaseBuilder(context, Database::class.java, "draft")
            .addMigrations(Database.MIGRATION_3_4)
            .build()
        dftDao = dftDb.databaseDao()
    }

    fun get(id: Int) = dftDao.get(id)
    fun getAll() = dftDao.getAll()

    fun pin(id: Int) = dftDao.updatePinned(id, true)
    fun unpin(id: Int) = dftDao.updatePinned(id, false)

    fun updateText(id: Int, text: String) = dftDao.updateText(id, text)

    fun delete(id: Int) {
        dftDao.delete(id)
        updateItemCount()
    }
    fun deleteAll(skipUnpinned: Boolean = true) {
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
                    it.text.mismatch(output.toTypedArray())
            }
            ?.let { b ->
                Timber.d("Accept $b")
                val all = dftDao.getAll()
                var pinned = false
                all.find { b.text == it.text }?.let {
                    dftDao.delete(it.id)
                    pinned = it.pinned
                }
                val rowId = dftDao.insert(b.copy(pinned = pinned))
                removeOutdated()
                dftDao.get(rowId)?.let { lastBean = it }
            }
    }

    private fun removeOutdated() {
        val all = dftDao.getAll()
        if (all.size > limit) {
            val outdated = all
                .map {
                    if (it.pinned) it.copy(id = Int.MAX_VALUE)
                    else it
                }
                .sortedBy { it.id }
                .subList(0, all.size - limit)
            dftDao.delete(outdated)
        }
    }
}
