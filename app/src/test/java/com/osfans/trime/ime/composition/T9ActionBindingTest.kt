/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.composition

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class T9ActionBindingTest :
    StringSpec({
        "a held click uses the callback bound when the touch began" {
            val clicks = mutableListOf<String>()
            val binding = T9ActionBinding<Int>()
            binding.bind(1) { clicks.add("pressed") }
            binding.press() shouldBe true
            binding.bind(1) { clicks.add("later equivalent bind") }
            checkNotNull(binding.release())
            binding.click()
            clicks shouldBe listOf("pressed")
        }

        "changing the revision during a held touch rejects its release" {
            val clicks = mutableListOf<Int>()
            val binding = T9ActionBinding<Int>()
            binding.bind(1) { clicks.add(1) }
            binding.press() shouldBe true
            binding.bind(2) { clicks.add(2) }
            binding.release() shouldBe null
            clicks shouldBe emptyList()
        }

        "recycling after UP cannot redirect the already queued click" {
            val clicks = mutableListOf<Int>()
            val binding = T9ActionBinding<Int>()
            binding.bind(1) { clicks.add(1) }
            binding.press()
            checkNotNull(binding.release())
            binding.bind(2) { clicks.add(2) }
            binding.click()
            clicks shouldBe emptyList()
        }

        "cleanup belonging to an earlier release cannot discard a later queued identity" {
            val clicks = mutableListOf<Int>()
            val binding = T9ActionBinding<Int>()
            binding.bind(1) { clicks.add(1) }
            binding.press()
            val first = checkNotNull(binding.release())
            binding.click()
            binding.bind(2) { clicks.add(2) }
            binding.press()
            checkNotNull(binding.release())
            binding.clearPending(first)
            binding.bind(3) { clicks.add(3) }
            binding.click()
            clicks shouldBe listOf(1)
        }

        "unbound controls and cancelled touches cannot release an action" {
            val binding = T9ActionBinding<Int>()
            binding.bind(null) { error("Disabled action") }
            binding.press() shouldBe false
            binding.release() shouldBe null
            binding.click()
            binding.bind(1) {}
            binding.press()
            binding.cancel()
            binding.release() shouldBe null
        }

        "a non-touch click operates on the current action and cannot reenter itself" {
            var clicks = 0
            val binding = T9ActionBinding<Int>()
            binding.bind(1) {
                clicks++
                binding.click()
            }
            binding.click()
            clicks shouldBe 1
        }
    })
