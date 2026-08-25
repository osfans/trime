// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class BoundedCopyPoolTest :
    StringSpec({
        "mapParallel waits for all workers before returning" {
            runBlocking {
                val items = (0 until 64).toList()
                val results =
                    BoundedCopyPool.mapParallel(
                        items,
                        parallelism = 4,
                        dispatcher = Dispatchers.Default,
                    ) { item ->
                        delay(5)
                        item * 2
                    }
                results shouldBe items.map { it * 2 }
            }
        }

        "mapParallel returns empty list for empty input" {
            runBlocking {
                BoundedCopyPool.mapParallel(emptyList<Int>(), parallelism = 4) { it } shouldBe emptyList()
            }
        }
    })
