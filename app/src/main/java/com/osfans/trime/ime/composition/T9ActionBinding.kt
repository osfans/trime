/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.composition

/** A held touch and its queued click keep the revision the user actually pressed. */
internal class T9ActionBinding<T : Any> {
    private data class Binding<T>(val identity: T, val click: () -> Unit)
    private data class Pending<T>(val token: Long, val binding: Binding<T>)
    private var current: Binding<T>? = null
    private var pressed: Binding<T>? = null
    private var pending: Pending<T>? = null
    private var token = 0L
    private var dispatching = false

    fun bind(identity: T?, click: () -> Unit) {
        current = identity?.let { Binding(it, click) }
    }

    fun press(): Boolean {
        pressed = if (pending == null && !dispatching) current else null
        return pressed != null
    }

    fun release(): Long? {
        val original = pressed
        pressed = null
        if (original == null || original.identity != current?.identity) return null
        val next = ++token
        pending = Pending(next, original)
        return next
    }

    fun clearPending(expectedToken: Long) {
        if (pending?.token == expectedToken) pending = null
    }

    fun click() {
        val original = pending?.binding ?: current
        pending = null
        if (dispatching || original == null || original.identity != current?.identity) return
        dispatching = true
        try {
            original.click()
        } finally {
            dispatching = false
        }
    }

    fun cancel() {
        pressed = null
    }

    fun detach() {
        pressed = null
        pending = null
    }
}
