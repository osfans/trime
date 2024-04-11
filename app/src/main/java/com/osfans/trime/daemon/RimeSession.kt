package com.osfans.trime.daemon

import com.osfans.trime.core.RimeApi

/**
 * A Interface to run different operations on RimeApi
 */
interface RimeSession {

    /**
     * Run an operation immediately
     * The suspended [block] will be executed in caller's thread.
     * Use this function only for non-blocking operations like
     * accessing [RimeApi.notificationFlow].
     */
    fun <T> run(block: suspend RimeApi.() -> T): T
}
