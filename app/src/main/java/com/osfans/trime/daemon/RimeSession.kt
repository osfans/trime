package com.osfans.trime.daemon

import com.osfans.trime.core.RimeApi

/**
 * A interface to run different operations on RimeApi
 */
interface RimeSession {
    /**
     * Run an operation immediately
     * The suspended [block] will be executed in caller's thread.
     * Use this function only for non-blocking operations like
     * accessing [RimeApi.notificationFlow].
     */
    fun <T> run(block: suspend RimeApi.() -> T): T

    /**
     * Run an operation immediately if rime is at ready state.
     * Otherwise, caller will be suspended until rime is ready and operation is done.
     * The [block] will be executed in main thread.
     * Client should use this function in most cases.
     */
    fun runOnReady(block: RimeApi.() -> Unit)

    /**
     * Run an operation if rime is at ready state.
     * Otherwise, do nothing.
     * The [block] will be executed in main thread.
     */
    fun runIfReady(block: RimeApi.() -> Unit)
}
