/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.data.theme.icons

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.security.MessageDigest

class CommunityMaterialGlyphTableTest :
    StringSpec({
        // Match the existing app JVM tests' src/main/assets/res working-directory convention.
        // Read the production resource once; do not vendor another 178KB copy in test resources.
        val resource = File("src/main/res/raw/community_material_glyphs.tsv")
        val bytes = resource.readBytes()
        val model = resource.readLines(Charsets.US_ASCII).map { line ->
            val fields = line.split('\t')
            check(fields.size == 2 && fields[1].matches(Regex("[0-9A-F]{4}")))
            fields[0] to fields[1].toInt(16).toChar()
        }

        "all 7134 packaged rows agree with an independent line model in both lookup directions" {
            model.size shouldBe 7134
            model.map { it.first }.toSet().size shouldBe 7134
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } shouldBe
                "d060a97a02f3639794fb847a43b7d9b6a773e021900659507fe0a0f3f561709f"
            val table = CommunityMaterialGlyphTable(bytes, 7134)
            table.size shouldBe model.size
            model.forEachIndexed { index, (name, character) ->
                table.nameAt(index) shouldBe name
                table.characterAt(index) shouldBe character
                table.indexOf(name) shouldBe index
            }
        }

        "prefix adjacent and missing keys never return another icon including Unicode keys" {
            val table = CommunityMaterialGlyphTable("cmd_a\t0041\ncmd_ab\t0042\ncmd_z\t0043\n".toByteArray(), 3)
            table.indexOf("cmd_a") shouldBe 0
            table.indexOf("cmd_ab") shouldBe 1
            table.indexOf("cmd_z") shouldBe 2
            listOf("", "cmd_", "cmd_0", "cmd_aa", "cmd_zz", "CMD_A", "cmd_a\u0000", "cmd_a\uD83D\uDE00", "cmd_\uD800", "cmd_\uDC00", "cmd_é", "cmd_中").forEach { key ->
                table.indexOf(key) shouldBe -1
            }
        }

        "single record and BMP boundary code units retain their full value" {
            val one = CommunityMaterialGlyphTable("cmd_a\t0000\n".toByteArray(), 1)
            one.indexOf("cmd_a") shouldBe 0
            one.characterAt(0).code shouldBe 0
            val table = CommunityMaterialGlyphTable("cmd_a\t0000\ncmd_b\tD7FF\ncmd_c\tE000\ncmd_d\tFFFF\n".toByteArray(), 4)
            (0 until table.size).map { table.characterAt(it).code } shouldBe listOf(0, 0xD7FF, 0xE000, 0xFFFF)
        }

        "truncation missing fields line endings and unexpected names are rejected" {
            listOf("", "cmd_a", "cmd_a\t", "cmd_a\tA001", "cmd_a\tA00\n", "cmd_a\tA001\r\n", "cmd_a\tA001\n\n", "other_a\tA001\n", "cmd_A\tA001\n").forEach { value ->
                shouldThrow<IllegalArgumentException> { CommunityMaterialGlyphTable(value.toByteArray(), 1) }
            }
        }

        "duplicate keys and out of order rows cannot produce ambiguous binary search results" {
            listOf("cmd_a\tA001\ncmd_a\tA002\n", "cmd_b\tA001\ncmd_a\tA002\n").forEach { value ->
                shouldThrow<IllegalArgumentException> { CommunityMaterialGlyphTable(value.toByteArray(), 2) }
            }
        }

        "bad hexadecimal code units are rejected without substituting another glyph" {
            listOf("cmd_a\tG001\n", "cmd_a\tA00g\n", "cmd_a\ta001\n").forEach { value ->
                shouldThrow<IllegalStateException> { CommunityMaterialGlyphTable(value.toByteArray(), 1) }
            }
        }

        "isolated surrogates and supplementary code points cannot be narrowed to Char" {
            listOf("D800", "DBFF", "DC00", "DFFF", "10000", "1F600", "10FFFF", "D83DDE00").forEach { field ->
                shouldThrow<IllegalArgumentException> { CommunityMaterialGlyphTable("cmd_a\t$field\n".toByteArray(), 1) }
            }
        }

        "missing or excess complete production records fail the declared full table count" {
            val lines = resource.readLines(Charsets.US_ASCII)
            shouldThrow<IllegalArgumentException> {
                CommunityMaterialGlyphTable((lines.dropLast(1).joinToString("\n") + "\n").toByteArray(), 7134)
            }
            shouldThrow<IllegalArgumentException> { CommunityMaterialGlyphTable(bytes, 7133) }
        }
    })
