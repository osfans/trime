// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

// Adapted from https://github.com/fcitx5-android/fcitx5-android/blob/364afb44dcf0d9e3db3d43a21a32601b2190cbdf/app/src/main/java/org/fcitx/fcitx5/android/core/FcitxLifecycle.kt
package com.osfans.trime.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class RimeLifecycleImpl : RimeLifecycle {
    private val internalStateFlow = MutableStateFlow(RimeLifecycle.State.STOPPED)
    override val currentStateFlow = internalStateFlow.asStateFlow()

    override val lifecycleScope: CoroutineScope = RimeLifecycleScope(this)

    fun emitState(state: RimeLifecycle.State) {
        when (state) {
            RimeLifecycle.State.STARTING -> {
                checkAtState(RimeLifecycle.State.STOPPED)
                internalStateFlow.value = RimeLifecycle.State.STARTING
            }
            RimeLifecycle.State.READY -> {
                checkAtState(RimeLifecycle.State.STARTING)
                internalStateFlow.value = RimeLifecycle.State.READY
            }
            RimeLifecycle.State.STOPPING -> {
                checkAtState(RimeLifecycle.State.READY)
                internalStateFlow.value = RimeLifecycle.State.STOPPING
            }
            RimeLifecycle.State.STOPPED -> {
                checkAtState(RimeLifecycle.State.STOPPING)
                internalStateFlow.value = RimeLifecycle.State.STOPPED
            }
        }
    }

    private fun checkAtState(state: RimeLifecycle.State) =
        takeIf { (internalStateFlow.value == state) }
            ?: throw IllegalStateException("Currently not at $state! Actual state is ${internalStateFlow.value}")
}

interface RimeLifecycle {
    val currentStateFlow: StateFlow<State>
    val lifecycleScope: CoroutineScope

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

class RimeLifecycleScope(
    val lifecycle: RimeLifecycle,
    override val coroutineContext: CoroutineContext = SupervisorJob(),
) : CoroutineScope {
    init {
        launch {
            lifecycle.currentStateFlow.collect {
                if (it == RimeLifecycle.State.STOPPED) {
                    coroutineContext.cancelChildren()
                }
            }
        }
    }
}

suspend fun <T> RimeLifecycle.whenAtState(
    state: RimeLifecycle.State,
    block: suspend CoroutineScope.() -> T,
): T =
    if (currentStateFlow.value == state) {
        block(lifecycleScope)
    } else {
        StateDelegate(this, state).run(block)
    }

suspend inline fun <T> RimeLifecycle.whenReady(noinline block: suspend CoroutineScope.() -> T) =
    whenAtState(RimeLifecycle.State.READY, block)

private class StateDelegate(
    val lifecycle: RimeLifecycle,
    val state: RimeLifecycle.State,
) {
    private var job: Job? = null

    init {
        job =
            lifecycle.lifecycleScope.launch {
                lifecycle.currentStateFlow.collect {
                    if (it == state) {
                        continuation?.resume(Unit)
                    }
                }
            }
    }

    private var continuation: Continuation<Unit>? = null

    suspend fun <T> run(block: suspend CoroutineScope.() -> T): T {
        suspendCoroutine { continuation = it }
        job?.cancel()
        job = null
        return block(lifecycle.lifecycleScope)
    }
}
