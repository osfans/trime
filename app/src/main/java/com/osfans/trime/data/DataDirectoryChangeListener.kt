package com.osfans.trime.data

/**
 * Do something when data directory change.
 */
object DataDirectoryChangeListener {

    // listener list
    val directoryChangeListeners = mutableListOf<DataDirectoryChangeListener.Listener>()

    interface Listener {
        fun onDataDirectoryChange()
    }

    fun addDirectoryChangeListener(directoryChangeListener: DataDirectoryChangeListener.Listener) {
        directoryChangeListeners.add(directoryChangeListener)
    }
    // do not remove
    // 在 directoryChangeListeners 中存放单例类的情况下，不应该尝试调用remove，会导致ConcurrentModificationException
}
