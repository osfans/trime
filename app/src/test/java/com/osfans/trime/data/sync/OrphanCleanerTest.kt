// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.io.path.createTempDirectory

class OrphanCleanerTest :
    StringSpec({
        "preserves installation.yaml even when missing from external listing" {
            val root = createTempDirectory().toFile()
            try {
                val installation = File(root, "installation.yaml")
                installation.writeText("installation_id: test")
                File(root, "orphan.yaml").writeText("orphan")

                val result =
                    OrphanCleaner.removeLocalOrphans(
                        root,
                        externalPaths = emptySet(),
                    )

                installation.exists() shouldBe true
                File(root, "orphan.yaml").exists() shouldBe false
                result.deleted shouldBe 1
            } finally {
                root.deleteRecursively()
            }
        }
    })
