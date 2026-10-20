/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.keyboard

/** Main-thread owner of one lazily initialized feedback resource. */
internal class FeedbackResource<T : Any>(private val release: (T) -> Unit) {
    private class Attempt<T> {
        var resource: T? = null
        var result: Boolean? = null
        var delivered = false
        var ready = false
    }

    private var initialized = false
    private var failed = false
    private var attempt: Attempt<T>? = null

    val canAcquire: Boolean get() = initialized && !failed
    val current: T? get() = attempt?.resource
    val ready: T? get() = attempt?.takeIf { it.ready }?.resource

    fun init() {
        initialized = true
    }

    /**
     * Completion must run on the same thread as the owner. It may arrive before the factory
     * returns, after destruction, or after a replacement resource has already been created.
     * A failed initialization is retried only after destroy/init, not on every key press.
     */
    fun acquire(
        enabled: Boolean,
        create: ((Boolean) -> Unit) -> T,
        onInitialized: (T, Boolean) -> Unit = { _, _ -> },
    ): T? {
        if (!canAcquire || !enabled) return null
        attempt?.let { return it.resource }
        val request = Attempt<T>()
        attempt = request
        val resource = try {
            create { success ->
                if (initialized && attempt === request && request.result == null) {
                    request.result = success
                    publish(request, onInitialized)
                }
            }
        } catch (error: Throwable) {
            if (attempt === request) {
                attempt = null
                failed = true
            }
            throw error
        }
        request.resource = resource
        if (!initialized || attempt !== request) {
            release(resource)
            return null
        }
        publish(request, onInitialized)
        return resource.takeIf { attempt === request }
    }

    private fun publish(request: Attempt<T>, onInitialized: (T, Boolean) -> Unit) {
        val resource = request.resource ?: return
        val success = request.result ?: return
        if (request.delivered) return
        request.delivered = true
        if (success) {
            request.ready = true
        } else {
            attempt = null
            failed = true
            release(resource)
        }
        onInitialized(resource, success)
    }

    fun destroy() {
        initialized = false
        failed = false
        val previous = attempt
        attempt = null
        previous?.resource?.let(release)
    }
}
