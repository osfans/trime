// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File

class SyncRelativePathTest :
    StringSpec({
        "normalize accepts simple relative paths" {
            SyncRelativePath.normalize("foo/bar.yaml") shouldBe "foo/bar.yaml"
            SyncRelativePath.normalize("/foo/bar.yaml") shouldBe "foo/bar.yaml"
        }
        "normalize rejects parent segments" {
            shouldThrow<SyncRelativePath.PathEscapeException> {
                SyncRelativePath.normalize("../outside.yaml")
            }
            shouldThrow<SyncRelativePath.PathEscapeException> {
                SyncRelativePath.normalize("foo/../outside.yaml")
            }
        }
        "normalize rejects empty and current-dir paths" {
            shouldThrow<SyncRelativePath.PathEscapeException> {
                SyncRelativePath.normalize("")
            }
            shouldThrow<SyncRelativePath.PathEscapeException> {
                SyncRelativePath.normalize("   ")
            }
            shouldThrow<SyncRelativePath.PathEscapeException> {
                SyncRelativePath.normalize("/")
            }
            shouldThrow<SyncRelativePath.PathEscapeException> {
                SyncRelativePath.normalize("./")
            }
        }
        "resolveContained stays within root" {
            val root = createTempDir()
            try {
                val resolved = SyncRelativePath.resolveContained(root, "default.yaml")
                resolved.path shouldBe File(root, "default.yaml").canonicalFile.path
            } finally {
                root.deleteRecursively()
            }
        }
    })
