// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.io.path.createTempDirectory

class AtomicLocalFileCopyTest :
    StringSpec({
        "copies file content via temp-dir round trip" {
            val dir = createTempDirectory().toFile()
            try {
                val source = File(dir, "source.txt")
                val dest = File(dir, "dest.txt")
                val payload = "hello sync\n".repeat(1024)
                source.writeText(payload)

                val bytes = AtomicLocalFileCopy.writeFromStream(dest) { output ->
                    source.inputStream().use { it.copyTo(output) }
                }

                bytes shouldBe source.length()
                dest.readText() shouldBe payload
            } finally {
                dir.deleteRecursively()
            }
        }

        "replaces an existing destination file" {
            val dir = createTempDirectory().toFile()
            try {
                val source = File(dir, "source.txt")
                val dest = File(dir, "dest.txt")
                source.writeText("replacement")
                dest.writeText("old")

                AtomicLocalFileCopy.writeFromStream(dest) { output ->
                    source.inputStream().use { it.copyTo(output) }
                }

                dest.readText() shouldBe "replacement"
            } finally {
                dir.deleteRecursively()
            }
        }

        "does not overwrite a sibling named dest.txt.tmp" {
            val dir = createTempDirectory().toFile()
            try {
                val sibling = File(dir, "dest.txt.tmp")
                sibling.writeText("sibling")
                val source = File(dir, "source.txt")
                source.writeText("new content")

                AtomicLocalFileCopy.writeFromStream(File(dir, "dest.txt")) { output ->
                    source.inputStream().use { it.copyTo(output) }
                }

                sibling.readText() shouldBe "sibling"
            } finally {
                dir.deleteRecursively()
            }
        }

        "keeps original content when write callback throws" {
            val dir = createTempDirectory().toFile()
            try {
                val dest = File(dir, "dest.txt")
                dest.writeText("original")

                shouldThrow<RuntimeException> {
                    AtomicLocalFileCopy.writeFromStream(dest) {
                        throw RuntimeException("write failed")
                    }
                }

                dest.readText() shouldBe "original"
            } finally {
                dir.deleteRecursively()
            }
        }
    })
