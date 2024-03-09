package com.osfans.trime.ime.broadcast

import com.osfans.trime.core.RimeNotification.OptionNotification

interface InputBroadcastReceiver {
    fun onRimeOptionUpdated(value: OptionNotification.Value) {}
}
