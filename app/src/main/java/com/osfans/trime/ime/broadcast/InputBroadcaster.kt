package com.osfans.trime.ime.broadcast

import com.osfans.trime.core.RimeNotification.OptionNotification
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.window.BoardWindow
import me.tatarka.inject.annotations.Inject
import java.util.concurrent.ConcurrentLinkedQueue

@InputScope
@Inject
class InputBroadcaster : InputBroadcastReceiver {
    private val receivers = ConcurrentLinkedQueue<InputBroadcastReceiver>()

    fun <T> addReceiver(receiver: T) {
        if (receiver is InputBroadcastReceiver && receiver !is InputBroadcaster) {
            receivers.add(receiver)
        }
    }

    fun <T> removeReceiver(receiver: T) {
        if (receiver is InputBroadcastReceiver && receiver !is InputBroadcaster) {
            receivers.remove(receiver)
        }
    }

    fun clear() {
        receivers.clear()
    }

    override fun onRimeOptionUpdated(value: OptionNotification.Value) {
        receivers.forEach { it.onRimeOptionUpdated(value) }
    }

    override fun onWindowAttached(window: BoardWindow) {
        receivers.forEach { it.onWindowAttached(window) }
    }

    override fun onWindowDetached(window: BoardWindow) {
        receivers.forEach { it.onWindowDetached(window) }
    }
}
