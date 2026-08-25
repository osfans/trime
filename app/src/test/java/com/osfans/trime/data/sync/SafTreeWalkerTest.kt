// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SafTreeWalkerTest :
    StringSpec({
        "should skip any build directory in the path" {
            SafTreeWalker.shouldSkip("build") shouldBe true
            SafTreeWalker.shouldSkip("foo/build") shouldBe true
            SafTreeWalker.shouldSkip("foo/build/bar.txt") shouldBe true
            SafTreeWalker.shouldSkip("foo/src/Bar.kt") shouldBe false
        }
    })
