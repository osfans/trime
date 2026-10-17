// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class UserDataManagerPathTest :
    StringSpec({
        "should skip any build directory in the path" {
            UserDataManager.shouldSkip("build") shouldBe true
            UserDataManager.shouldSkip("foo/build") shouldBe true
            UserDataManager.shouldSkip("foo/build/bar.txt") shouldBe true
            UserDataManager.shouldSkip("foo/src/Bar.kt") shouldBe false
        }
        "should skip directories whose name contains .userdb but not .userdb.yaml files" {
            UserDataManager.shouldSkip("luna_pinyin.userdb", isDirectory = true) shouldBe true
            UserDataManager.shouldSkip("foo/luna_pinyin.userdb/user.kct") shouldBe true
            UserDataManager.shouldSkip("luna_pinyin.userdb.yaml") shouldBe false
        }
        "skipPrefix drops that path and descendants but not sibling prefixes" {
            val skip = "sync/phone-a"
            UserDataManager.shouldVisit("sync/phone-a", skipPrefix = skip) shouldBe false
            UserDataManager.shouldVisit("sync/phone-a/luna.userdb.txt", skipPrefix = skip) shouldBe false
            UserDataManager.shouldVisit("sync/phone-abc/luna.userdb.txt", skipPrefix = skip) shouldBe true
            UserDataManager.shouldVisit("sync/phone-b/luna.userdb.txt", skipPrefix = skip) shouldBe true
            UserDataManager.shouldVisit("default.custom.yaml", skipPrefix = skip) shouldBe true
        }
        "blank prefixes do not filter" {
            UserDataManager.shouldVisit("sync/phone-a/foo.txt") shouldBe true
            UserDataManager.shouldVisit("sync/phone-a/foo.txt", skipPrefix = "") shouldBe true
        }
    })
