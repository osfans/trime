package com.osfans.trime.daemon

import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeApi
import kotlinx.coroutines.runBlocking
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object RimeDaemon {
    private val realRime by lazy { Rime.getInstance() }

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
        }

    fun createSession(name: String): RimeSession =
        lock.withLock {
            if (name in sessions) {
                return@withLock sessions.getValue(name)
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
                Rime.destroy()
            }
        }
}
