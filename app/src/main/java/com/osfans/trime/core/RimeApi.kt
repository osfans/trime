package com.osfans.trime.core

import kotlinx.coroutines.flow.SharedFlow

interface RimeApi {
    val notificationFlow: SharedFlow<RimeNotification<*>>

    val stateFlow: SharedFlow<RimeLifecycle.State>

    val isReady: Boolean
}
