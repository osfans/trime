// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.FileInputStream

class AtomicLocalFileCopyTest :
    StringSpec({
        "copies file content via temp-dir round trip" {
            val dir = createTempDir()
            try {
                val source = File(dir, "source.txt")
                val dest = File(dir, "dest.txt")
                val payload = "hello sync\n".repeat(1024)
                source.writeText(payload)

                val bytes =
                    FileInputStream(source).use { input ->
                        AtomicLocalFileCopy.copyFromInput(input, dest)
                    }

                bytes shouldBe source.length()
                dest.readText() shouldBe payload
            } finally {
                dir.deleteRecursively()
            }
        }

        "replaces an existing destination file" {
            val dir = createTempDir()
            try {
                val source = File(dir, "source.txt")
                val dest = File(dir, "dest.txt")
                source.writeText("replacement")
                dest.writeText("old")

                FileInputStream(source).use { input ->
                    AtomicLocalFileCopy.copyFromInput(input, dest)
                }

                dest.readText() shouldBe "replacement"
            } finally {
                dir.deleteRecursively()
            }
        }

        "does not overwrite a sibling named dest.txt.tmp" {
            val dir = createTempDir()
            try {
                val sibling = File(dir, "dest.txt.tmp")
                sibling.writeText("sibling")
                val source = File(dir, "source.txt")
                source.writeText("new content")

                FileInputStream(source).use { input ->
                    AtomicLocalFileCopy.copyFromInput(input, File(dir, "dest.txt"))
                }

                sibling.readText() shouldBe "sibling"
            } finally {
                dir.deleteRecursively()
            }
        }

        "keeps original content when write callback throws" {
            val dir = createTempDir()
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
