/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.core

import com.osfans.trime.ime.core.InlinePreeditMode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class EditorPreeditResponseTest :
    StringSpec({
        val spelling = CompositionProto(6, 3, 0, 6, "ni'hao", "你好")

        "disabled inline preedit hides text and cursor without discarding the composition" {
            for (t9 in listOf(false, true)) {
                editorPreeditResponse(spelling, InlinePreeditMode.DISABLE, t9) shouldBe
                    EditorPreeditResponse(InlinePreeditProto(""), spelling)
            }
        }

        "composing mode publishes the displayed spelling and cursor without a duplicate panel" {
            for (t9 in listOf(false, true)) {
                editorPreeditResponse(spelling, InlinePreeditMode.COMPOSING_TEXT, t9) shouldBe
                    EditorPreeditResponse(InlinePreeditProto("ni'hao", 3), CompositionProto())
            }
        }

        "ordinary commit preview has no spelling cursor mapping" {
            editorPreeditResponse(spelling, InlinePreeditMode.COMMIT_TEXT_PREVIEW, false) shouldBe
                EditorPreeditResponse(InlinePreeditProto("你好"), spelling)
        }

        "nine-key commit preview uses the editable spelling rather than the unselected Hanzi" {
            editorPreeditResponse(spelling, InlinePreeditMode.COMMIT_TEXT_PREVIEW, true) shouldBe
                EditorPreeditResponse(InlinePreeditProto("ni'hao", 3), spelling)
        }

        "displayed cursor offsets remain UTF16 when the spelling contains non BMP text" {
            val text = "a汉😀b"
            val composition = CompositionProto(text.length, 4, 0, text.length, text, "候选")
            editorPreeditResponse(composition, InlinePreeditMode.COMMIT_TEXT_PREVIEW, true).inlinePreedit shouldBe
                InlinePreeditProto(text, 4)
        }

        "missing spelling and preview become empty text instead of a literal null" {
            val empty = CompositionProto()
            editorPreeditResponse(empty, InlinePreeditMode.COMPOSING_TEXT, true) shouldBe
                EditorPreeditResponse(InlinePreeditProto("", 0), CompositionProto())
            editorPreeditResponse(empty, InlinePreeditMode.COMMIT_TEXT_PREVIEW, false) shouldBe
                EditorPreeditResponse(InlinePreeditProto(""), empty)
        }
    })
