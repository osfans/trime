/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.data.db

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DelayedPrivateClipboardQuarantineTest :
    StringSpec({
        "API 26 timestamps suppress every callback at or before the private boundary" {
            val quarantine = DelayedPrivateClipboardQuarantine()
            quarantine.arm(byteArrayOf(1), timestamp = 100L, timestampsSupported = true)

            quarantine.shouldSuppress(byteArrayOf(1), timestamp = 100L) shouldBe true
            quarantine.shouldSuppress(byteArrayOf(1), timestamp = 99L) shouldBe true
            quarantine.shouldSuppress(byteArrayOf(1), timestamp = 100L) shouldBe true
        }

        "API 26 newer timestamp clears quarantine and is allowed" {
            val quarantine = DelayedPrivateClipboardQuarantine()
            quarantine.arm(byteArrayOf(1), timestamp = 100L, timestampsSupported = true)

            quarantine.shouldSuppress(byteArrayOf(2), timestamp = 101L) shouldBe false
            quarantine.shouldSuppress(byteArrayOf(1), timestamp = 100L) shouldBe false
        }

        "API 26 different identity clears quarantine despite equal or regressed clocks" {
            listOf(100L, 99L).forEach { callbackTimestamp ->
                val quarantine = DelayedPrivateClipboardQuarantine()
                quarantine.arm(byteArrayOf(1), timestamp = 100L, timestampsSupported = true)

                quarantine.shouldSuppress(byteArrayOf(2), callbackTimestamp) shouldBe false
                quarantine.shouldSuppress(byteArrayOf(1), timestamp = 100L) shouldBe false
            }
        }

        "API 26 missing identity remains fail closed until a newer timestamp arrives" {
            val quarantine = DelayedPrivateClipboardQuarantine()
            quarantine.arm(byteArrayOf(1), timestamp = 100L, timestampsSupported = true)

            quarantine.shouldSuppress(fingerprint = null, timestamp = 100L) shouldBe true
            quarantine.shouldSuppress(fingerprint = null, timestamp = null) shouldBe true
            quarantine.shouldSuppress(fingerprint = null, timestamp = 101L) shouldBe false
        }

        "invalid API 26 boundary timestamp falls back to fingerprint comparison" {
            val quarantine = DelayedPrivateClipboardQuarantine()
            quarantine.arm(byteArrayOf(1), timestamp = 0L, timestampsSupported = true)

            quarantine.shouldSuppress(byteArrayOf(1), timestamp = 200L) shouldBe true
            quarantine.shouldSuppress(byteArrayOf(2), timestamp = 201L) shouldBe false
        }

        "API 21 fingerprint matches remain quarantined until different content arrives" {
            val quarantine = DelayedPrivateClipboardQuarantine()
            quarantine.arm(byteArrayOf(3, 4), timestamp = null, timestampsSupported = false)

            quarantine.shouldSuppress(byteArrayOf(3, 4), timestamp = null) shouldBe true
            quarantine.shouldSuppress(byteArrayOf(3, 4), timestamp = null) shouldBe true
            quarantine.shouldSuppress(byteArrayOf(5, 6), timestamp = null) shouldBe false
            quarantine.shouldSuppress(byteArrayOf(3, 4), timestamp = null) shouldBe false
        }

        "missing callback identity fails closed while quarantine remains armed" {
            val quarantine = DelayedPrivateClipboardQuarantine()
            quarantine.arm(byteArrayOf(7), timestamp = null, timestampsSupported = false)

            quarantine.shouldSuppress(fingerprint = null, timestamp = null) shouldBe true
            quarantine.shouldSuppress(byteArrayOf(7), timestamp = null) shouldBe true
        }

        "an unknown private identity adopts the first readable identity and copies it" {
            val quarantine = DelayedPrivateClipboardQuarantine()
            quarantine.arm(fingerprint = null, timestamp = null, timestampsSupported = false)
            val firstReadable = byteArrayOf(8, 9)

            quarantine.shouldSuppress(firstReadable, timestamp = null) shouldBe true
            firstReadable[0] = 0

            quarantine.shouldSuppress(byteArrayOf(8, 9), timestamp = null) shouldBe true
            quarantine.shouldSuppress(byteArrayOf(10), timestamp = null) shouldBe false
        }

        "arming copies the private fingerprint array" {
            val quarantine = DelayedPrivateClipboardQuarantine()
            val privateFingerprint = byteArrayOf(11, 12)
            quarantine.arm(privateFingerprint, timestamp = null, timestampsSupported = false)
            privateFingerprint[0] = 0

            quarantine.shouldSuppress(byteArrayOf(11, 12), timestamp = null) shouldBe true
            quarantine.shouldSuppress(byteArrayOf(0, 12), timestamp = null) shouldBe false
        }
    })
