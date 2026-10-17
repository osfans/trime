/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.switches

import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeSchema
import com.osfans.trime.daemon.RimeSession
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy

private fun cachedSession(options: Map<String, Boolean>): RimeSession {
    val api = Proxy.newProxyInstance(RimeApi::class.java.classLoader, arrayOf(RimeApi::class.java)) { _, method, args ->
        check(method.name == "getRuntimeOptionCached") { "UI tried to call engine API: ${method.name}" }
        options[args[0] as String] ?: false
    } as RimeApi
    return object : RimeSession {
        override fun <T> run(block: suspend RimeApi.() -> T): T = runBlocking { block(api) }
        override suspend fun <T> runOnReady(block: suspend RimeApi.() -> T): T = error("Must not wait for engine")
        override fun runIfReady(block: suspend RimeApi.() -> Unit): Unit = error("Must not query engine")
        override val lifecycleScope: CoroutineScope get() = error("No asynchronous query needed")
    }
}

class SwitchOptionEntryTest :
    StringSpec({
        "binary switch label uses the cached state" {
            val switch = RimeSchema.Switch(name = "ascii_mode", states = listOf("Chinese", "English"))
            val entry = SwitchOptionEntry.fromSwitch(cachedSession(mapOf("ascii_mode" to true)), switch)
            entry?.label shouldBe "English → Chinese"
        }

        "option groups use the active cached member" {
            val switch = RimeSchema.Switch(options = listOf("one", "two"), states = listOf("First", "Second"))
            SwitchOptionEntry.fromSwitch(cachedSession(mapOf("two" to true)), switch)?.label shouldBe "Second"
        }

        "missing cached members safely default to the first state" {
            val switch = RimeSchema.Switch(options = listOf("one", "two"), states = listOf("First", "Second"))
            SwitchOptionEntry.fromSwitch(cachedSession(emptyMap()), switch)?.label shouldBe "First"
        }

        "invalid switch shapes do not query engine" {
            val switch = RimeSchema.Switch(name = "bad", states = listOf("Only"))
            SwitchOptionEntry.fromSwitch(cachedSession(emptyMap()), switch) shouldBe null
        }
    })
