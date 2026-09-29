// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RimeDataSyncPathTest :
    StringSpec({
        "should skip any build directory in the path" {
            RimeDataSync.shouldSkip("build") shouldBe true
            RimeDataSync.shouldSkip("foo/build") shouldBe true
            RimeDataSync.shouldSkip("foo/build/bar.txt") shouldBe true
            RimeDataSync.shouldSkip("foo/src/Bar.kt") shouldBe false
        }
        "should skip directories whose name contains .userdb but not .userdb.yaml files" {
            RimeDataSync.shouldSkip("luna_pinyin.userdb", isDirectory = true) shouldBe true
            RimeDataSync.shouldSkip("foo/luna_pinyin.userdb/user.kct") shouldBe true
            RimeDataSync.shouldSkip("luna_pinyin.userdb.yaml") shouldBe false
        }
        "skipPrefix drops that path and descendants but not sibling prefixes" {
            val skip = "sync/phone-a"
            RimeDataSync.shouldVisit("sync/phone-a", skipPrefix = skip) shouldBe false
            RimeDataSync.shouldVisit("sync/phone-a/luna.userdb.txt", skipPrefix = skip) shouldBe false
            RimeDataSync.shouldVisit("sync/phone-abc/luna.userdb.txt", skipPrefix = skip) shouldBe true
            RimeDataSync.shouldVisit("sync/phone-b/luna.userdb.txt", skipPrefix = skip) shouldBe true
            RimeDataSync.shouldVisit("default.custom.yaml", skipPrefix = skip) shouldBe true
        }
        "blank prefixes do not filter" {
            RimeDataSync.shouldVisit("sync/phone-a/foo.txt") shouldBe true
            RimeDataSync.shouldVisit("sync/phone-a/foo.txt", skipPrefix = "") shouldBe true
        }
    })
