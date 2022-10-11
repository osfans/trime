package com.osfans.trime.data.db.clipboard

import android.content.ClipboardManager
import android.content.Context
import androidx.room.Room
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.db.Database
import com.osfans.trime.data.db.DatabaseBean
import com.osfans.trime.data.db.DatabaseDao
import com.osfans.trime.util.StringUtils.mismatch
import com.osfans.trime.util.StringUtils.replace
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.clipboardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

object ClipboardHelper :
    ClipboardManager.OnPrimaryClipChangedListener,
    CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.Default) {
    private lateinit var clbDb: Database
    private lateinit var clbDao: DatabaseDao

    fun interface OnClipboardUpdateListener {
        fun onUpdate(text: String)
    }

    private val mutex = Mutex()

    var itemCount: Int = 0
        private set

    private suspend fun updateItemCount() {
        itemCount = clbDao.itemCount()
    }

    private val onUpdateListeners = WeakHashSet<OnClipboardUpdateListener>()

    fun addOnUpdateListener(listener: OnClipboardUpdateListener) {
        onUpdateListeners.add(listener)
    }

    fun removeOnUpdateListener(listener: OnClipboardUpdateListener) {
        onUpdateListeners.remove(listener)
    }

    private val limit get() = AppPrefs.defaultInstance().other.clipboardLimit.toInt()
    private val compare get() = AppPrefs.defaultInstance().other.clipboardCompareRules
        .trim().split('\n')
    private val output get() = AppPrefs.defaultInstance().other.clipboardOutputRules
        .trim().split('\n')

    var lastBean: DatabaseBean? = null

    fun init(context: Context) {
        clipboardManager.addPrimaryClipChangedListener(this)
        clbDb = Room
            .databaseBuilder(context, Database::class.java, "clipboard.db")
            .addMigrations(Database.MIGRATION_3_4)
            .build()
        clbDao = clbDb.databaseDao()
        launch { updateItemCount() }
    }

    suspend fun get(id: Int) = clbDao.get(id)
    suspend fun getAll() = clbDao.getAll()

    suspend fun pin(id: Int) = clbDao.updatePinned(id, true)
    suspend fun unpinned(id: Int) = clbDao.updatePinned(id, true)

    suspend fun delete(id: Int) {
        clbDao.delete(id)
        updateItemCount()
    }
    suspend fun deleteAll(skipUnpinned: Boolean = true) {
        if (skipUnpinned) {
            clbDao.deleteAllUnpinned()
        } else {
            clbDao.deleteAll()
        }
    }

    override fun onPrimaryClipChanged() {
        if (!(limit != 0 && this::clbDao.isInitialized)) {
            return
        }
        clipboardManager
            .primaryClip
            ?.let { DatabaseBean.fromClipData(it) }
            ?.takeIf {
                it.text!!.isNotBlank() &&
                    it.text.mismatch(output.toTypedArray())
            }
            ?.let { b ->
                if (b.text!!.replace(compare.toTypedArray()).isEmpty()) return
                Timber.d("Accept clipboard $b")
                launch {
                    mutex.withLock {
                        val all = clbDao.getAll()
                        var pinned = false
                        all.find { b.text == it.text }?.let {
                            clbDao.delete(it.id)
                            pinned = it.pinned
                        }
                        val rowId = clbDao.insert(b.copy(pinned = pinned))
                        removeOutdated()
                        updateItemCount()
                        clbDao.get(rowId)?.let { newBean ->
                            lastBean = newBean
                            onUpdateListeners.forEach { listener ->
                                listener.onUpdate(newBean.text ?: "")
                            }
                        }
                    }
                }
            }
    }

    private suspend fun removeOutdated() {
        val all = clbDao.getAll()
        if (all.size > limit) {
            val outdated = all
                .map {
                    if (it.pinned) it.copy(id = Int.MAX_VALUE)
                    else it
                }
                .sortedBy { it.id }
                .subList(0, all.size - limit)
            clbDao.delete(outdated)
        }
    }
}
