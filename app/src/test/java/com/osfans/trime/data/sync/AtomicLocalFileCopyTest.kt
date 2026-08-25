// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

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
    })
