// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.core.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.io.path.createTempDirectory

class OrphanHistoryTest :
    StringSpec({
        fun fixture(block: (File) -> Unit) {
            val root = createTempDirectory().toFile()
            try {
                block(root)
            } finally {
                root.deleteRecursively()
            }
        }
        fun record(file: File) = SyncEntry(file.length(), file.lastModified(), SyncFingerprint.of(file))

        "fresh or switched trees preserve untracked local files and empty directories" {
            fixture { root ->
                val personal = File(root, "personal.custom.yaml").apply { writeText("personal") }
                val directory = File(root, "empty-personal").apply { mkdir() }
                UserDataManager.removeLocalOrphans(root, emptySet()).deleted shouldBe 0
                personal.readText() shouldBe "personal"
                directory.isDirectory shouldBe true
            }
        }
        "local size changes survive an external deletion" {
            fixture { root ->
                val file = File(root, "edited.yaml").apply { writeText("old") }
                val entry = record(file)
                file.writeText("local edits")
                file.setLastModified(entry.lastModified)
                UserDataManager.removeLocalOrphans(root, emptySet(), knownEntries = mapOf(file.name to entry)).deleted shouldBe 0
                file.readText() shouldBe "local edits"
            }
        }
        "same size local edits survive through their modification time" {
            fixture { root ->
                val file = File(root, "edited.yaml").apply { writeText("old") }
                val entry = record(file)
                file.writeText("new")
                check(file.setLastModified(entry.lastModified + 2000))
                UserDataManager.removeLocalOrphans(root, emptySet(), knownEntries = mapOf(file.name to entry)).deleted shouldBe 0
                file.readText() shouldBe "new"
            }
        }
        "unknown provider metadata cannot authorize deletion" {
            fixture { root ->
                val file = File(root, "unknown.yaml").apply { writeText("retained") }
                for (entry in listOf(SyncEntry(-1, file.lastModified()), SyncEntry(file.length(), 0))) {
                    UserDataManager.removeLocalOrphans(root, emptySet(), knownEntries = mapOf(file.name to entry)).deleted shouldBe 0
                    file.exists() shouldBe true
                }
            }
        }
        "legacy metadata-only history never authorizes deletion" {
            fixture { root ->
                val file = File(root, "legacy.yaml").apply { writeText("retained") }
                val entry = SyncEntry(file.length(), file.lastModified())
                UserDataManager.removeLocalOrphans(root, emptySet(), knownEntries = mapOf(file.name to entry)).deleted shouldBe 0
                file.exists() shouldBe true
            }
        }
        "same-size edits with unchanged coarse timestamps are detected by content" {
            fixture { root ->
                val file = File(root, "edited.yaml").apply { writeText("old") }
                val entry = record(file)
                file.writeText("new")
                check(file.setLastModified(entry.lastModified))
                UserDataManager.removeLocalOrphans(root, emptySet(), knownEntries = mapOf(file.name to entry)).deleted shouldBe 0
                file.readText() shouldBe "new"
            }
        }
        "only proven unchanged external deletions prune their own empty parents" {
            fixture { root ->
                val file = File(root, "tracked/orphan.yaml").apply {
                    parentFile!!.mkdir()
                    writeText("old")
                }
                val unrelated = File(root, "unrelated").apply { mkdir() }
                val entries = mapOf("tracked/orphan.yaml" to record(file))
                UserDataManager.removeLocalOrphans(root, entries.keys, knownEntries = entries).deleted shouldBe 0
                UserDataManager.removeLocalOrphans(root, emptySet(), knownEntries = entries).deleted shouldBe 2
                file.exists() shouldBe false
                unrelated.isDirectory shouldBe true
            }
        }
        "synchronized history never overrides installation identity protection" {
            fixture { root ->
                val file = File(root, "installation.yaml").apply { writeText("identity") }
                UserDataManager.removeLocalOrphans(root, emptySet(), knownEntries = mapOf(file.name to record(file))).deleted shouldBe 0
                file.readText() shouldBe "identity"
            }
        }
    })
