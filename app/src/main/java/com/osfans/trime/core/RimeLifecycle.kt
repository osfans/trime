/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

// Adapted from [fcitx5-android@FcitxLifecycle.kt](https://github.com/fcitx5-android/fcitx5-android/blob/1c66257ad4c4cdc2852793940aec498e51f5e46f/app/src/main/java/org/fcitx/fcitx5/android/core/FcitxLifecycle.kt)
package com.osfans.trime.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

class RimeLifecycleRegistry : RimeLifecycle {

    @Volatile override var failureCause: Throwable? = null
        private set

    fun fail(error: Throwable) {
        failureCause = error
        internalStateFlow.value = RimeLifecycle.State.FAILED
        job.cancelChildren(RimeUnavailableException(error))
    }

    private val internalStateFlow = MutableStateFlow(RimeLifecycle.State.STOPPED)

    override val stateFlow = internalStateFlow.asStateFlow()

    override val currentState: RimeLifecycle.State
        get() = internalStateFlow.value

    private val job = SupervisorJob()

    override val lifecycleScope = CoroutineScope(job + Dispatchers.Default)

    fun emitEvent(event: RimeLifecycle.Event) {
        val newState = internalStateFlow.updateAndGet {
            when (event) {
                RimeLifecycle.Event.ON_START -> {
                    check(it == RimeLifecycle.State.STOPPED || it == RimeLifecycle.State.FAILED)
                    failureCause = null
                    RimeLifecycle.State.STARTING
                }

                RimeLifecycle.Event.ON_READY -> {
                    checkAtState(it, RimeLifecycle.State.STARTING)
                    RimeLifecycle.State.READY
                }

                RimeLifecycle.Event.ON_STOP -> {
                    checkAtState(it, RimeLifecycle.State.READY)
                    RimeLifecycle.State.STOPPING
                }

                RimeLifecycle.Event.ON_STOPPED -> {
                    checkAtState(it, RimeLifecycle.State.STOPPING)
                    RimeLifecycle.State.STOPPED
                }
            }
        }
        // Cancel the stopped generation once. Clients joining during STOPPING
        // must survive ON_STOPPED so their work can await the next READY.
        if (newState == RimeLifecycle.State.STOPPING) {
            job.cancelChildren()
        }
    }

    private fun checkAtState(currentState: RimeLifecycle.State, state: RimeLifecycle.State) {
        if (currentState == RimeLifecycle.State.FAILED) throw RimeUnavailableException(failureCause)
        if (currentState != state) {
            throw IllegalStateException("Currently not at $state! Actual state is $currentState")
        }
    }
}

interface RimeLifecycle {
    val failureCause: Throwable?
        get() = null
    val stateFlow: StateFlow<State>
    val currentState: State
    val lifecycleScope: CoroutineScope

    enum class State {
        STARTING,
        READY,
        STOPPING,
        STOPPED,
        FAILED,
    }

    enum class Event {
        ON_START,
        ON_READY,
        ON_STOP,
        ON_STOPPED,
    }
}

class RimeUnavailableException(cause: Throwable?) : CancellationException("Rime engine is unavailable") {
    init {
        initCause(cause)
    }
}

interface RimeLifecycleOwner {
    val lifecycle: RimeLifecycle
}

val RimeLifecycleOwner.lifecycleScope get() = lifecycle.lifecycleScope

suspend fun <T> RimeLifecycle.whenAtState(
    state: RimeLifecycle.State,
    block: suspend CoroutineScope.() -> T,
): T {
    val reached = stateFlow.first { it == state || it == RimeLifecycle.State.FAILED }
    if (reached == RimeLifecycle.State.FAILED) throw RimeUnavailableException(failureCause)
    return block(lifecycleScope)
}

suspend inline fun <T> RimeLifecycle.whenReady(
    noinline block: suspend CoroutineScope.() -> T,
) = whenAtState(RimeLifecycle.State.READY, block)

suspend inline fun <T> RimeLifecycle.whenStopped(noinline block: suspend CoroutineScope.() -> T) = whenAtState(RimeLifecycle.State.STOPPED, block)

fun <T> RimeLifecycle.launchWhenReady(block: suspend CoroutineScope.() -> T) = lifecycleScope.launch { whenReady(block) }

fun <T> RimeLifecycle.launchWhenStopped(block: suspend CoroutineScope.() -> T) = lifecycleScope.launch { whenStopped(block) }
