package com.osfans.trime.ime.core

import android.os.Looper
import androidx.core.os.HandlerCompat
import com.osfans.trime.core.Rime
import com.osfans.trime.data.DataDirectoryChangeListener
import com.osfans.trime.data.theme.Theme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.Collections
import kotlin.system.measureTimeMillis

object RimeWrapper {
    private var mainThreadHandler = HandlerCompat.createAsync(Looper.getMainLooper())
    private val mutex = Mutex()
    private val myThreadSafeList = Collections.synchronizedList(mutableListOf<Runnable>())
    private val _statusStateFlow = MutableStateFlow(Status.UN_INIT)
    val statusStateFlow = _statusStateFlow.asStateFlow()

    var canStart = false

    fun startup(r: Runnable? = null) {
        r.let {
            myThreadSafeList.add(it)
        }
        if (canStart) {
            if (mutex.tryLock()) {
                if (_statusStateFlow.value == Status.UN_INIT) {
                    Timber.d("Starting in a thread")
                    _statusStateFlow.value = Status.IN_PROGRESS
                    mutex.unlock()

                    val scope = CoroutineScope(Dispatchers.IO)
                    scope.launch {
                        measureTimeMillis {
                            Rime.getInstance(false)
                            Theme.get()
                        }.also {
                            Timber.d("Startup completed.  It takes ${it / 1000} seconds")
                        }

                        mutex.withLock {
                            _statusStateFlow.value = Status.READY
                        }

                        notifyUnlock()
                    }
                } else {
                    mutex.unlock()
                }
            }
        } else {
            Timber.d("RimeWrapper shall not be started")
        }
    }

    suspend fun deploy(): Boolean {
        if (mutex.tryLock()) {
            if (_statusStateFlow.value != Status.IN_PROGRESS) {
                _statusStateFlow.value = Status.IN_PROGRESS
                mutex.unlock()

                DataDirectoryChangeListener.directoryChangeListeners.forEach {
                    it.onDataDirectoryChange()
                }

                Rime.deploy()

                mutex.withLock {
                    _statusStateFlow.value = Status.READY
                }
                Timber.d("Rime Deployed")

                return true
            } else {
                mutex.unlock()
            }
        }
        return false
    }

    private fun notifyUnlock() {
        Timber.d("notifying unlock")
        while (myThreadSafeList.isNotEmpty()) {
            myThreadSafeList.removeFirstOrNull()?.let {
                mainThreadHandler.post(it)
            }
        }
        Timber.d("Unlock Run Completed")
    }

    fun runCheck() {
        if (isReady()) {
            notifyUnlock()
        } else if (_statusStateFlow.value == Status.UN_INIT) {
            startup()
        }
    }

    fun isReady(): Boolean {
        return _statusStateFlow.value == Status.READY
    }

    fun runAfterStarted(r: Runnable) {
        myThreadSafeList.add(r)
        runCheck()
    }
}

enum class Status {
    UN_INIT,
    IN_PROGRESS,
    READY,
}
