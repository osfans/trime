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
        "should skip directories whose name contains .userdb but not .userdb.yaml files" {
            SafTreeWalker.shouldSkip("luna_pinyin.userdb", isDirectory = true) shouldBe true
            SafTreeWalker.shouldSkip("foo/luna_pinyin.userdb/user.kct") shouldBe true
            SafTreeWalker.shouldSkip("luna_pinyin.userdb.yaml") shouldBe false
        }
        "skipPrefix drops that path and descendants but not sibling prefixes" {
            val skip = "sync/phone-a"
            SafTreeWalker.shouldVisit("sync/phone-a", skipPrefix = skip) shouldBe false
            SafTreeWalker.shouldVisit("sync/phone-a/luna.userdb.txt", skipPrefix = skip) shouldBe false
            SafTreeWalker.shouldVisit("sync/phone-abc/luna.userdb.txt", skipPrefix = skip) shouldBe true
            SafTreeWalker.shouldVisit("sync/phone-b/luna.userdb.txt", skipPrefix = skip) shouldBe true
            SafTreeWalker.shouldVisit("default.custom.yaml", skipPrefix = skip) shouldBe true
        }
        "limitToPrefix keeps ancestors, the prefix, and descendants" {
            val limit = "sync/phone-a"
            SafTreeWalker.shouldVisit("sync", limitToPrefix = limit) shouldBe true
            SafTreeWalker.shouldVisit("sync/phone-a", limitToPrefix = limit) shouldBe true
            SafTreeWalker.shouldVisit("sync/phone-a/luna.userdb.txt", limitToPrefix = limit) shouldBe true
            SafTreeWalker.shouldVisit("sync/phone-b", limitToPrefix = limit) shouldBe false
            SafTreeWalker.shouldVisit("sync/phone-b/luna.userdb.txt", limitToPrefix = limit) shouldBe false
            SafTreeWalker.shouldVisit("default.custom.yaml", limitToPrefix = limit) shouldBe false
        }
        "blank prefixes do not filter" {
            SafTreeWalker.shouldVisit("sync/phone-a/foo.txt") shouldBe true
            SafTreeWalker.shouldVisit("sync/phone-a/foo.txt", skipPrefix = "") shouldBe true
            SafTreeWalker.shouldVisit("default.custom.yaml", limitToPrefix = "") shouldBe true
        }
    })
