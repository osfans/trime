// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.core.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory

class UpstreamUserDataIntegrationTest :
    StringSpec({
        "larger sync hash buffers retain content across boundaries and short reads" {
            val content = ByteArray(32 * 1024 * 3 + 17) { (it * 31 + 127).toByte() }
            val shortReads = object : ByteArrayInputStream(content) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int = super.read(buffer, offset, minOf(length, 7))
            }
            SyncFingerprint.digest(shortReads).toList() shouldBe
                MessageDigest.getInstance("SHA-256").digest(content).toList()
        }

        "sync hashing stays bounded for files larger than the copy buffer" {
            val content = ByteArray(1024 * 1024 + 5) { (it % 251).toByte() }
            var largestRead = 0
            var bytesRead = 0
            val input = object : ByteArrayInputStream(content) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    largestRead = maxOf(largestRead, length)
                    return super.read(buffer, offset, length).also { if (it > 0) bytesRead += it }
                }
            }
            SyncFingerprint.digest(input).toList() shouldBe
                MessageDigest.getInstance("SHA-256").digest(content).toList()
            bytesRead shouldBe content.size
            largestRead shouldBe 32 * 1024
        }

        "moved first-run bootstrap includes full pinyin and nine-key schemas" {
            val directory = createTempDirectory().toFile()
            try {
                UserDataManager.ensureDefaultCustomConfig(directory)
                val config = directory.resolve("default.custom.yaml")
                config.readLines().map(String::trim).filter { it.startsWith("- schema:") } shouldBe
                    listOf("- schema: luna_pinyin", "- schema: luna_pinyin_simp", "- schema: luna_pinyin_t9")
                UserDataManager.ensureDefaultCustomConfig(directory)
                directory.listFiles()!!.map { it.name } shouldBe listOf("default.custom.yaml")
            } finally {
                directory.deleteRecursively()
            }
        }

        "moved bootstrap preserves existing user configuration byte for byte" {
            val directory = createTempDirectory().toFile()
            try {
                val config = directory.resolve("default.custom.yaml")
                val original = "# user choices\r\npatch:\r\n  menu/page_size: 9\r\n".toByteArray()
                config.writeBytes(original)
                UserDataManager.ensureDefaultCustomConfig(directory)
                config.readBytes().toList() shouldBe original.toList()
            } finally {
                directory.deleteRecursively()
            }
        }
    })
