// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.core.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

class SyncSafetyTest :
    StringSpec({
        "a failed directory creation can be retried" {
            val root = createTempDirectory().toFile()
            try {
                val blockingFile = File(root, "folder").apply { writeText("blocked") }
                val gate = UserDataManager.LocalDirectoryGate()
                shouldThrow<IllegalStateException> { gate.ensure(root, "folder/sub") }
                blockingFile.delete()
                gate.ensure(root, "folder/sub")
                File(root, "folder/sub").isDirectory shouldBe true
            } finally {
                root.deleteRecursively()
            }
        }
        "file imports cannot replace directories" {
            val root = createTempDirectory().toFile()
            try {
                val directory = File(root, "folder").apply { mkdir() }
                val sentinel = File(directory, "sentinel").apply { writeText("retained") }
                shouldThrow<IllegalStateException> {
                    UserDataManager.writeFromStream(directory) { it.write(1) }
                }
                sentinel.readText() shouldBe "retained"
            } finally {
                root.deleteRecursively()
            }
        }
        "recovery files survive subsequent orphan cleanup" {
            val root = createTempDirectory().toFile()
            try {
                val backup = File(root, ".trime-bak-12345678-1234-1234-1234-123456789abc.tmp").apply { writeText("recovery") }
                UserDataManager.removeLocalOrphans(root, emptySet()).deleted shouldBe 0
                backup.readText() shouldBe "recovery"
                UserDataManager.shouldSkip("user.yaml.bak") shouldBe false
            } finally {
                root.deleteRecursively()
            }
        }
        "unknown metadata never produces a cached skip" {
            for ((size, time) in listOf(-1L to 0L, 12L to 0L, -1L to 123L)) {
                val index = SyncIndexData(entries = mapOf("test" to SyncEntry(size, time)))
                UserDataManager.shouldCopy("test", size, time, index) shouldBe true
            }
            val index = SyncIndexData(entries = mapOf("test" to SyncEntry(12, 123)))
            UserDataManager.shouldCopy("test", 12, 123, index) shouldBe false
            // StorageAccess represents a provider's unknown size as zero. A previous actual
            // byte count must therefore force another verified copy.
            UserDataManager.shouldCopy("test", 0, 123, index) shouldBe true
        }
        "names preserve spaces but reject path components" {
            SyncRelativePath.normalize(" folder/file ") shouldBe " folder/file "
            for (path in listOf(".", "foo/./bar", "foo/../bar", "foo/\u0000")) {
                shouldThrow<SyncRelativePath.PathEscapeException> { SyncRelativePath.normalize(path) }
            }
            for (name in listOf(".", "..", "a/b", "a\\b", "\u0000", "")) {
                shouldThrow<IllegalArgumentException> { SyncRelativePath.requireSafeName(name) }
            }
            SyncRelativePath.requireSafeName(" file.yaml ")
            UserDataManager.shouldSkip("build /file.yaml") shouldBe false
        }
        "orphan cleanup never traverses a symlink outside the test root" {
            val sandbox = createTempDirectory().toFile()
            try {
                val root = File(sandbox, "root").apply { mkdir() }
                val outside = File(sandbox, "outside").apply { mkdir() }
                val sentinel = File(outside, "sentinel").apply { writeText("retained") }
                Files.createSymbolicLink(File(root, "linked-dir").toPath(), outside.toPath())
                Files.createSymbolicLink(File(root, "linked-file").toPath(), sentinel.toPath())
                UserDataManager.removeLocalOrphans(root, emptySet()).failed shouldBe 0
                sentinel.readText() shouldBe "retained"
            } finally {
                sandbox.deleteRecursively()
            }
        }
        "internal aliases cannot bypass user dictionary protection" {
            val root = createTempDirectory().toFile()
            val alias = File(root, "alias")
            val loop = File(root, "loop")
            try {
                val dictionary = File(root, "test.userdb").apply { mkdir() }
                val sentinel = File(dictionary, "data").apply { writeText("personal") }
                Files.createSymbolicLink(alias.toPath(), dictionary.toPath())
                Files.createSymbolicLink(loop.toPath(), root.toPath())
                SyncRelativePath.isDirectPath(root, alias) shouldBe false
                shouldThrow<SyncRelativePath.PathEscapeException> {
                    SyncRelativePath.resolveContained(root, "alias/data")
                }
                UserDataManager.removeLocalOrphans(root, emptySet()).deleted shouldBe 0
                sentinel.readText() shouldBe "personal"
            } finally {
                alias.delete()
                loop.delete()
                root.deleteRecursively()
            }
        }
    })
