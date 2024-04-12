package com.osfans.trime.daemon

import androidx.core.app.NotificationCompat
import com.blankj.utilcode.util.NotificationUtils
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeLifecycle
import com.osfans.trime.core.whenReady
import com.osfans.trime.util.appContext
import kotlinx.coroutines.runBlocking
import splitties.systemservices.notificationManager
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

    private const val CHANNEL_ID = "rime-daemon"
    private var restartId = 0

    /**
     * Restart Rime instance to deploy while keep the session
     */
    fun restartRime(fullCheck: Boolean = false) =
        lock.withLock {
            val id = restartId++
            NotificationUtils.notify(CHANNEL_ID, id) {
                it.setSmallIcon(R.drawable.ic_baseline_sync_24)
                    .setContentTitle(appContext.getString(R.string.rime_daemon))
                    .setContentText(appContext.getString(R.string.restarting_rime))
                    .setOngoing(true)
                    .setProgress(100, 0, true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
            }
            realRime.finalize()
            realRime.startup(fullCheck)
            // cancel notification on ready
            realRime.lifecycle.whenReady {
                notificationManager.cancel(CHANNEL_ID, id)
            }
        }
}
