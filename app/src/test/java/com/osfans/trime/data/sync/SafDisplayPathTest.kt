// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SafDisplayPathTest :
    StringSpec({
        "fromDocumentId maps primary volume to path without emulated prefix" {
            SafDisplayPath.fromDocumentId("primary:Documents/rime") shouldBe "/Documents/rime"
        }
        "fromDocumentId maps raw document id to path without emulated prefix" {
            SafDisplayPath.fromDocumentId("raw:/storage/emulated/0/Download/rime") shouldBe "/Download/rime"
        }
        "fromDocumentId maps SD card volume to storage path" {
            SafDisplayPath.fromDocumentId("ABCD-1234:Music/rime") shouldBe "/storage/ABCD-1234/Music/rime"
        }
        "fromDocumentId maps emulated root to slash" {
            SafDisplayPath.fromDocumentId("primary:") shouldBe "/"
            SafDisplayPath.fromDocumentId("raw:/storage/emulated/0") shouldBe "/"
        }
        "fromDocumentId returns null for unknown document id format" {
            SafDisplayPath.fromDocumentId("drive-root") shouldBe null
        }
        "stripEmulatedStoragePrefix leaves non-emulated paths unchanged" {
            SafDisplayPath.stripEmulatedStoragePrefix("/storage/ABCD-1234/Music/rime") shouldBe
                "/storage/ABCD-1234/Music/rime"
        }
    })
