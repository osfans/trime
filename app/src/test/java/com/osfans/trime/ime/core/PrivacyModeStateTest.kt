/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class PrivacyModeStateTest :
    StringSpec({
        beforeTest {
            PrivacyModeState.initialize(manual = false)
        }

        "a granted persistence lease survives a boundary until it is released" {
            val permit = PrivacyModeState.acquirePersistencePermit()
            permit shouldNotBe null

            PrivacyModeState.beginInput(11, PrivacyMode(password = true))

            PrivacyModeState.active shouldBe true
            PrivacyModeState.acquirePersistencePermit().shouldBeNull()
            val granted = requireNotNull(permit)
            PrivacyModeState.allowsPersistence(granted) shouldBe true

            PrivacyModeState.releasePersistencePermit(granted)
            PrivacyModeState.allowsPersistence(granted) shouldBe false
        }

        "stale editor owners cannot clear a newer private session" {
            PrivacyModeState.beginInput(21, PrivacyMode(password = true))
            PrivacyModeState.beginInput(22, PrivacyMode(appRequested = true))

            PrivacyModeState.endInput(21)
            PrivacyModeState.active shouldBe true

            PrivacyModeState.endInput(22)
            PrivacyModeState.active shouldBe false
        }

        "process replacement blocks ordinary input binds manual changes and editor cleanup" {
            PrivacyModeState.beginInput(101, PrivacyMode(password = true))
            PrivacyModeState.beginProcessReplacement()

            PrivacyModeState.beginInput(102, PrivacyMode())
            var ordinaryBoundary = false
            PrivacyModeState.updateInput(102, PrivacyMode()) { ordinaryBoundary = true } shouldBe true
            PrivacyModeState.setManual(false) { ordinaryBoundary = true }
            PrivacyModeState.endInput(102) { ordinaryBoundary = true }
            PrivacyModeState.endInput(101) { ordinaryBoundary = true }

            PrivacyModeState.active shouldBe true
            ordinaryBoundary shouldBe false
            PrivacyModeState.observeOrdinaryPersistence().shouldBeNull()
            PrivacyModeState.acquirePersistencePermit().shouldBeNull()
        }

        "a replacement boundary rejects stale clipboard reads and publication of prior writes" {
            val observation = requireNotNull(PrivacyModeState.observeOrdinaryPersistence())
            val permit = requireNotNull(PrivacyModeState.acquirePersistencePermit(observation))
            PrivacyModeState.beginProcessReplacement()
            PrivacyModeState.beginProcessReplacement()

            PrivacyModeState.acquirePersistencePermit(observation).shouldBeNull()
            var published = false
            PrivacyModeState.publishIfOrdinary(permit) { published = true } shouldBe false
            published shouldBe false
            PrivacyModeState.allowsPersistence(permit) shouldBe true
            PrivacyModeState.releasePersistencePermit(permit)
        }

        "a new normal process resets replacement privacy and preserves the saved manual preference" {
            PrivacyModeState.beginProcessReplacement()
            PrivacyModeState.initialize(manual = true)
            PrivacyModeState.active shouldBe true

            PrivacyModeState.setManual(false)
            PrivacyModeState.active shouldBe false
            PrivacyModeState.observeOrdinaryPersistence() shouldNotBe null
        }

        "manual mode remains active after an editor ends and ordinary mode resumes when disabled" {
            PrivacyModeState.setManual(true)
            PrivacyModeState.beginInput(31, PrivacyMode(manual = true))
            PrivacyModeState.endInput(31)
            PrivacyModeState.active shouldBe true

            PrivacyModeState.setManual(false)
            PrivacyModeState.active shouldBe false
            val permit = requireNotNull(PrivacyModeState.acquirePersistencePermit())
            PrivacyModeState.allowsPersistence(permit) shouldBe true
            PrivacyModeState.releasePersistencePermit(permit)
        }

        "turning manual privacy off stays private until the native editor update succeeds" {
            PrivacyModeState.setManual(true)
            PrivacyModeState.beginInput(41, PrivacyMode(manual = true))

            PrivacyModeState.setManual(false)
            PrivacyModeState.active shouldBe true
            PrivacyModeState.acquirePersistencePermit().shouldBeNull()

            PrivacyModeState.updateInput(41, PrivacyMode()) shouldBe true
            PrivacyModeState.active shouldBe false
        }

        "a private to ordinary editor transition stays private until its owner updates" {
            PrivacyModeState.beginInput(51, PrivacyMode(password = true))
            PrivacyModeState.beginInput(
                52,
                PrivacyMode(),
                retainPrivacyUntilUpdate = PrivacyModeState.active,
            )

            PrivacyModeState.active shouldBe true
            PrivacyModeState.updateInput(51, PrivacyMode()) shouldBe false
            PrivacyModeState.active shouldBe true

            PrivacyModeState.updateInput(52, PrivacyMode()) shouldBe true
            PrivacyModeState.active shouldBe false
        }

        "a completed native bind cannot overwrite a newer manual privacy request" {
            PrivacyModeState.beginInput(61, PrivacyMode())
            PrivacyModeState.setManual(true)

            PrivacyModeState.updateInput(61, PrivacyMode()) shouldBe true
            PrivacyModeState.active shouldBe true
            PrivacyModeState.acquirePersistencePermit().shouldBeNull()
        }

        "ordinary state is not observable until its private clipboard boundary is armed" {
            PrivacyModeState.beginInput(71, PrivacyMode(password = true))
            val releaseEntered = CountDownLatch(1)
            val allowBoundary = CountDownLatch(1)
            val observerAttempted = CountDownLatch(1)
            val observerFinished = CountDownLatch(1)
            val boundaryArmed = AtomicBoolean(false)
            val observation = AtomicReference<Long?>()
            val observerSawBoundary = AtomicBoolean(false)

            val releasing = thread(name = "privacy-release") {
                PrivacyModeState.updateInput(71, PrivacyMode()) {
                    releaseEntered.countDown()
                    check(allowBoundary.await(5, TimeUnit.SECONDS))
                    boundaryArmed.set(true)
                }
            }
            check(releaseEntered.await(5, TimeUnit.SECONDS))
            val observing = thread(name = "persistence-observer") {
                observerAttempted.countDown()
                observation.set(PrivacyModeState.observeOrdinaryPersistence())
                observerSawBoundary.set(boundaryArmed.get())
                observerFinished.countDown()
            }
            check(observerAttempted.await(5, TimeUnit.SECONDS))
            try {
                observerFinished.await(100, TimeUnit.MILLISECONDS) shouldBe false
            } finally {
                allowBoundary.countDown()
                releasing.join(5_000)
                observing.join(5_000)
            }

            releasing.isAlive shouldBe false
            observing.isAlive shouldBe false
            observation.get() shouldNotBe null
            observerSawBoundary.get() shouldBe true
            PrivacyModeState.active shouldBe false
        }

        "a clipboard observation cannot acquire after crossing a private generation" {
            val staleObservation = requireNotNull(PrivacyModeState.observeOrdinaryPersistence())
            PrivacyModeState.beginInput(81, PrivacyMode(password = true))
            var boundaryArmed = false

            PrivacyModeState.endInput(81) {
                PrivacyModeState.active shouldBe true
                PrivacyModeState.observeOrdinaryPersistence().shouldBeNull()
                boundaryArmed = true
            }

            boundaryArmed shouldBe true
            PrivacyModeState.acquirePersistencePermit(staleObservation).shouldBeNull()
            val currentObservation = requireNotNull(PrivacyModeState.observeOrdinaryPersistence())
            val permit = requireNotNull(PrivacyModeState.acquirePersistencePermit(currentObservation))
            PrivacyModeState.releasePersistencePermit(permit)
        }

        "publishing a granted lease is atomic with a private transition" {
            val permit = requireNotNull(PrivacyModeState.acquirePersistencePermit())
            val publishEntered = CountDownLatch(1)
            val allowPublish = CountDownLatch(1)
            val transitionAttempted = CountDownLatch(1)
            val transitionFinished = CountDownLatch(1)
            val published = AtomicBoolean(false)
            val publishResult = AtomicReference<Boolean?>()
            val publishFailure = AtomicReference<Throwable?>()

            val publishing = thread(name = "ordinary-publisher") {
                runCatching {
                    PrivacyModeState.publishIfOrdinary(permit) {
                        publishEntered.countDown()
                        check(allowPublish.await(5, TimeUnit.SECONDS))
                        published.set(true)
                    }
                }.fold(publishResult::set, publishFailure::set)
            }
            check(publishEntered.await(5, TimeUnit.SECONDS))
            val transitioning = thread(name = "private-transition") {
                transitionAttempted.countDown()
                PrivacyModeState.beginInput(91, PrivacyMode(password = true))
                transitionFinished.countDown()
            }
            check(transitionAttempted.await(5, TimeUnit.SECONDS))
            try {
                transitionFinished.await(100, TimeUnit.MILLISECONDS) shouldBe false
            } finally {
                allowPublish.countDown()
                publishing.join(5_000)
                transitioning.join(5_000)
            }

            publishing.isAlive shouldBe false
            transitioning.isAlive shouldBe false
            publishFailure.get().shouldBeNull()
            publishResult.get() shouldBe true
            published.get() shouldBe true
            PrivacyModeState.active shouldBe true

            var leakedAfterBoundary = false
            PrivacyModeState.publishIfOrdinary(permit) {
                leakedAfterBoundary = true
            } shouldBe false
            leakedAfterBoundary shouldBe false
            PrivacyModeState.allowsPersistence(permit) shouldBe true
            PrivacyModeState.releasePersistencePermit(permit)
        }
    })
