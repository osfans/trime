// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.provider

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

class DocumentsBaseDirTest :
    StringSpec({
        "null external files dir stays unresolved" {
            RimeDataProvider.resolveDocumentsRoot(null).shouldBeNull()
        }

        "writable external files dir yields a documents root" {
            val parent = createTempDirectory().toFile()
            try {
                val external = File(parent, "files").also { it.mkdirs() }
                val resolved = RimeDataProvider.resolveDocumentsRoot(external)
                resolved.shouldNotBeNull()
                val canonicalParent = checkNotNull(external.canonicalFile.parentFile)
                resolved.first.canonicalFile shouldBe external.canonicalFile
                resolved.second shouldBe "${canonicalParent.path}${File.separator}"
            } finally {
                parent.deleteRecursively()
            }
        }

        "later real parent succeeds after a null attempt" {
            RimeDataProvider.resolveDocumentsRoot(null).shouldBeNull()
            val parent = createTempDirectory().toFile()
            try {
                val external = File(parent, "files").also { it.mkdirs() }
                RimeDataProvider.resolveDocumentsRoot(external).shouldNotBeNull()
            } finally {
                parent.deleteRecursively()
            }
        }

        "ordinary file is not exposed as a documents root" {
            val parent = createTempDirectory().toFile()
            try {
                val file = File(parent, "files").also { it.writeText("data") }
                RimeDataProvider.resolveDocumentsRoot(file).shouldBeNull()
            } finally {
                parent.deleteRecursively()
            }
        }

        "security denial leaves the documents root unresolved" {
            val denied = object : File("denied") {
                override fun getCanonicalFile(): File = throw SecurityException("denied")
            }
            RimeDataProvider.resolveDocumentsRoot(denied).shouldBeNull()
        }

        "document ids resolve within the canonical root" {
            val parent = createTempDirectory().toFile()
            try {
                val external = File(parent, "files").also { it.mkdirs() }
                val (root, prefix) = RimeDataProvider.resolveDocumentsRoot(external).shouldNotBeNull()
                RimeDataProvider.resolveDocument(root, prefix, "files/rime/default.yaml") shouldBe
                    File(external, "rime/default.yaml").canonicalFile
                RimeDataProvider.resolveDocument(root, prefix, "files") shouldBe external.canonicalFile
            } finally {
                parent.deleteRecursively()
            }
        }

        "document ids cannot escape or replace the provider root" {
            val parent = createTempDirectory().toFile()
            try {
                val external = File(parent, "files").also { it.mkdirs() }
                val (root, prefix) = RimeDataProvider.resolveDocumentsRoot(external).shouldNotBeNull()
                RimeDataProvider.resolveDocument(root, prefix, "files/../../outside").shouldBeNull()
                RimeDataProvider.resolveDocument(root, prefix, external.absolutePath).shouldBeNull()
                RimeDataProvider.resolveDocument(root, "${parent.parent}${File.separator}", "files").shouldBeNull()
            } finally {
                parent.deleteRecursively()
            }
        }

        "document ids reject symlinks that leave the provider root" {
            val parent = createTempDirectory().toFile()
            val outside = createTempDirectory().toFile()
            try {
                val external = File(parent, "files").also { it.mkdirs() }
                Files.createSymbolicLink(File(external, "outside").toPath(), outside.toPath())
                val (root, prefix) = RimeDataProvider.resolveDocumentsRoot(external).shouldNotBeNull()
                RimeDataProvider.resolveDocument(root, prefix, "files/outside").shouldBeNull()
            } finally {
                parent.deleteRecursively()
                outside.deleteRecursively()
            }
        }

        "document ids reject symlinks to siblings inside the provider root" {
            val parent = createTempDirectory().toFile()
            try {
                val external = File(parent, "files").also { it.mkdirs() }
                val sibling = File(external, "target").also { it.mkdir() }
                Files.createSymbolicLink(File(external, "alias").toPath(), sibling.toPath())
                val (root, prefix) = RimeDataProvider.resolveDocumentsRoot(external).shouldNotBeNull()
                RimeDataProvider.resolveDocument(root, prefix, "files/alias").shouldBeNull()
            } finally {
                parent.deleteRecursively()
            }
        }
    })
