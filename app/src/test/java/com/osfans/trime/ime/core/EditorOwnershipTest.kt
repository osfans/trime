/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class EditorOwnershipTest :
    StringSpec({
        "rapid editor changes retain every possible native owner" {
            val generation = EditorGeneration()
            val first = generation.token
            val ownership = EditorOwnership().apply { add(first) }
            val second = generation.advance()
            ownership.add(second)
            ownership.add(second)

            generation.accepts(first) shouldBe false
            ownership.snapshot().asList() shouldContainExactly listOf(first, second)
        }

        "zero cannot become an editor owner" {
            shouldThrow<IllegalArgumentException> { EditorOwnership().add(0) }
        }

        "each service generation gets a distinct daemon session" {
            val generation = EditorGeneration()
            val firstToken = generation.token
            val first = imeRimeSessionName("TrimeInputMethodService", firstToken)
            val secondToken = generation.advance()
            val second = imeRimeSessionName("TrimeInputMethodService", secondToken)

            (first == second) shouldBe false
            first shouldBe "TrimeInputMethodService#$firstToken"
            second shouldBe "TrimeInputMethodService#$secondToken"
        }

        "zero cannot identify a daemon session" {
            shouldThrow<IllegalArgumentException> {
                imeRimeSessionName("TrimeInputMethodService", 0)
            }
        }

        "content caches stay hidden until the current ordinary editor is natively bound" {
            canAccessEditorContent(
                editorActive = true,
                policyAllowsContent = true,
                processPrivacyActive = false,
                currentEditorToken = 91,
                nativeBoundEditorToken = 90,
            ) shouldBe false
            canAccessEditorContent(
                editorActive = true,
                policyAllowsContent = true,
                processPrivacyActive = false,
                currentEditorToken = 91,
                nativeBoundEditorToken = 91,
            ) shouldBe true
        }

        "local and transitional process privacy both block content caches" {
            canAccessEditorContent(
                editorActive = true,
                policyAllowsContent = false,
                processPrivacyActive = false,
                currentEditorToken = 101,
                nativeBoundEditorToken = 101,
            ) shouldBe false
            canAccessEditorContent(
                editorActive = true,
                policyAllowsContent = true,
                processPrivacyActive = true,
                currentEditorToken = 101,
                nativeBoundEditorToken = 101,
            ) shouldBe false
        }
    })
