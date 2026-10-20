/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

import android.text.InputType
import android.view.inputmethod.EditorInfo
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private val enterThemeLabels = mapOf(
    EditorInfo.IME_ACTION_GO to "Theme go",
    EditorInfo.IME_ACTION_SEARCH to "Theme search",
    EditorInfo.IME_ACTION_SEND to "Theme send",
    EditorInfo.IME_ACTION_NEXT to "Theme next",
    EditorInfo.IME_ACTION_DONE to "Theme done",
    EditorInfo.IME_ACTION_PREVIOUS to "Theme previous",
)

private fun enterLabel(action: Int?, appLabel: CharSequence?, mode: Int): String = EditorEnterAction.label(action, appLabel, mode, "Theme return", enterThemeLabels::get)

class EditorEnterActionTest :
    StringSpec({
        "standard actions use their matching theme labels after unrelated option flags are masked" {
            for ((id, expectedLabel) in enterThemeLabels) {
                val action = EditorEnterAction.resolve(
                    InputType.TYPE_CLASS_TEXT,
                    id or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NAVIGATE_NEXT,
                    EditorInfo.IME_ACTION_UNSPECIFIED,
                    null,
                )
                action shouldBe id
                enterLabel(action, null, 0) shouldBe expectedLabel
            }
        }

        "NONE and UNSPECIFIED send a Return key and show the default instead of an app label" {
            for (id in listOf(EditorInfo.IME_ACTION_NONE, EditorInfo.IME_ACTION_UNSPECIFIED)) {
                val action = EditorEnterAction.resolve(InputType.TYPE_CLASS_TEXT, id, 0, "App action")
                action shouldBe null
                for (mode in 0..3) enterLabel(action, "App action", mode) shouldBe "Theme return"
            }
        }

        "a labeled nonstandard custom ID wins over the search option for both action and label" {
            val action = EditorEnterAction.resolve(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEARCH, 42, "Approve")
            action shouldBe 42
            // Mode 0 must not misleadingly show Search for a custom action it cannot name.
            enterLabel(action, "Approve", 0) shouldBe "Theme return"
            for (mode in 1..3) enterLabel(action, "Approve", mode) shouldBe "Approve"
        }

        "a custom ID in the standard action set uses that action's theme label" {
            val action = EditorEnterAction.resolve(
                InputType.TYPE_CLASS_TEXT,
                EditorInfo.IME_ACTION_SEARCH,
                EditorInfo.IME_ACTION_SEND,
                "App delivery",
            )
            action shouldBe EditorInfo.IME_ACTION_SEND
            enterLabel(action, "App delivery", 0) shouldBe "Theme send"
            enterLabel(action, "App delivery", 1) shouldBe "App delivery"
            enterLabel(action, "App delivery", 2) shouldBe "App delivery"
            enterLabel(action, "App delivery", 3) shouldBe "Theme send"
        }

        "a missing custom label does not make actionId override the standard option" {
            for (label in listOf(null, "")) {
                val action = EditorEnterAction.resolve(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEND, 42, label)
                action shouldBe EditorInfo.IME_ACTION_SEND
                enterLabel(action, label, 0) shouldBe "Theme send"
                enterLabel(action, label, 2) shouldBe "Theme send"
            }
        }

        "an unspecified custom ID retains the editor's standard action" {
            val action = EditorEnterAction.resolve(
                InputType.TYPE_CLASS_TEXT,
                EditorInfo.IME_ACTION_SEARCH,
                EditorInfo.IME_ACTION_UNSPECIFIED,
                "Find in document",
            )
            action shouldBe EditorInfo.IME_ACTION_SEARCH
            enterLabel(action, "Find in document", 0) shouldBe "Theme search"
            enterLabel(action, "Find in document", 2) shouldBe "Find in document"
        }

        "NO_ENTER_ACTION suppresses both standard and custom actions regardless of label mode" {
            for (custom in listOf(false, true)) {
                val action = EditorEnterAction.resolve(
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                    EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION,
                    if (custom) 42 else EditorInfo.IME_ACTION_UNSPECIFIED,
                    if (custom) "Approve" else null,
                )
                action shouldBe null
                for (mode in 0..3) enterLabel(action, "Approve", mode) shouldBe "Theme return"
            }
        }

        "TYPE_NULL always sends a Return key even when an app supplies an action" {
            val action = EditorEnterAction.resolve(InputType.TYPE_NULL, EditorInfo.IME_ACTION_SEARCH, 42, "Approve")
            action shouldBe null
            for (mode in 0..3) enterLabel(action, "Approve", mode) shouldBe "Theme return"
        }

        "a multiline flag alone does not discard an explicit Send action" {
            val type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            EditorEnterAction.resolve(type, EditorInfo.IME_ACTION_SEND, 0, null) shouldBe EditorInfo.IME_ACTION_SEND
            EditorEnterAction.resolve(type, EditorInfo.IME_ACTION_UNSPECIFIED, 0, null) shouldBe null
            enterLabel(EditorInfo.IME_ACTION_SEND, null, 0) shouldBe "Theme send"
        }

        "app-only labels never show null while app-preferred mode falls back to the theme action" {
            for (label in listOf(null, "")) {
                enterLabel(EditorInfo.IME_ACTION_SEARCH, label, 1) shouldBe "Theme return"
                enterLabel(EditorInfo.IME_ACTION_SEARCH, label, 2) shouldBe "Theme search"
            }
            enterLabel(EditorInfo.IME_ACTION_SEARCH, "App search", 0) shouldBe "Theme search"
            enterLabel(EditorInfo.IME_ACTION_SEARCH, "App search", 1) shouldBe "App search"
            enterLabel(EditorInfo.IME_ACTION_SEARCH, "App search", 2) shouldBe "App search"
            enterLabel(EditorInfo.IME_ACTION_SEARCH, "App search", 3) shouldBe "Theme search"
        }

        "invalid theme modes retain the existing never-use-app-label fallback" {
            for (mode in listOf(-1, 4, Int.MAX_VALUE)) {
                enterLabel(EditorInfo.IME_ACTION_SEARCH, "App search", mode) shouldBe "Theme search"
                enterLabel(42, "Approve", mode) shouldBe "Theme return"
            }
        }

        "a labeled actionId equal to NONE preserves the existing explicit custom-ID dispatch" {
            val action = EditorEnterAction.resolve(InputType.TYPE_CLASS_TEXT, EditorInfo.IME_ACTION_SEND, EditorInfo.IME_ACTION_NONE, "App action")
            action shouldBe EditorInfo.IME_ACTION_NONE
            enterLabel(action, "App action", 2) shouldBe "App action"
            enterLabel(action, "App action", 0) shouldBe "Theme return"
        }
    })
