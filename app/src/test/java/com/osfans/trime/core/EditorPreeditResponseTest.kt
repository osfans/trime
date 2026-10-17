/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.core

import com.osfans.trime.ime.core.InlinePreeditMode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class EditorPreeditResponseTest :
    StringSpec({
        "next-word suggestions never become editor composition or inline preview in any mode" {
            val suggestion = CompositionProto(preedit = "‸", commitTextPreview = "世界")
            for (mode in InlinePreeditMode.entries) {
                for (t9 in listOf(false, true)) {
                    editorPreeditResponse(suggestion, mode, t9, true) shouldBe
                        EditorPreeditResponse(InlinePreeditProto(""), CompositionProto())
                }
            }
        }

        "editor text readers omit unselected suggestions while retaining ordinary input" {
            val suggestion = CompositionProto(preedit = "‸", commitTextPreview = "世界")
            compositionForEditor(suggestion, true) shouldBe CompositionProto()
            val normal = CompositionProto(5, 2, 0, 5, "nihao", "你好")
            compositionForEditor(normal, false) shouldBe normal
        }

        "ordinary inline modes preserve raw cursor and commit preview behavior" {
            val normal = CompositionProto(5, 2, 0, 5, "nihao", "你好")
            for (t9 in listOf(false, true)) {
                editorPreeditResponse(normal, InlinePreeditMode.DISABLE, t9, false) shouldBe
                    EditorPreeditResponse(InlinePreeditProto(""), normal)
                editorPreeditResponse(normal, InlinePreeditMode.COMPOSING_TEXT, t9, false) shouldBe
                    EditorPreeditResponse(InlinePreeditProto("nihao", 2), CompositionProto())
            }
            editorPreeditResponse(normal, InlinePreeditMode.COMMIT_TEXT_PREVIEW, false, false) shouldBe
                EditorPreeditResponse(InlinePreeditProto("你好"), normal)
            editorPreeditResponse(normal, InlinePreeditMode.COMMIT_TEXT_PREVIEW, true, false) shouldBe
                EditorPreeditResponse(InlinePreeditProto("nihao", 2), normal)
        }
    })
