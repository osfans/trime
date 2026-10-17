/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.setup

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SetupTrialTextTest :
    StringSpec({
        "unfinished pinyin, digits and an empty trial are rejected" {
            listOf(null, "", "nihao", "64426", "Android 16", "🙂").forEach {
                containsCommittedChinese(it) shouldBe false
            }
        }

        "simplified, traditional and supplementary Han commits are accepted" {
            listOf("你好", "汉字", "漢字", "B站", String(Character.toChars(0x20000))).forEach {
                containsCommittedChinese(it) shouldBe true
            }
        }

        "Han after supplementary non-Han is found without breaking surrogate pairs" {
            containsCommittedChinese("🙂你好") shouldBe true
            containsCommittedChinese("🙂 Wi-Fi") shouldBe false
        }
    })
