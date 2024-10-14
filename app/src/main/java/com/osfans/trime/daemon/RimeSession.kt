// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.daemon

import com.osfans.trime.core.RimeApi
import kotlinx.coroutines.CoroutineScope

/**
 * A interface to run different operations on RimeApi
 */
interface RimeSession {
    /**
     * Run an operation immediately
     * The suspended [block] will be executed in caller's thread.
     * Use this function only for non-blocking operations like
     * accessing [RimeApi.callbackFlow].
     */
    fun <T> run(block: suspend RimeApi.() -> T): T

    /**
     * Run an operation immediately if rime is at ready state.
     * Otherwise, caller will be suspended until rime is ready and operation is done.
     * The [block] will be executed in caller's thread.
     * Client should use this function in most cases.
     */
    suspend fun <T> runOnReady(block: suspend RimeApi.() -> T): T

    /**
     * Run an operation if rime is at ready state.
     * Otherwise, do nothing.
     * The [block] will be executed in executed in thread pool.
     * This function does not block or suspend the caller.
     */
    fun runIfReady(block: suspend RimeApi.() -> Unit)

    val lifecycleScope: CoroutineScope
}
