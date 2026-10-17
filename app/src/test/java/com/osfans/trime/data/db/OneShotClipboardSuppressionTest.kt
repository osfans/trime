/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.data.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class OneShotClipboardSuppressionTest :
    StringSpec({
        "matching callbacks stay suppressed until a different identity arrives" {
            val suppression = OneShotClipboardSuppression()
            val fingerprint = byteArrayOf(1, 2, 3)

            suppression.write(fingerprint) { Unit }

            suppression.consume(fingerprint) shouldBe true
            suppression.consume(fingerprint) shouldBe true
            suppression.consume(byteArrayOf(99)) shouldBe false
            suppression.consume(fingerprint) shouldBe false
        }

        "two sensitive writes suppress two callbacks that both read the newest clip" {
            val suppression = OneShotClipboardSuppression()
            val first = byteArrayOf(21, 22)
            val newest = byteArrayOf(23, 24)

            suppression.write(first) { Unit }
            suppression.write(newest) { Unit }

            suppression.consume(newest) shouldBe true
            suppression.consume(newest) shouldBe true
            suppression.consume(byteArrayOf(26)) shouldBe false
            suppression.consume(newest) shouldBe false
        }

        "a mismatched callback consumes stale suppression without hiding content" {
            val suppression = OneShotClipboardSuppression()
            val expected = byteArrayOf(4, 5, 6)

            suppression.write(expected) { Unit }

            suppression.consume(byteArrayOf(7, 8, 9)) shouldBe false
            suppression.consume(expected) shouldBe false
        }

        "an unreadable callback fails closed for one pending sensitive notification" {
            val suppression = OneShotClipboardSuppression()
            val fingerprint = byteArrayOf(25)
            suppression.write(fingerprint) { Unit }

            suppression.consume(fingerprint = null) shouldBe true
            suppression.consume(fingerprint) shouldBe true
            suppression.consume(byteArrayOf(26)) shouldBe false
            suppression.consume(fingerprint) shouldBe false
        }

        "a failed clipboard write clears its pending suppression" {
            val suppression = OneShotClipboardSuppression()
            val fingerprint = byteArrayOf(10, 11, 12)

            shouldThrow<IllegalStateException> {
                suppression.write(fingerprint) { error("clipboard unavailable") }
            }

            suppression.consume(fingerprint) shouldBe false
        }

        "a callback can discard pending state without reading clipboard content" {
            val suppression = OneShotClipboardSuppression()
            val fingerprint = byteArrayOf(20, 21)
            suppression.write(fingerprint) { Unit }

            suppression.discard()

            suppression.consume(fingerprint) shouldBe false
        }

        "an older failing writer cannot clear a newer pending write" {
            val suppression = OneShotClipboardSuppression()
            val older = byteArrayOf(13)
            val newer = byteArrayOf(14)

            shouldThrow<IllegalStateException> {
                suppression.write(older) {
                    suppression.write(newer) { Unit }
                    error("older write failed")
                }
            }

            suppression.consume(newer) shouldBe true
        }

        "fingerprints are copied instead of retaining caller-owned mutable state" {
            val suppression = OneShotClipboardSuppression()
            val fingerprint = byteArrayOf(15, 16)
            suppression.write(fingerprint) { Unit }
            fingerprint[0] = 0

            suppression.consume(byteArrayOf(15, 16)) shouldBe true
        }
    })
