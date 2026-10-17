/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.db

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.annotation.Keep
import androidx.room.Room
import androidx.room.withTransaction
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.ime.core.PrivacyModeState
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
    private lateinit var clbDb: Database
    private lateinit var clbDao: DatabaseDao

    fun interface OnClipboardUpdateListener {
        fun onUpdate(bean: DatabaseBean?)
    }

    private val mutex = Mutex()

    var itemCount: Int = 0
        private set

    private suspend fun updateItemCount() {
        itemCount = clbDao.itemCount()
    }

    private val onUpdateListenersLock = Any()
    private val onUpdateListeners = WeakHashSet<OnClipboardUpdateListener>()

    fun addOnUpdateListener(listener: OnClipboardUpdateListener) {
        synchronized(onUpdateListenersLock) { onUpdateListeners.add(listener) }
    }

    fun removeOnUpdateListener(listener: OnClipboardUpdateListener) {
        synchronized(onUpdateListenersLock) { onUpdateListeners.remove(listener) }
    }

    private val clipPref = AppPrefs.defaultInstance().clipboard

    private val enabledPref = clipPref.clipboardListening
    private val listenerRegistrationLock = Any()

    @Volatile
    private var listenerRegistered = false

    private val delayedPrivateClipboardQuarantine = DelayedPrivateClipboardQuarantine()

    private fun setClipboardListening(enabled: Boolean) {
        synchronized(listenerRegistrationLock) {
            if (enabled == listenerRegistered) return
            if (enabled) {
                clipboardManager.addPrimaryClipChangedListener(this)
            } else {
                clipboardManager.removePrimaryClipChangedListener(this)
            }
            listenerRegistered = enabled
        }
    }

    @Keep
    private val enabledListener = PreferenceDelegate.OnChangeListener<Boolean> { _, value ->
        setClipboardListening(value)
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

    @Volatile
    var lastBean: DatabaseBean? = null

    private fun updateLastBean(bean: DatabaseBean?) {
        lastBean = bean
        val listeners = synchronized(onUpdateListenersLock) { onUpdateListeners.toList() }
        listeners.forEach { it.onUpdate(bean) }
    }

    /** Drop in-memory clipboard content and tell active input bars to hide their suggestion. */
    fun clearTransientSuggestion() {
        updateLastBean(null)
    }

    /**
     * Publish app-owned sensitive content and suppress the resulting local callback on API 21+.
     * Android's standard sensitive marker is also attached where ClipDescription supports it.
     */
    fun setSensitivePrimaryClip(
        manager: ClipboardManager,
        clip: ClipData,
    ) {
        if (listenerRegistered) {
            AppOwnedSensitiveClipboard.setPrimaryClip(manager, clip)
        } else {
            AppOwnedSensitiveClipboard.discardPending()
            manager.setPrimaryClip(SensitiveClipboardCompat.markSensitive(clip))
        }
    }

    /**
     * Snapshot the current private clipboard boundary before process privacy is released.
     * Only a SHA-256 identity, plus the platform timestamp where available, is retained.
     */
    fun quarantineCurrentPrimaryClip() {
        val timestampsSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        val result = runCatching { clipboardManager.primaryClip }
        if (result.isFailure) {
            delayedPrivateClipboardQuarantine.arm(
                fingerprint = null,
                timestamp = null,
                timestampsSupported = timestampsSupported,
            )
            return
        }
        val clip = result.getOrNull()
        if (clip == null) {
            delayedPrivateClipboardQuarantine.clear()
            return
        }
        delayedPrivateClipboardQuarantine.arm(
            fingerprint = ClipboardContentIdentity.sha256OrNull(clip),
            timestamp = clip.timestampOrNull(),
            timestampsSupported = timestampsSupported,
        )
    }

    private fun ClipData.timestampOrNull(): Long? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching { description.timestamp }.getOrNull()?.takeIf { it > 0L }
    } else {
        null
    }

    private fun publishCapturedBean(
        bean: DatabaseBean,
        permit: Long,
    ) {
        PrivacyModeState.publishIfOrdinary(permit) {
            updateLastBean(bean)
        }
    }

    fun init(
        context: Context,
        persistHistory: Boolean = true,
    ) {
        clbDb =
            (
                if (persistHistory) {
                    Room.databaseBuilder(context, Database::class.java, "clipboard.db")
                } else {
                    Room.inMemoryDatabaseBuilder(context, Database::class.java)
                }
                )
                .addMigrations(Database.MIGRATION_3_4)
                .build()
        clbDao = clbDb.databaseDao()
        enabledListener.onChange(enabledPref.key, enabledPref.getValue())
        enabledPref.registerOnChangeListener(enabledListener)
        limitListener.onChange(limitPref.key, limitPref.getValue())
        limitPref.registerOnChangeListener(limitListener)
        launch { updateItemCount() }
    }

    suspend fun get(id: Int) = clbDao.get(id)

    suspend fun haveUnpinned() = clbDao.haveUnpinned()

    fun allBeans() = clbDao.allBeans()

    suspend fun pin(id: Int) = clbDao.updatePinned(id, true)

    suspend fun unpin(id: Int) = clbDao.updatePinned(id, false)

    /** Missing rows are a normal edit outcome; only a committed single-row update is published. */
    suspend fun updateText(
        id: Int,
        text: String,
    ): TextUpdateResult {
        val permit = clipboardPersistenceGate.acquire(isSensitive = false)
            ?: throw PersistencePermitExpiredException()
        try {
            return mutex.withLock {
                val existing = clbDao.get(id) ?: return@withLock TextUpdateResult.NOT_FOUND
                val updated = existing.copy(text = text)
                val outcome = clbDb.withTransaction {
                    clipboardPersistenceGate.guardTransaction(permit) {
                        when (val affected = clbDao.updateText(id, text)) {
                            0 -> TextUpdateResult.NOT_FOUND
                            1 -> TextUpdateResult.SAVED
                            else -> error("Unexpected edited row count: $affected")
                        }
                    }
                }
                if (outcome == TextUpdateResult.SAVED) {
                    PrivacyModeState.publishIfOrdinary(permit) {
                        if (lastBean?.id == id) updateLastBean(updated)
                    }
                }
                outcome
            }
        } finally {
            clipboardPersistenceGate.release(permit)
        }
    }

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
        updateItemCount()
    }

    private var lastClipTimestamp = -1L
    private var lastClipHash = 0

    /**
     * 此方法设置监听剪贴板变化，如有新的剪贴内容，就启动选定的剪贴板管理器
     *
     * - [compareRules] 比较规则。每次通知剪贴板管理器，都会保存 ClipBoardCompare 处理过的 string。
     * 如果两次处理过的内容不变，则不通知。
     *
     * - [outputRules] 输出规则。如果剪贴板内容与规则匹配，则不通知剪贴板管理器。
     */
    override fun onPrimaryClipChanged() {
        if (!listenerRegistered) {
            AppOwnedSensitiveClipboard.discardPending()
            return
        }
        val observedEpoch = PrivacyModeState.observeOrdinaryPersistence()
        if (observedEpoch == null) {
            AppOwnedSensitiveClipboard.discardPending()
            clearTransientSuggestion()
            return
        }
        val clip =
            clipboardManager.primaryClip ?: run {
                AppOwnedSensitiveClipboard.discardPending()
                delayedPrivateClipboardQuarantine.clear()
                return
            }
        val fingerprint = ClipboardContentIdentity.sha256OrNull(clip)
        val appOwnedSensitive = AppOwnedSensitiveClipboard.consumeIfOwned(fingerprint)
        val delayedPrivate =
            delayedPrivateClipboardQuarantine.shouldSuppress(
                fingerprint = fingerprint,
                timestamp = clip.timestampOrNull(),
            )
        if (appOwnedSensitive || delayedPrivate) {
            clearTransientSuggestion()
            return
        }
        if (SensitiveClipboardCompat.isSensitive(clip.description)) {
            clearTransientSuggestion()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timestamp = clip.description.timestamp
            if (timestamp == lastClipTimestamp) return
            lastClipTimestamp = timestamp
        } else {
            val timestamp = System.currentTimeMillis()
            val hash = clip.hashCode()
            if (timestamp - lastClipTimestamp < 100L && hash == lastClipHash) return
            lastClipTimestamp = timestamp
            lastClipHash = hash
        }
        val permit = clipboardPersistenceGate.acquire(
            isSensitive = false,
            observedEpoch = observedEpoch,
        ) ?: run {
            clearTransientSuggestion()
            return
        }
        launch {
            try {
                mutex.withLock {
                    val bean = DatabaseBean.fromClipData(clip) ?: return@withLock
                    if (bean.text.isNullOrBlank()) return@withLock
                    if (bean.text.matchesAny(outputRules) ||
                        bean.text.removeRegexSet(compareRules).isEmpty()
                    ) {
                        return@withLock
                    }
                    clbDao.find(bean.text)?.let { existing ->
                        val refreshed =
                            clbDb.withTransaction {
                                clipboardPersistenceGate.guardTransaction(permit) {
                                    clbDao.updateTime(existing.id, bean.time)
                                    existing.copy(time = bean.time)
                                }
                            }
                        publishCapturedBean(refreshed, permit)
                        return@withLock
                    }
                    val insertedBean =
                        clbDb.withTransaction {
                            clipboardPersistenceGate.guardTransaction(permit) {
                                val rowId = clbDao.insert(bean)
                                val id = rowId.toInt()
                                clbDao.get(rowId) ?: bean.copy(id = id)
                            }
                        }
                    removeOutdated()
                    updateItemCount()
                    publishCapturedBean(insertedBean, permit)
                }
            } catch (_: PersistencePermitExpiredException) {
                // Room rolled the transaction back; never publish the rejected content.
            } catch (exception: Exception) {
                Timber.w("Failed to update clipboard database: $exception")
            }
        }.invokeOnCompletion {
            // Completion handlers also run when a coroutine is cancelled before its body starts.
            clipboardPersistenceGate.release(permit)
        }
    }

    private suspend fun removeOutdated() {
        val limit = limitPref.getValue()
        val unpinned = clbDao.getAllUnpinned()
        if (unpinned.size > limit) {
            val outdated =
                unpinned
                    .sortedBy { it.id }
                    .getOrNull(unpinned.size - limit)
            clbDao.deletedUnpinnedEarlierThan(outdated?.time ?: System.currentTimeMillis())
        }
    }
}
