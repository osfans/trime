// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.core.data

import com.osfans.trime.data.opencc.DictionaryConversionCache
import com.osfans.trime.data.theme.ThemeLoader
import com.osfans.trime.data.theme.ThemeYaml
import com.osfans.trime.util.mapping
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory

class LocalImportTransactionTest :
    StringSpec({
        fun root(parent: File) = File(parent, "rime").apply { mkdir() }
        fun write(root: File, path: String, contents: String) = File(root, path).apply {
            parentFile!!.mkdirs()
            writeText(contents)
        }

        "staging and validation failures leave all live configuration untouched" {
            val parent = createTempDirectory().toFile()
            try {
                val root = root(parent)
                write(root, "default.custom.yaml", "patch: {menu/page_size: 5}\n")
                val transaction = LocalImportTransaction(root)
                transaction.stageFile("default.custom.yaml").writeText("patch: [")
                val failure = shouldThrowAny {
                    ConfigImportValidator.validate(transaction.stageFile("default.custom.yaml"), "default.custom.yaml")
                }
                transaction.rollback(failure) shouldBe true
                File(root, "default.custom.yaml").readText() shouldBe "patch: {menu/page_size: 5}\n"
                parent.listFiles()!!.size shouldBe 1
            } finally {
                parent.deleteRecursively()
            }
        }
        "successful activation commits the whole group" {
            val parent = createTempDirectory().toFile()
            try {
                val root = root(parent)
                write(root, "first.yaml", "old")
                val transaction = LocalImportTransaction(root)
                transaction.stageFile("first.yaml").writeText("new")
                transaction.stageFile("nested/second.yaml").writeText("second")
                transaction.publish("first.yaml")
                transaction.publish("nested/second.yaml")
                transaction.commit()
                File(root, "first.yaml").readText() shouldBe "new"
                File(root, "nested/second.yaml").readText() shouldBe "second"
                parent.listFiles()!!.size shouldBe 1
            } finally {
                parent.deleteRecursively()
            }
        }
        "activation failure restores source, compiled cache and native-written state" {
            val parent = createTempDirectory().toFile()
            try {
                val root = root(parent)
                write(root, "default.custom.yaml", "old source")
                write(root, "build/default.yaml", "old compiled")
                write(root, "build/luna.table.bin", "old binary")
                write(root, "user.yaml", "old schema choice")
                write(root, "installation.yaml", "old installation")
                write(root, "custom_phrase.txt", "retained dictionary")
                write(root, "unchanged.trime.yaml", "original unchanged theme")
                write(root, "trash/previous.yaml", "original retained trash")
                val transaction = LocalImportTransaction(root)
                transaction.stageFile("default.custom.yaml").writeText("new source")
                transaction.stageFile("new.schema.yaml").writeText("new schema")
                val expectedSources = linkedMapOf(
                    "unchanged.trime.yaml" to SyncFingerprint.of(File(root, "unchanged.trime.yaml")),
                    "default.custom.yaml" to SyncFingerprint.of(transaction.stageFile("default.custom.yaml")),
                    "new.schema.yaml" to SyncFingerprint.of(transaction.stageFile("new.schema.yaml")),
                )
                transaction.snapshotRuntime(listOf("unchanged.trime.yaml"))
                transaction.publish("default.custom.yaml")
                transaction.publish("new.schema.yaml")
                val failure = shouldThrowAny {
                    write(root, "build/default.yaml", "failed compiled")
                    write(root, "build/failed.prism.bin", "failed binary")
                    write(root, "user.yaml", "failed schema choice")
                    write(root, "installation.yaml", "changed installation")
                    check(File(root, "new.schema.yaml").renameTo(File(root, "trash/new.schema.yaml")))
                    check(File(root, "unchanged.trime.yaml").renameTo(File(root, "trash/unchanged.trime.yaml")))
                    write(root, "trash/previous.yaml", "native replaced retained trash")
                    transaction.verifySources(expectedSources)
                }
                failure.message shouldBe "Native activation changed validated configuration source: unchanged.trime.yaml"
                transaction.rollback(failure) shouldBe true
                File(root, "default.custom.yaml").readText() shouldBe "old source"
                File(root, "new.schema.yaml").exists() shouldBe false
                File(root, "build/default.yaml").readText() shouldBe "old compiled"
                File(root, "build/luna.table.bin").readText() shouldBe "old binary"
                File(root, "build/failed.prism.bin").exists() shouldBe false
                File(root, "user.yaml").readText() shouldBe "old schema choice"
                File(root, "installation.yaml").readText() shouldBe "old installation"
                File(root, "custom_phrase.txt").readText() shouldBe "retained dictionary"
                File(root, "unchanged.trime.yaml").readText() shouldBe "original unchanged theme"
                File(root, "trash/previous.yaml").readText() shouldBe "original retained trash"
                File(root, "trash/new.schema.yaml").exists() shouldBe false
                File(root, "trash/unchanged.trime.yaml").exists() shouldBe false
            } finally {
                parent.deleteRecursively()
            }
        }
        "a later publication failure rolls back earlier published files" {
            val parent = createTempDirectory().toFile()
            try {
                val root = root(parent)
                write(root, "first.yaml", "old first")
                File(root, "blocked.yaml").mkdir()
                val transaction = LocalImportTransaction(root)
                transaction.stageFile("first.yaml").writeText("new first")
                transaction.stageFile("blocked.yaml").writeText("new blocked")
                transaction.publish("first.yaml")
                val failure = shouldThrowAny { transaction.publish("blocked.yaml") }
                transaction.rollback(failure) shouldBe true
                File(root, "first.yaml").readText() shouldBe "old first"
                File(root, "blocked.yaml").isDirectory shouldBe true
            } finally {
                parent.deleteRecursively()
            }
        }
        "a structurally valid theme with an invalid typed value restores its source and deployed artifact" {
            val parent = createTempDirectory().toFile()
            try {
                val root = root(parent)
                val old = "name: Previous\nstyle: {candidate_text_size: 20}\npreset_color_schemes: {default: {name: Default}}\n"
                val invalid = "name: Invalid\nstyle: {candidate_text_size: not_a_number}\npreset_color_schemes: {default: {name: Default}}\n"
                write(root, "selected.trime.yaml", old)
                write(root, "build/selected.trime.yaml", old)
                write(root, "unrelated.custom.yaml", "untouched user patch")
                val transaction = LocalImportTransaction(root)
                val staged = transaction.stageFile("selected.trime.yaml").apply { writeText(invalid) }
                ConfigImportValidator.validate(staged, "selected.trime.yaml") shouldBe true
                transaction.snapshotRuntime()
                transaction.publish("selected.trime.yaml")
                val failure = shouldThrowAny {
                    val deployed = write(root, "build/selected.trime.yaml", invalid)
                    ThemeLoader.decodeAndReport("selected.trime", ThemeYaml.parser.parseToYamlNode(deployed.readText()).mapping!!)
                }
                transaction.rollback(failure) shouldBe true
                File(root, "selected.trime.yaml").readText() shouldBe old
                File(root, "build/selected.trime.yaml").readText() shouldBe old
                File(root, "unrelated.custom.yaml").readText() shouldBe "untouched user patch"
            } finally {
                parent.deleteRecursively()
            }
        }
        "failed activation restores generated OpenCC dictionaries and invalidates conversion evidence" {
            val parent = createTempDirectory().toFile()
            try {
                val root = root(parent)
                val source = write(root, "opencc/local.txt", "old text dictionary")
                val output = write(root, "opencc/local.ocd2", "old usable binary")
                val transaction = LocalImportTransaction(root)
                transaction.stageFile("opencc/local.txt").writeText("new text dictionary")
                transaction.snapshotRuntime()
                transaction.publish("opencc/local.txt")
                val cache = DictionaryConversionCache()
                val converter: (File, File) -> Unit = { input, dest -> dest.writeText("converted: ${input.readText()}") }
                val failure = shouldThrowAny {
                    cache.convert(source, output, converter) shouldBe true
                    write(root, "opencc/newly_generated.ocd2", "new binary")
                    throw IOException("theme activation failed after OpenCC conversion")
                }
                transaction.rollback(failure) shouldBe true
                source.readText() shouldBe "old text dictionary"
                output.readText() shouldBe "old usable binary"
                File(root, "opencc/newly_generated.ocd2").exists() shouldBe false
                // The process-local conversion cache must observe restored bytes, too.
                cache.convert(source, output, converter) shouldBe true
                output.readText() shouldBe "converted: old text dictionary"
            } finally {
                parent.deleteRecursively()
            }
        }
        "deleted tracked files are recoverable until activation succeeds" {
            val parent = createTempDirectory().toFile()
            try {
                val root = root(parent)
                write(root, "previous.yaml", "previous")
                val transaction = LocalImportTransaction(root)
                transaction.delete("previous.yaml")
                transaction.rollback(IOException("cancelled activation")) shouldBe true
                File(root, "previous.yaml").readText() shouldBe "previous"
            } finally {
                parent.deleteRecursively()
            }
        }
        "failure to close native handles retains backups instead of replacing live cache" {
            val parent = createTempDirectory().toFile()
            try {
                val root = root(parent)
                write(root, "first.yaml", "original")
                write(root, "build/default.yaml", "old compiled")
                val transaction = LocalImportTransaction(root)
                transaction.stageFile("first.yaml").writeText("new")
                transaction.snapshotRuntime()
                transaction.publish("first.yaml")
                write(root, "build/default.yaml", "in use by native")
                transaction.retainForRecovery(IOException("native handles could not be closed"))
                File(root, "build/default.yaml").readText() shouldBe "in use by native"
                File(transaction.recoveryDirectory, "runtime/build/default.yaml").readText() shouldBe "old compiled"
                File(transaction.recoveryDirectory, "original/first.yaml").readText() shouldBe "original"
            } finally {
                parent.deleteRecursively()
            }
        }
        "rollback failures retain the original backup and report its location" {
            val parent = createTempDirectory().toFile()
            try {
                val root = root(parent)
                write(root, "first.yaml", "recoverable original")
                val transaction = LocalImportTransaction(root)
                transaction.stageFile("first.yaml").writeText("failed new")
                transaction.publish("first.yaml")
                File(root, "first.yaml").delete()
                write(root, "first.yaml/blocker", "prevents removal")
                val failure = IOException("activation failed")
                transaction.rollback(failure) shouldBe false
                File(transaction.recoveryDirectory, "original/first.yaml").readText() shouldBe "recoverable original"
                failure.suppressed.any { it.message?.contains(transaction.recoveryDirectory) == true } shouldBe true
            } finally {
                parent.deleteRecursively()
            }
        }
        "traversal paths are rejected without touching a sibling" {
            val parent = createTempDirectory().toFile()
            try {
                val root = root(parent)
                val sibling = write(parent, "outside.yaml", "outside")
                val transaction = LocalImportTransaction(root)
                val failure = shouldThrowAny { transaction.stageFile("../outside.yaml") }
                transaction.rollback(failure) shouldBe true
                sibling.readText() shouldBe "outside"
            } finally {
                parent.deleteRecursively()
            }
        }
    })
