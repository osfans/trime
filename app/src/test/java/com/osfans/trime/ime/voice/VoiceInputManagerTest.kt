// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.voice

import android.content.Context
import android.speech.SpeechRecognizer
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify

class VoiceInputManagerTest :
    BehaviorSpec({
        Given("A mocked Android context") {
            val context = mockk<Context>(relaxed = true)
            var receivedResult: String? = null
            var receivedError: String? = null

            val onResult: (String) -> Unit = { receivedResult = it }
            val onError: (String) -> Unit = { receivedError = it }

            When("VoiceInputManager is created") {
                val voiceInputManager = VoiceInputManager.create(context, onResult, onError)

                Then("it should not be null") {
                    voiceInputManager shouldNotBe null
                }

                And("when checking if voice input is available") {
                    // Mock that speech recognition is available
                    mockkStatic(SpeechRecognizer::class)
                    every { SpeechRecognizer.isRecognitionAvailable(context) } returns true

                    Then("it should return true") {
                        VoiceInputManager.isVoiceInputAvailable(context) shouldBe true
                    }
                }

                And("when speech recognition is not available") {
                    every { SpeechRecognizer.isRecognitionAvailable(context) } returns false

                    Then("it should return false") {
                        VoiceInputManager.isVoiceInputAvailable(context) shouldBe false
                    }
                }

                And("when destroyed") {
                    voiceInputManager.destroy()

                    Then("isListening should return false") {
                        voiceInputManager.isListening() shouldBe false
                    }
                }
            }
        }
    })