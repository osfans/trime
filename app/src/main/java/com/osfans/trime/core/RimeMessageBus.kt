/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.core

import androidx.annotation.WorkerThread
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking

/**
 * Ordered broadcast from synchronous JNI callbacks, with at most [capacity] buffered messages.
 *
 * A slow subscriber backpressures the native producer instead of dropping committed text or
 * key events. Call only from the engine/maintenance worker: consumers must enqueue native work
 * asynchronously rather than synchronously wait for that worker from their collect callback.
 * With no subscribers, publication returns immediately and retains no stale editor messages.
 */
internal class RimeMessageBus(capacity: Int = 64) {
    private val messages = MutableSharedFlow<RimeMessage<*>>(extraBufferCapacity = capacity)
    val flow = messages.asSharedFlow()

    @WorkerThread
    fun publish(message: RimeMessage<*>, beforePublish: () -> Unit = {}) {
        // Cache handlers can synchronously publish a schema change while handling a status.
        // Preserve that nested notification's position before the outer status message.
        beforePublish()
        if (!messages.tryEmit(message)) {
            // JNI cannot suspend. This blocks only its worker and does not create an unbounded
            // queue of relay coroutines. Default SharedFlow overflow behavior is SUSPEND.
            runBlocking { messages.emit(message) }
        }
    }
}
