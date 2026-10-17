/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.util

import java.util.concurrent.atomic.AtomicBoolean

/** Coordinates lifecycle and broadcast requests to replace a device-protected process. */
internal class DirectBootProcessHandoff(
    private val beforeReplacement: () -> Unit,
) {
    private val lock = Any()

    private val replacementStarted = AtomicBoolean(false)

    val pending: Boolean
        get() = replacementStarted.get()

    fun request(
        apiLevel: Int,
        directBootMode: Boolean,
        isUserUnlocked: () -> Boolean,
        finishBroadcast: (() -> Unit)? = null,
        replaceProcess: (finishBroadcast: () -> Unit) -> Unit,
    ): Boolean {
        val broadcastFinished = AtomicBoolean(false)
        val finishBroadcastOnce = {
            if (broadcastFinished.compareAndSet(false, true)) finishBroadcast?.invoke()
            Unit
        }
        if (apiLevel < 24 || !directBootMode) {
            finishBroadcastOnce()
            return false
        }
        val startReplacement = try {
            synchronized(lock) {
                when {
                    pending -> false

                    !isUserUnlocked() -> false

                    else -> {
                        // Publish privacy before another lifecycle can observe pending.
                        beforeReplacement()
                        replacementStarted.compareAndSet(false, true)
                    }
                }
            }
        } catch (error: Throwable) {
            finishBroadcastOnce()
            throw error
        }
        if (startReplacement) {
            try {
                replaceProcess(finishBroadcastOnce)
            } catch (error: Throwable) {
                finishBroadcastOnce()
                throw error
            }
        } else {
            finishBroadcastOnce()
        }
        return pending
    }
}
