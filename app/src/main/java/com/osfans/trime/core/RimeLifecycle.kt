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
    private val _stateFlow = MutableStateFlow(RimeLifecycle.State.STOPPED)
    override val stateFlow = _stateFlow.asStateFlow()

    override val lifecycleScope: CoroutineScope = RimeLifecycleScope(this)

    fun emitState(state: RimeLifecycle.State) {
        when (state) {
            RimeLifecycle.State.STARTING -> {
                checkAtState(RimeLifecycle.State.STOPPED)
                _stateFlow.value = RimeLifecycle.State.STARTING
            }
            RimeLifecycle.State.READY -> {
                checkAtState(RimeLifecycle.State.STARTING)
                _stateFlow.value = RimeLifecycle.State.READY
            }
            RimeLifecycle.State.STOPPED -> {
                checkAtState(RimeLifecycle.State.READY)
                _stateFlow.value = RimeLifecycle.State.STOPPED
            }
        }
    }

    private fun checkAtState(state: RimeLifecycle.State) =
        takeIf { (_stateFlow.value == state) }
            ?: throw IllegalStateException("Currently not at $state! Actual state is ${_stateFlow.value}")
}

interface RimeLifecycle {
    val stateFlow: StateFlow<State>
    val lifecycleScope: CoroutineScope

    enum class State {
        STARTING,
        READY,
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
            lifecycle.stateFlow.collect {
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
): T {
    return if (stateFlow.value == state) {
        block(lifecycleScope)
    } else {
        StateDelegate(this, state).run(block)
    }
}

suspend inline fun <T> RimeLifecycle.whenReady(noinline block: suspend CoroutineScope.() -> T) =
    whenAtState(RimeLifecycle.State.READY, block)

private class StateDelegate(val lifecycle: RimeLifecycle, val state: RimeLifecycle.State) {
    private var job: Job? = null

    init {
        job =
            lifecycle.lifecycleScope.launch {
                lifecycle.stateFlow.collect {
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
