// SPDX-FileCopyrightText: 2015 - 2026 Rime community
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.userdict

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

private fun withStore(block: (File, File, DictionaryFileStore) -> Unit) {
    val root = createTempDirectory().toFile()
    try {
        val data = File(root, "rime").apply { mkdir() }
        val backups = File(root, "private-backups")
        block(data, backups, DictionaryFileStore(data, backups))
    } finally {
        root.deleteRecursively()
    }
}

private fun database(root: File): File = File(root, "luna_pinyin.userdb").apply {
    mkdir()
    File(this, "CURRENT").writeText("old manifest")
    File(this, "000001.ldb").writeText("virtual-test-phrase: old learned record")
    File(this, "empty").mkdir()
}

class DictionaryFileStoreTest :
    StringSpec({
        "failed migration restores exact records and removes newly introduced files" {
            withStore { data, backups, store ->
                val db = database(data)
                shouldThrow<IOException> {
                    store.mutate(db.name) {
                        File(db, "000001.ldb").writeText("partial")
                        File(db, "new.ldb").writeText("new record")
                        throw IOException("simulated import failure")
                    }
                }
                File(db, "CURRENT").readText() shouldBe "old manifest"
                File(db, "000001.ldb").readText() shouldBe "virtual-test-phrase: old learned record"
                File(db, "new.ldb").exists() shouldBe false
                File(db, "empty").isDirectory shouldBe true
                File(backups, "pending").exists() shouldBe false
                store.backups(db.name).size shouldBe 1
            }
        }

        "successful mutation keeps the new records and a verified recovery backup" {
            withStore { data, backups, store ->
                val db = database(data)
                store.mutate(db.name) { File(db, "CURRENT").writeText("new manifest") }
                File(db, "CURRENT").readText() shouldBe "new manifest"
                File(backups, "pending").exists() shouldBe false
                store.restore(store.latest(db.name)!!)
                File(db, "CURRENT").readText() shouldBe "old manifest"
            }
        }

        "a new process recovers a partial mutation from the persistent journal" {
            withStore { data, backups, store ->
                val db = database(data)
                val backup = store.capture(db.name)
                store.beginMutation(backup)
                db.deleteRecursively()
                db.mkdir()
                File(db, "partial.ldb").writeText("interrupted mutation")
                DictionaryFileStore(data, backups).recoverPendingMutation() shouldBe true
                File(db, "CURRENT").readText() shouldBe "old manifest"
                File(db, "partial.ldb").exists() shouldBe false
                DictionaryFileStore(data, backups).recoverPendingMutation() shouldBe false
            }
        }

        "failed creation of a new dictionary rolls back its absence" {
            withStore { data, _, store ->
                val name = "new.userdb"
                shouldThrow<IOException> {
                    store.mutate(name) {
                        File(data, name).mkdir()
                        File(data, "$name/partial.ldb").writeText("partial")
                        throw IOException("simulated failure")
                    }
                }
                File(data, name).exists() shouldBe false
            }
        }

        "cancellation rolls back and retains cancellation type" {
            withStore { data, backups, store ->
                val db = database(data)
                shouldThrow<CancellationException> {
                    store.mutate(db.name) {
                        File(db, "CURRENT").writeText("partial")
                        throw CancellationException("user cancelled")
                    }
                }
                File(db, "CURRENT").readText() shouldBe "old manifest"
                File(backups, "pending").exists() shouldBe false
            }
        }

        "corrupt backup is rejected before replacing the current dictionary" {
            withStore { data, backups, store ->
                val db = database(data)
                val backup = store.capture(db.name)
                File(backups, "${backup.id}/data/CURRENT").writeText("corrupt backup")
                File(db, "CURRENT").writeText("keep current")
                shouldThrow<IOException> { store.restore(backup) }
                File(db, "CURRENT").readText() shouldBe "keep current"
            }
        }

        "a backup cannot be recovered under a different dictionary name" {
            withStore { data, _, store ->
                val db = database(data)
                val backup = store.capture(db.name)
                shouldThrow<IOException> { store.restore(backup.copy(target = "other.userdb")) }
                File(db, "CURRENT").readText() shouldBe "old manifest"
                File(data, "other.userdb").exists() shouldBe false
            }
        }

        "retention keeps at most three backups per target and monotonically orders them" {
            withStore { data, _, store ->
                val db = database(data)
                val created = (0 until 7).map { index ->
                    File(db, "CURRENT").writeText("manifest $index")
                    store.capture(db.name)
                }
                created.zipWithNext().all { (first, second) -> first.created < second.created } shouldBe true
                store.backups(db.name).map { it.id }.toSet() shouldBe created.takeLast(3).map { it.id }.toSet()
                store.latest(db.name)?.id shouldBe created.last().id
            }
        }

        "retention preserves an older backup referenced by an unfinished mutation" {
            withStore { data, backups, store ->
                val db = database(data)
                val pending = store.capture(db.name)
                store.beginMutation(pending)
                repeat(5) { store.capture(db.name) }
                File(backups, pending.id).isDirectory shouldBe true
                store.recoverPendingMutation() shouldBe true
            }
        }

        "backup directory failures prevent mutation entirely" {
            withStore { data, backups, store ->
                val db = database(data)
                backups.writeText("not a directory")
                var mutated = false
                shouldThrow<IOException> { store.mutate(db.name) { mutated = true } }
                mutated shouldBe false
                File(db, "CURRENT").readText() shouldBe "old manifest"
            }
        }

        "provider names and parent traversal cannot become recovery paths" {
            withStore { data, _, store ->
                shouldThrow<IllegalArgumentException> { store.capture("../outside.userdb") }
                shouldThrow<IllegalArgumentException> { store.capture("/outside.userdb") }
                shouldThrow<IllegalArgumentException> { store.capture("a\\outside.userdb") }
                data.listFiles()!!.isEmpty() shouldBe true
            }
        }

        "symbolic links inside a database are rejected without following or removing their target" {
            withStore { data, backups, store ->
                val db = database(data)
                val outside = File(data.parentFile, "outside").apply { writeText("keep outside") }
                Files.createSymbolicLink(File(db, "linked.ldb").toPath(), outside.toPath())
                shouldThrow<IOException> { store.capture(db.name) }
                outside.readText() shouldBe "keep outside"
                backups.listFiles()!!.none { it.name.endsWith(".tmp") } shouldBe true
            }
        }

        "symbolic link database destinations are rejected" {
            withStore { data, _, store ->
                val outside = File(data.parentFile, "outside-db").apply { mkdir() }
                Files.createSymbolicLink(File(data, "luna.userdb").toPath(), outside.toPath())
                shouldThrow<IOException> { store.capture("luna.userdb") }
                outside.isDirectory shouldBe true
            }
        }

        "trusted Android style ancestor aliases resolve while child links remain forbidden" {
            val root = createTempDirectory().toFile()
            try {
                val actual = File(root, "actual").apply { mkdir() }
                val alias = File(root, "alias")
                Files.createSymbolicLink(alias.toPath(), actual.toPath())
                val data = File(alias, "rime").apply { mkdir() }
                val backup = File(alias, "files/userdict-backups")
                val store = DictionaryFileStore(data, backup)
                val db = database(data)
                val captured = store.capture(db.name)
                File(db, "CURRENT").writeText("new manifest")
                store.restore(captured)
                File(db, "CURRENT").readText() shouldBe "old manifest"
                val outside = File(root, "outside").apply { writeText("keep outside") }
                Files.createSymbolicLink(File(db, "linked.ldb").toPath(), outside.toPath())
                shouldThrow<IOException> { store.capture(db.name) }
                outside.readText() shouldBe "keep outside"
                alias.delete()
            } finally {
                root.deleteRecursively()
            }
        }

        "unfinished recovery retains cleanup paths and removes only its owned staging files" {
            withStore { data, backups, store ->
                val db = database(data)
                val backup = store.capture(db.name)
                store.beginMutation(backup)
                val token = "11111111-1111-1111-1111-111111111111"
                val previous = File(data, ".dictionary-previous-$token")
                val staging = File(data, ".dictionary-recovery-$token")
                db.renameTo(previous) shouldBe true
                staging.mkdir() shouldBe true
                File(staging, "partial.ldb").writeText("partial recovery")
                val journal = File(backups, "pending")
                val metadata = java.util.Properties().apply { journal.inputStream().use(::load) }
                metadata.setProperty("cleanup", "${previous.name}|${staging.name}")
                journal.outputStream().use { metadata.store(it, null) }
                DictionaryFileStore(data, backups).recoverPendingMutation() shouldBe true
                File(db, "CURRENT").readText() shouldBe "old manifest"
                previous.exists() shouldBe false
                staging.exists() shouldBe false
            }
        }

        "unsafe cleanup journal paths never remove or replace a dictionary" {
            withStore { data, backups, store ->
                val db = database(data)
                store.beginMutation(store.capture(db.name))
                val journal = File(backups, "pending")
                val metadata = java.util.Properties().apply { journal.inputStream().use(::load) }
                metadata.setProperty("cleanup", "../outside")
                journal.outputStream().use { metadata.store(it, null) }
                shouldThrow<IOException> { store.recoverPendingMutation() }
                File(db, "CURRENT").readText() shouldBe "old manifest"
            }
        }
    })
