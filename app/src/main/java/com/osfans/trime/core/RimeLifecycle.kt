package com.osfans.trime.core

import android.os.Handler
import android.os.Looper
import androidx.core.os.HandlerCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.Collections

class RimeLifecycleImpl : RimeLifecycle {
    private val _stateFlow = MutableStateFlow(RimeLifecycle.State.STOPPED)
    override val stateFlow = _stateFlow.asStateFlow()

    override val handler = HandlerCompat.createAsync(Looper.getMainLooper())
    override val runnableList: MutableList<Runnable> = Collections.synchronizedList(mutableListOf<Runnable>())

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
    val handler: Handler
    val runnableList: MutableList<Runnable>

    enum class State {
        STARTING,
        READY,
        STOPPED,
    }
}

interface RimeLifecycleOwner {
    val lifecycle: RimeLifecycle
    val handler get() = lifecycle.handler
}

fun RimeLifecycle.whenAtState(
    state: RimeLifecycle.State,
    block: () -> Unit,
) {
    runnableList.add(Runnable { block() })
    if (stateFlow.value == state) {
        handler.post(runnableList.removeFirst())
    } else {
        StateDelegate(this, state).run(block)
    }
}

inline fun RimeLifecycle.whenReady(noinline block: () -> Unit) = whenAtState(RimeLifecycle.State.READY, block)

private class StateDelegate(val lifecycle: RimeLifecycle, val state: RimeLifecycle.State) {
    private var job: Job? = null

    init {
        job =
            lifecycle.stateFlow.onEach {
                if (it == state) {
                    while (lifecycle.runnableList.isNotEmpty()) {
                        lifecycle.handler.post(lifecycle.runnableList.removeFirst())
                    }
                }
            }.launchIn(MainScope())
    }

    fun <T> run(block: () -> T): T {
        job?.cancel()
        job = null
        return block()
    }
}
