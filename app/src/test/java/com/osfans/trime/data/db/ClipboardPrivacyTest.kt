/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.data.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking

class ClipboardPrivacyTest :
    StringSpec({
        "API 21 through 23 never call the unavailable ClipDescription extras API" {
            var queried = false
            SensitiveClipboardCompat.isSensitive(23) {
                queried = true
                true
            } shouldBe false
            queried shouldBe false
        }

        "API 24 and later read the documented literal sensitive key" {
            listOf(24, 32, 33, 37).forEach { sdk ->
                var queriedKey: String? = null
                SensitiveClipboardCompat.isSensitive(sdk) { key ->
                    queriedKey = key
                    true
                } shouldBe true
                queriedKey shouldBe "android.content.extra.IS_SENSITIVE"
            }
        }

        "missing and false sensitive extras remain ordinary clipboard content" {
            SensitiveClipboardCompat.isSensitive(24) { null } shouldBe false
            SensitiveClipboardCompat.isSensitive(37) { false } shouldBe false
        }

        "malformed sensitive metadata fails closed" {
            SensitiveClipboardCompat.isSensitive(24) { error("bad parcel") } shouldBe true
        }

        "sensitive clips never acquire a persistence generation" {
            var acquisitions = 0
            val gate = ClipboardPersistenceGate(
                acquirePermit = {
                    acquisitions += 1
                    7L
                },
                validatePermit = { true },
            )
            gate.acquire(isSensitive = true) shouldBe null
            acquisitions shouldBe 0
        }

        "a granted lease stays valid across a boundary and release invalidates it" {
            var nextPermit = 11L
            var active = false
            val permits = mutableSetOf<Long>()
            val gate = ClipboardPersistenceGate(
                acquirePermit = {
                    if (active) {
                        null
                    } else {
                        nextPermit.also {
                            nextPermit++
                            permits += it
                        }
                    }
                },
                validatePermit = permits::contains,
                releasePermit = { permits.remove(it) },
            )
            val queued = gate.acquire(isSensitive = false)!!
            gate.allows(queued) shouldBe true

            active = true
            gate.allows(queued) shouldBe true
            gate.acquire(isSensitive = false) shouldBe null

            gate.release(queued)
            gate.allows(queued) shouldBe false
            active = false
            gate.allows(gate.acquire(isSensitive = false)!!) shouldBe true
        }

        "releasing a lease inside a transaction throws so Room can roll it back" {
            val permits = mutableSetOf(3L)
            val gate =
                ClipboardPersistenceGate(
                    acquirePermit = { 3L },
                    validatePermit = permits::contains,
                    releasePermit = { permits.remove(it) },
                )
            val permit = gate.acquire(isSensitive = false)!!

            shouldThrow<PersistencePermitExpiredException> {
                runBlocking {
                    gate.guardTransaction(permit) {
                        gate.release(permit)
                    }
                }
            }
        }
    })
