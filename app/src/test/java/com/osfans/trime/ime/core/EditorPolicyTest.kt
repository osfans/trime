/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

import android.text.InputType
import android.view.inputmethod.EditorInfo
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class EditorPolicyTest :
    StringSpec({
        "password privacy covers text web visible and numeric passwords only" {
            listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD).forEach {
                EditorPolicy.isPassword(InputType.TYPE_CLASS_TEXT or it) shouldBe true
            }
            EditorPolicy.isPassword(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD) shouldBe true
            EditorPolicy.isPassword(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI) shouldBe false
            EditorPolicy.isPassword(InputType.TYPE_CLASS_NUMBER) shouldBe false
        }
        "privacy combines password app request and manual reasons" {
            val password = EditorPolicy.privacy(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                0,
                manual = false,
            )
            password shouldBe PrivacyMode(password = true)

            val appRequested = EditorPolicy.privacy(
                InputType.TYPE_CLASS_TEXT,
                EditorInfo.IME_ACTION_DONE or EditorPolicy.NO_PERSONALIZED_LEARNING_FLAG,
                manual = false,
            )
            appRequested shouldBe PrivacyMode(appRequested = true)

            val combined = EditorPolicy.privacy(
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                EditorPolicy.NO_PERSONALIZED_LEARNING_FLAG,
                manual = true,
            )
            combined shouldBe PrivacyMode(password = true, appRequested = true, manual = true)
            combined.allowLearning shouldBe false
            combined.allowContext shouldBe false
            combined.allowClipboardHistory shouldBe false
            combined.allowClipboardSuggestion shouldBe false
            combined.allowSpeech shouldBe false
            combined.allowContentAccess shouldBe false
        }
        "ordinary editor permits private-data features" {
            val mode = EditorPolicy.privacy(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_DONE, manual = false)
            mode.active shouldBe false
            mode.allowLearning shouldBe true
            mode.allowContext shouldBe true
            mode.allowClipboardHistory shouldBe true
            mode.allowClipboardSuggestion shouldBe true
            mode.allowSpeech shouldBe true
            mode.allowContentAccess shouldBe true
        }
        "chat search and multiline keep the user's keyboard" {
            listOf(
                InputType.TYPE_CLASS_TEXT,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
            ).forEach {
                EditorPolicy.keyboard(it, EditorInfo.IME_ACTION_SEARCH) shouldBe EditorKeyboard.USER
            }
        }
        "URI email and all password variants use a temporary ASCII keyboard" {
            listOf(
                InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            ).forEach {
                EditorPolicy.keyboard(InputType.TYPE_CLASS_TEXT or it, 0) shouldBe EditorKeyboard.ASCII
            }
        }
        "phone decimal signed and numeric passwords stay numeric even with FORCE_ASCII" {
            listOf(
                InputType.TYPE_CLASS_PHONE,
                InputType.TYPE_CLASS_NUMBER,
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED,
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            ).forEach {
                EditorPolicy.keyboard(it, EditorInfo.IME_FLAG_FORCE_ASCII) shouldBe EditorKeyboard.NUMBER
            }
        }
        "FORCE_ASCII applies to text editors" {
            EditorPolicy.keyboard(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_FLAG_FORCE_ASCII) shouldBe EditorKeyboard.ASCII
        }
        "all standard editor actions use the masked action" {
            (EditorInfo.IME_ACTION_GO..EditorInfo.IME_ACTION_PREVIOUS).forEach {
                EditorPolicy.enterAction(InputType.TYPE_CLASS_TEXT, it or EditorInfo.IME_FLAG_NO_EXTRACT_UI, 0, null) shouldBe it
            }
        }
        "custom label and ID override standard action together" {
            EditorPolicy.enterAction(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEARCH, 731, "Continue") shouldBe 731
            EditorPolicy.enterAction(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEARCH, 731, null) shouldBe EditorInfo.IME_ACTION_SEARCH
        }
        "null type and NO_ENTER_ACTION always send Enter even with a custom ID" {
            EditorPolicy.enterAction(InputType.TYPE_NULL, EditorInfo.IME_ACTION_GO, 731, "Continue") shouldBe null
            EditorPolicy.enterAction(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION, 731, "Continue") shouldBe null
        }
        "NONE and UNSPECIFIED are real Enter keys" {
            EditorPolicy.enterAction(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_NONE, 0, null) shouldBe null
            EditorPolicy.enterAction(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_UNSPECIFIED, 0, null) shouldBe null
        }
        "focus changes reject queued output and recreated services cannot reuse an editor token" {
            val first = EditorGeneration()
            val old = first.token
            first.accepts(old) shouldBe true
            first.advance()
            first.accepts(old) shouldBe false
            EditorGeneration().accepts(first.token) shouldBe false
            first.accepts(0) shouldBe false
        }
    })
