/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.clip

import android.content.ClipboardManager
import android.content.Context
import androidx.annotation.Keep
import androidx.room.withTransaction
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.util.WeakHashSet
import com.osfans.trime.util.matchesAny
import com.osfans.trime.util.removeRegexSet
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
    private val database: ClipDatabase get() = ClipDatabaseHolder.get()
    private val dao: ClipboardDao get() = database.clipboardDao()

    fun interface OnClipboardUpdateListener {
        fun onUpdate(entity: ClipboardEntity)
    }

    private val mutex = Mutex()

    var itemCount: Int = 0
        private set

    private suspend fun updateItemCount() {
        itemCount = dao.itemCount()
    }

    private val onUpdateListeners = WeakHashSet<OnClipboardUpdateListener>()

    fun addOnUpdateListener(listener: OnClipboardUpdateListener) {
        onUpdateListeners.add(listener)
    }

    fun removeOnUpdateListener(listener: OnClipboardUpdateListener) {
        onUpdateListeners.remove(listener)
    }

    private val clipPref = AppPrefs.defaultInstance().clipboard

    private val enabledPref = clipPref.clipboardListening

    @Keep
    private val enabledListener = PreferenceDelegate.OnChangeListener<Boolean> { _, value ->
        if (value) {
            clipboardManager.addPrimaryClipChangedListener(this)
        } else {
            clipboardManager.removePrimaryClipChangedListener(this)
        }
    }

    private val limitPref = clipPref.clipboardLimit

    @Keep
    private val limitListener = PreferenceDelegate.OnChangeListener<Int> { _, _ ->
        launch { removeOutdated() }
    }

    private val compareRules: Set<Regex> by lazy {
        val rules by clipPref.clipboardCompareRules
        rules
            .split('\n')
            .map { Regex(it.trim()) }
            .toSet()
    }

    private val outputRules: Set<Regex> by lazy {
        val rules by clipPref.clipboardOutputRules
        rules
            .split('\n')
            .map { Regex(it) }
            .toSet()
    }

    var lastEntity: ClipboardEntity? = null

    private fun updateLastEntity(entity: ClipboardEntity) {
        lastEntity = entity
        onUpdateListeners.forEach { it.onUpdate(entity) }
    }

    fun init(context: Context) {
        ClipDatabaseHolder.init(context)
        clipboardManager.addPrimaryClipChangedListener(this)
        enabledListener.onChange(enabledPref.key, enabledPref.getValue())
        enabledPref.registerOnChangeListener(enabledListener)
        limitListener.onChange(limitPref.key, limitPref.getValue())
        limitPref.registerOnChangeListener(limitListener)
        launch { updateItemCount() }
    }

    suspend fun get(id: Int) = dao.get(id)

    suspend fun haveUnpinned() = dao.haveUnpinned()

    fun allEntities() = dao.all()

    suspend fun pin(id: Int) = dao.updatePinned(id, true)

    suspend fun unpin(id: Int) = dao.updatePinned(id, false)

    suspend fun updateText(
        id: Int,
        text: String,
    ) {
        lastEntity?.let {
            if (id == it.id) updateLastEntity(it.copy(text = text))
        }
        dao.updateText(id, text)
    }

    suspend fun delete(id: Int) {
        dao.delete(id)
        updateItemCount()
    }

    suspend fun deleteAll(skipUnpinned: Boolean = true) {
        if (skipUnpinned) {
            dao.deleteAllUnpinned()
        } else {
            dao.deleteAll()
        }
        updateItemCount()
    }

    private var lastClipTimestamp = -1L

    /**
     * 此方法设置监听剪贴板变化，如有新的剪贴内容，就启动选定的剪贴板管理器
     *
     * - [compareRules] 比较规则。每次通知剪贴板管理器，都会保存 ClipBoardCompare 处理过的 string。
     * 如果两次处理过的内容不变，则不通知。
     *
     * - [outputRules] 输出规则。如果剪贴板内容与规则匹配，则不通知剪贴板管理器。
     */
    override fun onPrimaryClipChanged() {
        val clip = clipboardManager.primaryClip ?: return
        val timestamp = clip.description.timestamp
        if (timestamp == lastClipTimestamp) return
        lastClipTimestamp = timestamp
        launch {
            mutex.withLock {
                val entity = ClipboardEntity.fromClipData(clip) ?: return@withLock
                if (entity.text.isBlank()) return@withLock
                if (entity.text.matchesAny(outputRules) ||
                    entity.text.removeRegexSet(compareRules).isEmpty()
                ) {
                    return@withLock
                }
                try {
                    dao.find(entity.text, entity.sensitive)?.let {
                        updateLastEntity(it.copy(timestamp = entity.timestamp))
                        dao.updateTimestamp(it.id, entity.timestamp)
                        return@withLock
                    }
                    val insertedEntity =
                        database.withTransaction {
                            val rowId = dao.insert(entity)
                            removeOutdated()
                            updateItemCount()
                            dao.get(rowId) ?: entity
                        }
                    updateLastEntity(insertedEntity)
                    updateItemCount()
                } catch (exception: Exception) {
                    Timber.w("Failed to update clipboard database: $exception")
                    updateLastEntity(entity)
                }
            }
        }
    }

    private suspend fun removeOutdated() {
        val limit = limitPref.getValue()
        val unpinned = dao.getAllUnpinned()
        if (unpinned.size > limit) {
            val outdated =
                unpinned
                    .sortedBy { it.id }
                    .getOrNull(unpinned.size - limit)
            dao.deleteUnpinnedEarlierThan(outdated?.timestamp ?: System.currentTimeMillis())
        }
    }
}
