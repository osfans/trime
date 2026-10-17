// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.core.data

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.io.path.createTempDirectory

class ConfigImportValidatorTest :
    StringSpec({
        fun validate(path: String, content: String): Boolean {
            val dir = createTempDirectory().toFile()
            return try {
                val file = File(dir, "input")
                file.writeText(content)
                ConfigImportValidator.validate(file, path)
            } finally {
                dir.deleteRecursively()
            }
        }

        "valid schema and custom patch need no hand-edited deployment state" {
            validate("test.schema.yaml", "schema:\n  schema_id: test\n  name: Test\nengine:\n  processors: [speller]\n") shouldBe true
            validate("default.custom.yaml", "patch:\n  menu/page_size: 5\n") shouldBe true
        }
        "dictionary body is separate from its YAML header" {
            validate("test.dict.yaml", "---\nname: test\nversion: '1'\n...\n虚构词\ttest\t1\n") shouldBe true
            validate("test.dict.yaml", "name: test\n...") shouldBe true
        }
        "safe native resource directives remain supported" {
            validate("test.yaml", "__include: default:/menu\n__patch: [symbols:/patch]\n") shouldBe true
        }
        "invalid YAML and nonmapping roots are rejected" {
            listOf("broken: [", "- one\n- two\n", "scalar", "# empty\n").forEach {
                shouldThrowAny { validate("test.yaml", it) }
            }
        }
        "schema filename and schema ID must agree" {
            shouldThrowAny { validate("test.schema.yaml", "schema:\n  schema_id: other\n") }
            shouldThrowAny { validate("test.schema.yaml", "engine: [speller]\n") }
            shouldThrowAny { validate("test.schema.yaml", "schema: {schema_id: test}\nengine: [speller]\n") }
        }
        "custom files must contain a usable patch" {
            shouldThrowAny { validate("default.custom.yaml", "unrelated: value\n") }
            shouldThrowAny { validate("default.custom.yaml", "patch: null\n") }
        }
        "traversal and noncanonical import paths are rejected" {
            listOf("../test.yaml", "/test.yaml", "./test.yaml", "a\\test.yaml").forEach {
                shouldThrowAny { validate(it, "key: value\n") }
            }
        }
        "resource references cannot escape the configuration hierarchy" {
            listOf("../outside:/value", "/outside:/value", "other:/../../value", "a\\b:/value").forEach {
                shouldThrowAny { validate("test.yaml", "__include: '$it'\n") }
            }
        }
        "aliases have a finite expansion budget" {
            val aliases = (0..1100).joinToString("\n") { "key$it: *base" }
            shouldThrowAny { validate("test.yaml", "base: &base [one, two]\n$aliases\n") }
        }
        "deep structures are bounded before activation" {
            val content = (0..66).joinToString("\n") { "  ".repeat(it) + "key$it:" } + " value\n"
            shouldThrowAny { validate("test.yaml", content) }
        }
        "event parsing rejects large node trees within a small byte payload" {
            val content = "items: [" + (0..25_000).joinToString(",") { "x" } + "]\n"
            shouldThrowAny { validate("test.yaml", content) }
        }
        "invalid UTF-8 is rejected rather than silently replaced" {
            val dir = createTempDirectory().toFile()
            try {
                val file = File(dir, "input")
                file.writeBytes(byteArrayOf(0xC3.toByte(), 0x28))
                shouldThrowAny { ConfigImportValidator.validate(file, "test.yaml") }
            } finally {
                dir.deleteRecursively()
            }
        }
        "large YAML and unterminated dictionary headers are rejected" {
            shouldThrowAny { validate("test.yaml", "#".repeat(ConfigImportValidator.MAX_YAML_BYTES + 1)) }
            shouldThrowAny { validate("test.dict.yaml", "name: test\nword\tcode\n") }
        }
        "obviously invalid theme sections fail before replacing the active file" {
            shouldThrowAny { validate("test.trime.yaml", "name: Test\nstyle: [one]\n") }
            shouldThrowAny { validate("test.trime.yaml", "name: Test\npreset_color_schemes: []\n") }
        }
        "ordinary binary or text data does not trigger configuration parsing" {
            validate("sync/peer/userdb.txt", "portable dump") shouldBe false
            ConfigImportValidator.requiresDeployment("sync/peer/userdb.txt") shouldBe false
            ConfigImportValidator.requiresDeployment("custom_phrase.txt") shouldBe true
            ConfigImportValidator.requiresDeployment("trime_predict.db") shouldBe true
            ConfigImportValidator.requiresDeployment("rime.lua") shouldBe true
            ConfigImportValidator.requiresDeployment("lua/input_hook.lua") shouldBe true
            ConfigImportValidator.requiresDeployment("opencc/test.ocd2") shouldBe true
            ConfigImportValidator.requiresDeployment("opencc/test.txt") shouldBe true
            ConfigImportValidator.requiresDeployment("notes/test.txt") shouldBe false
            ConfigImportValidator.requiresDeployment("backgrounds/test.png") shouldBe false
            ConfigImportValidator.requiresDeployment("fonts/test.ttf") shouldBe false
        }
    })
