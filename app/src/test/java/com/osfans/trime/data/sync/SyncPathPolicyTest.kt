// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SyncPathPolicyTest :
    StringSpec({
        "import skips installation.yaml" {
            SyncPathPolicy.shouldImport("installation.yaml", "phone-a") shouldBe false
            SyncPathPolicy.shouldImport("installation.yaml", null) shouldBe false
        }
        "import skips own sync folder" {
            SyncPathPolicy.shouldImport("sync/phone-a/luna.userdb.txt", "phone-a") shouldBe false
            SyncPathPolicy.shouldImport("sync/phone-a", "phone-a") shouldBe false
        }
        "import allows peer sync folder and other files" {
            SyncPathPolicy.shouldImport("sync/phone-b/luna.userdb.txt", "phone-a") shouldBe true
            SyncPathPolicy.shouldImport("default.custom.yaml", "phone-a") shouldBe true
            SyncPathPolicy.shouldImport("sync/phone-b/luna.userdb.txt", null) shouldBe true
        }
        "ownSyncPrefix is sync/<id>" {
            SyncPathPolicy.ownSyncPrefix("phone-a") shouldBe "sync/phone-a"
        }
        "preserve installation.yaml always" {
            SyncPathPolicy.shouldPreserveLocal("installation.yaml", "phone-a") shouldBe true
            SyncPathPolicy.shouldPreserveLocal("installation.yaml", null) shouldBe true
            SyncPathPolicy.shouldPreserveLocal("default.custom.yaml", "phone-a") shouldBe false
        }
        "preserve own sync folder when ownId is known" {
            SyncPathPolicy.shouldPreserveLocal("sync/phone-a/luna.userdb.txt", "phone-a") shouldBe true
            SyncPathPolicy.shouldPreserveLocal("sync/phone-a", "phone-a") shouldBe true
            SyncPathPolicy.shouldPreserveLocal("sync/phone-b/luna.userdb.txt", "phone-a") shouldBe false
            SyncPathPolicy.shouldPreserveLocal("sync/phone-a/luna.userdb.txt", null) shouldBe false
        }
        "readInstallationId parses yaml text" {
            SyncPathPolicy.readInstallationId(
                """
                installation_id: "abc-123"
                distribution_code_name: trime
                """.trimIndent(),
            ) shouldBe "abc-123"
            SyncPathPolicy.readInstallationId("distribution_code_name: trime") shouldBe null
        }
    })
