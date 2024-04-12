package com.osfans.trime.daemon

import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeLifecycle
import com.osfans.trime.core.whenReady
import kotlinx.coroutines.runBlocking
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object RimeDaemon {
    private val realRime by lazy { Rime() }

    private val rimeImpl by lazy { object : RimeApi by realRime {} }

    private val sessions = mutableMapOf<String, RimeSession>()

    private val lock = ReentrantLock()

    private fun establish(name: String) =
        object : RimeSession {
            private inline fun <T> ensureEstablished(block: () -> T) =
                if (name in sessions) {
                    block()
                } else {
                    throw IllegalStateException("Session $name is not established")
                }

            override fun <T> run(block: suspend RimeApi.() -> T): T =
                ensureEstablished {
                    runBlocking { block(rimeImpl) }
                }

            override fun runOnReady(block: RimeApi.() -> Unit) {
                ensureEstablished {
                    realRime.lifecycle.whenReady { block(rimeImpl) }
                }
            }

            override fun runIfReady(block: RimeApi.() -> Unit) {
                ensureEstablished {
                    if (realRime.isReady) {
                        realRime.lifecycle.handler.post { block(rimeImpl) }
                    }
                }
            }
        }

    fun createSession(name: String): RimeSession =
        lock.withLock {
            if (name in sessions) {
                return@withLock sessions.getValue(name)
            }
            if (realRime.lifecycle.stateFlow.value == RimeLifecycle.State.STOPPED) {
                realRime.startup(false)
            }
            val session = establish(name)
            sessions[name] = session
            return@withLock session
        }

    fun destroySession(name: String): Unit =
        lock.withLock {
            if (name !in sessions) {
                return
            }
            sessions -= name
            if (sessions.isEmpty()) {
                realRime.finalize()
            }
        }

    /**
     * Restart Rime instance to deploy while keep the session
     */
    fun restartRime(fullCheck: Boolean = false) =
        lock.withLock {
            realRime.finalize()
            realRime.startup(fullCheck)
        }
}
