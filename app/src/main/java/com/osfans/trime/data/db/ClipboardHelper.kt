package com.osfans.trime.data.db

import android.content.ClipboardManager
import android.content.Context
import androidx.room.Room
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.util.StringUtils.matches
import com.osfans.trime.util.StringUtils.removeAll
import com.osfans.trime.util.WeakHashSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.systemservices.clipboardManager
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
        Timber.d("Add OnUpdateListener: $listener")
        val result = onUpdateListeners.add(listener)
        Timber.d(
            "onUpdateListeners.add: result = $result," +
                "onUpdateListeners.size = ${onUpdateListeners.size}",
        )
    }

    fun removeOnUpdateListener(listener: OnClipboardUpdateListener) {
        Timber.d("Remove OnUpdateListener: $listener")
        val result = onUpdateListeners.remove(listener)
        Timber.d(
            "onUpdateListeners.remove: result = $result," +
                "onUpdateListeners.size = ${onUpdateListeners.size}",
        )
    }

    private val limit get() = AppPrefs.defaultInstance().clipboard.clipboardLimit
    private val compare get() = AppPrefs.defaultInstance().clipboard.clipboardCompareRules
    private val output get() = AppPrefs.defaultInstance().clipboard.clipboardOutputRules

    var lastBean: DatabaseBean? = null

    fun init(context: Context) {
        clipboardManager.addPrimaryClipChangedListener(this)
        clbDb =
            Room
                .databaseBuilder(context, Database::class.java, "clipboard.db")
                .addMigrations(Database.MIGRATION_3_4)
                .build()
        clbDao = clbDb.databaseDao()
        launch { updateItemCount() }
    }

    suspend fun get(id: Int) = clbDao.get(id)

    suspend fun getAll() = clbDao.getAll()

    suspend fun pin(id: Int) = clbDao.updatePinned(id, true)

    suspend fun unpin(id: Int) = clbDao.updatePinned(id, false)

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

    /**
     * 此方法设置监听剪贴板变化，如有新的剪贴内容，就启动选定的剪贴板管理器
     *
     * - [compare] 比较规则。每次通知剪贴板管理器，都会保存 ClipBoardCompare 处理过的 string。
     * 如果两次处理过的内容不变，则不通知。
     *
     * - [output] 输出规则。如果剪贴板内容与规则匹配，则不通知剪贴板管理器。
     */
    override fun onPrimaryClipChanged() {
        if (!(limit != 0 && this::clbDao.isInitialized)) {
            return
        }
        clipboardManager
            .primaryClip
            ?.let { DatabaseBean.fromClipData(it) }
            ?.takeIf {
                it.text!!.isNotBlank() &&
                    !it.text.matches(output.toTypedArray())
            }
            ?.let { b ->
                if (b.text!!.removeAll(compare.toTypedArray()).isEmpty()) return
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
            clbDao.delete(outdated)
        }
    }

    suspend fun updateText(
        id: Int,
        text: String,
    ) = clbDao.updateText(id, text)
}
