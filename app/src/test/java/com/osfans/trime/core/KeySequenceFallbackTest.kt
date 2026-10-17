/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.core

import com.osfans.trime.ime.keyboard.keyboardMacroParts
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class KeySequenceFallbackTest :
    StringSpec({
        "empty control macros and mixed control text never submit their command source" {
            listOf("{Escape}", "{Shift_R}{Release+Shift_R}", "{Control+a}", "{Escape}/fh", "a{BackSpace}b").forEach {
                literalKeySequenceFallback(it) shouldBe null
            }
        }

        "keyboard macro tokenization retains a control prefix rather than creating literal fallback" {
            val parts = keyboardMacroParts("{Escape}/fh{Control+a}{}{")
            parts shouldBe listOf("{Escape}/fh", "{Control+a}", "{braceleft}", "{braceright}", "{")
            parts.mapNotNull(::literalKeySequenceFallback) shouldBe listOf("{")
        }

        "plain symbols text and literal terminal braces retain their exact fallback" {
            listOf("@./", "Wi-Fi", "你好🙂", "}", "{", "abc{").forEach {
                literalKeySequenceFallback(it) shouldBe it
            }
            literalKeySequenceFallback("") shouldBe null
        }
    })
