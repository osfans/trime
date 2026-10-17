/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InlineSuggestionRequestGateTest :
    StringSpec({
        "a response is accepted only for the editor that created the request" {
            var currentEditor = 7L
            val gate = InlineSuggestionRequestGate { it == currentEditor }

            gate.record(currentEditor, privateInput = false) shouldBe true
            gate.acceptedEditorToken() shouldBe currentEditor

            currentEditor = 8L
            gate.acceptedEditorToken() shouldBe null
        }

        "a privacy boundary invalidates a request without changing the editor token" {
            val currentEditor = 11L
            val gate = InlineSuggestionRequestGate { it == currentEditor }

            gate.record(currentEditor, privateInput = false) shouldBe true
            gate.invalidate()
            gate.acceptedEditorToken() shouldBe null
        }

        "a new request after a boundary restores current-editor Autofill" {
            val currentEditor = 13L
            val gate = InlineSuggestionRequestGate { it == currentEditor }

            gate.record(currentEditor, privateInput = false) shouldBe true
            gate.invalidate()
            gate.record(currentEditor, privateInput = false) shouldBe true
            gate.acceptedEditorToken() shouldBe currentEditor
        }

        "a request cannot be recorded without an active matching editor" {
            val gate = InlineSuggestionRequestGate { false }

            gate.record(0L, privateInput = false) shouldBe false
            gate.record(3L, privateInput = false) shouldBe false
            gate.acceptedEditorToken() shouldBe null
        }

        "private editors never record an inline suggestion request" {
            var currentEditor = 17L
            val gate = InlineSuggestionRequestGate { it == currentEditor }

            gate.record(currentEditor, privateInput = false) shouldBe true
            gate.acceptedEditorToken() shouldBe currentEditor

            gate.record(currentEditor, privateInput = true) shouldBe false
            gate.acceptedEditorToken() shouldBe null

            currentEditor = 18L
            gate.record(currentEditor, privateInput = false) shouldBe true
            gate.acceptedEditorToken() shouldBe currentEditor
        }
    })
