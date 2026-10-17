/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class RuntimeOptionCacheTest :
    StringSpec({
        "unknown options are disabled without calling native code" {
            RuntimeOptionCache()["custom_option"] shouldBe false
        }

        "option changes include disabling an existing option" {
            val cache = RuntimeOptionCache()
            cache.update("ascii_mode", true)
            cache["ascii_mode"] shouldBe true
            cache.update("ascii_mode", false)
            cache["ascii_mode"] shouldBe false
        }

        "schema reset drops stale custom option values" {
            val cache = RuntimeOptionCache()
            cache.update("custom_option", true)
            cache.clear()
            cache["custom_option"] shouldBe false
        }

        "UI reads continue while the engine thread is occupied" {
            val cache = RuntimeOptionCache()
            val published = CountDownLatch(1)
            val releaseEngine = CountDownLatch(1)
            val engine = thread {
                cache.update("simplification", true)
                published.countDown()
                releaseEngine.await()
            }
            try {
                published.await()
                repeat(10000) { cache["simplification"] shouldBe true }
                engine.isAlive shouldBe true
            } finally {
                releaseEngine.countDown()
                engine.join()
            }
        }

        "initial empty schema does not open a native config on the caller thread" {
            val schema = RimeSchema.empty()
            schema.schemaId shouldBe ".default"
            schema.alphabet shouldBe ""
            schema.switches shouldBe emptyList()
        }
    })
