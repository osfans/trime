// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.io.path.createTempDirectory

class UserDataManagerOrphanTest :
    StringSpec({
        "preserves installation.yaml even when missing from external listing" {
            val root = createTempDirectory().toFile()
            try {
                val installation = File(root, "installation.yaml")
                installation.writeText("installation_id: test")
                File(root, "orphan.yaml").writeText("orphan")

                val result =
                    UserDataManager.removeLocalOrphans(
                        root,
                        externalPaths = emptySet(),
                        knownEntries = trackedOrphan(root),
                    )

                installation.exists() shouldBe true
                File(root, "orphan.yaml").exists() shouldBe false
                result.deleted shouldBe 1
            } finally {
                root.deleteRecursively()
            }
        }
        "preserves own sync dumps even when missing from external listing" {
            val root = createTempDirectory().toFile()
            try {
                val ownDump = File(root, "sync/phone-a/luna.userdb.txt")
                ownDump.parentFile?.mkdirs()
                ownDump.writeText("dump")
                File(root, "orphan.yaml").writeText("orphan")

                val result =
                    UserDataManager.removeLocalOrphans(
                        root,
                        externalPaths = emptySet(),
                        ownId = "phone-a",
                        knownEntries = trackedOrphan(root),
                    )

                ownDump.exists() shouldBe true
                File(root, "orphan.yaml").exists() shouldBe false
                result.deleted shouldBe 1
            } finally {
                root.deleteRecursively()
            }
        }
        "preserves sync backups when installation identity is missing" {
            val root = createTempDirectory().toFile()
            try {
                val dump = File(root, "backups/phone-a/luna.userdb.txt")
                checkNotNull(dump.parentFile).mkdirs()
                dump.writeText("retained backup")
                File(root, "orphan.yaml").writeText("orphan")
                val result =
                    UserDataManager.removeLocalOrphans(
                        root,
                        emptySet(),
                        syncDir = "backups",
                        knownEntries = trackedOrphan(root),
                    )
                dump.readText() shouldBe "retained backup"
                result.deleted shouldBe 1
            } finally {
                root.deleteRecursively()
            }
        }
    })

private fun trackedOrphan(root: File): Map<String, SyncEntry> = File(root, "orphan.yaml").let {
    mapOf(it.name to SyncEntry(it.length(), it.lastModified(), SyncFingerprint.of(it)))
}
