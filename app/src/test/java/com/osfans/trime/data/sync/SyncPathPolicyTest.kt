// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.io.path.createTempDirectory

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
        "ownSyncPrefix uses custom syncDir" {
            SyncPathPolicy.ownSyncPrefix("phone-a", "backups") shouldBe "backups/phone-a"
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
        "readSyncDir uses sync_dir or defaults to sync" {
            SyncPathPolicy.readSyncDir("sync_dir: backups") shouldBe "backups"
            SyncPathPolicy.readSyncDir("sync_dir: \"backups\"") shouldBe "backups"
            SyncPathPolicy.readSyncDir("installation_id: phone-a") shouldBe "sync"
            SyncPathPolicy.readSyncDir("sync_dir: \"\"") shouldBe "sync"
            SyncPathPolicy.readSyncDir("sync_dir: \"  \"") shouldBe "sync"
            SyncPathPolicy.readSyncDir("sync_dir: backups/") shouldBe "backups"
            SyncPathPolicy.readSyncDir("sync_dir: /sdcard/rime-sync") shouldBe "/sdcard/rime-sync"
            SyncPathPolicy.readSyncDir("sync_dir: /sdcard/rime-sync/") shouldBe "/sdcard/rime-sync"
        }
        "import skips own folder under custom syncDir" {
            SyncPathPolicy.shouldImport("backups/phone-a/luna.userdb.txt", "phone-a", "backups") shouldBe false
            SyncPathPolicy.shouldImport("backups/phone-a", "phone-a", "backups") shouldBe false
            SyncPathPolicy.shouldImport("backups/phone-b/luna.userdb.txt", "phone-a", "backups") shouldBe true
            SyncPathPolicy.shouldImport("sync/phone-a/luna.userdb.txt", "phone-a", "backups") shouldBe true
        }
        "preserve own folder under custom syncDir" {
            SyncPathPolicy.shouldPreserveLocal("backups/phone-a/luna.userdb.txt", "phone-a", "backups") shouldBe true
            SyncPathPolicy.shouldPreserveLocal("backups/phone-a", "phone-a", "backups") shouldBe true
            SyncPathPolicy.shouldPreserveLocal("backups/phone-b/luna.userdb.txt", "phone-a", "backups") shouldBe false
            SyncPathPolicy.shouldPreserveLocal("sync/phone-a/luna.userdb.txt", "phone-a", "backups") shouldBe false
        }
        "localSyncRoot joins relative dirs and keeps absolute dirs" {
            val userDataDir = createTempDirectory().toFile()
            try {
                SyncPathPolicy.localSyncRoot("backups", userDataDir).canonicalFile shouldBe
                    File(userDataDir, "backups").canonicalFile
                SyncPathPolicy.localSyncRoot("sync", userDataDir).canonicalFile shouldBe
                    File(userDataDir, "sync").canonicalFile
                val absolute = File(userDataDir, "outside-abs").apply { mkdirs() }
                SyncPathPolicy.localSyncRoot(absolute.path, userDataDir).canonicalFile shouldBe
                    absolute.canonicalFile
                SyncPathPolicy.localOwnSyncDir("phone-a", "backups", userDataDir).canonicalFile shouldBe
                    File(userDataDir, "backups/phone-a").canonicalFile
            } finally {
                userDataDir.deleteRecursively()
            }
        }
        "treeRelativeSyncDir relativizes paths under userDataDir" {
            val userDataDir = createTempDirectory().toFile()
            try {
                SyncPathPolicy.treeRelativeSyncDir("backups", userDataDir) shouldBe "backups"
                val nested = File(userDataDir, "mysync").apply { mkdirs() }
                SyncPathPolicy.treeRelativeSyncDir(nested.path, userDataDir) shouldBe "mysync"
                SyncPathPolicy.ownSyncPrefix("phone-a", nested.path, userDataDir) shouldBe "mysync/phone-a"
                val outside = createTempDirectory().toFile()
                try {
                    SyncPathPolicy.treeRelativeSyncDir(outside.path, userDataDir) shouldBe outside.name
                    SyncPathPolicy.ownSyncPrefix("phone-a", outside.path, userDataDir) shouldBe
                        "${outside.name}/phone-a"
                } finally {
                    outside.deleteRecursively()
                }
            } finally {
                userDataDir.deleteRecursively()
            }
        }
    })
