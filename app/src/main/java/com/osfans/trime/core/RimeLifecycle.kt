/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// Adapted from https://github.com/fcitx5-android/fcitx5-android/blob/364afb44dcf0d9e3db3d43a21a32601b2190cbdf/app/src/main/java/org/fcitx/fcitx5/android/core/FcitxLifecycle.kt
package com.osfans.trime.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class RimeLifecycleRegistry : RimeLifecycle {

    private val observers = ConcurrentLinkedQueue<RimeLifecycleObserver>()

    override fun addObserver(observer: RimeLifecycleObserver) {
        observers.add(observer)
    }

    override fun removeObserver(observer: RimeLifecycleObserver) {
        observers.remove(observer)
    }

    override val currentState: RimeLifecycle.State
        get() = internalState

    private var internalState = RimeLifecycle.State.STOPPED

    override val lifecycleScope: CoroutineScope = RimeLifecycleScope(this)

    fun emitState(state: RimeLifecycle.State) = synchronized(internalState) {
        when (state) {
            RimeLifecycle.State.STARTING -> {
                checkAtState(RimeLifecycle.State.STOPPED)
                internalState = RimeLifecycle.State.STARTING
            }
            RimeLifecycle.State.READY -> {
                checkAtState(RimeLifecycle.State.STARTING)
                internalState = RimeLifecycle.State.READY
            }
            RimeLifecycle.State.STOPPING -> {
                checkAtState(RimeLifecycle.State.READY)
                internalState = RimeLifecycle.State.STOPPING
            }
            RimeLifecycle.State.STOPPED -> {
                checkAtState(RimeLifecycle.State.STOPPING)
                internalState = RimeLifecycle.State.STOPPED
            }
        }
        observers.forEach { it.onChanged(state) }
    }

    private fun checkAtState(state: RimeLifecycle.State) = takeIf { (internalState == state) }
        ?: throw IllegalStateException("Currently not at $state! Actual state is $internalState")
}

interface RimeLifecycle {
    val currentState: State
    val lifecycleScope: CoroutineScope

    fun addObserver(observer: RimeLifecycleObserver)
    fun removeObserver(observer: RimeLifecycleObserver)

    enum class State {
        STARTING,
        READY,
        STOPPING,
        STOPPED,
    }
}

interface RimeLifecycleOwner {
    val lifecycle: RimeLifecycle
}

val RimeLifecycleOwner.lifecycleScope get() = lifecycle.lifecycleScope

fun interface RimeLifecycleObserver {
    fun onChanged(value: RimeLifecycle.State)
}

class RimeLifecycleScope(
    val lifecycle: RimeLifecycle,
    override val coroutineContext: CoroutineContext = SupervisorJob(),
) : CoroutineScope,
    RimeLifecycleObserver {
    override fun onChanged(value: RimeLifecycle.State) {
        if (lifecycle.currentState >= RimeLifecycle.State.STOPPING) {
            coroutineContext.cancelChildren()
        }
    }
}

suspend fun <T> RimeLifecycle.whenAtState(
    state: RimeLifecycle.State,
    block: suspend CoroutineScope.() -> T,
): T = if (state == currentState) {
    block(lifecycleScope)
} else {
    StateDelegate(this, state).run(block)
}

suspend inline fun <T> RimeLifecycle.whenReady(
    noinline block: suspend CoroutineScope.() -> T,
) = whenAtState(RimeLifecycle.State.READY, block)

private class StateDelegate(
    val lifecycle: RimeLifecycle,
    val state: RimeLifecycle.State,
) {
    private val observer = RimeLifecycleObserver {
        if (lifecycle.currentState == state) {
            continuation?.resume(Unit)
        }
    }

    init {
        lifecycle.addObserver(observer)
    }

    private var continuation: Continuation<Unit>? = null

    suspend fun <T> run(block: suspend CoroutineScope.() -> T): T {
        suspendCoroutine { continuation = it }
        lifecycle.removeObserver(observer)
        return block(lifecycle.lifecycleScope)
    }
}
