/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.keyboard

import com.osfans.trime.data.theme.ThemeTestSupport
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class KeyboardConfigResolverTest :
    StringSpec({
        val base = ThemeTestSupport.decodeBuiltinTheme("trime.yaml").presetKeyboards.getValue("default")

        "valid import chains and unknown-keyboard fallback retain the complete preset" {
            val presets = mapOf("default" to base, "a" to base.copy(importPreset = "b"), "b" to base.copy(importPreset = "default"))
            resolveKeyboardConfig("a", presets) shouldBe base
            resolveKeyboardConfig("missing", presets) shouldBe base
            resolveKeyboardConfig("missing", emptyMap()) shouldBe null
        }

        "self imports and missing alias targets cannot recurse through the default forever" {
            shouldThrow<IllegalArgumentException> { resolveKeyboardConfig("default", mapOf("default" to base.copy(importPreset = "default"))) }
            shouldThrow<IllegalArgumentException> { resolveKeyboardConfig("default", mapOf("default" to base.copy(importPreset = "missing"))) }
        }

        "multi-keyboard cycles are bounded" {
            shouldThrow<IllegalArgumentException> {
                resolveKeyboardConfig("a", mapOf("a" to base.copy(importPreset = "b"), "b" to base.copy(importPreset = "a")))
            }
        }

        "switch targets resolve existing and compatible layouts before the default" {
            val presets =
                linkedMapOf(
                    "default" to base,
                    "custom" to base,
                    "__compat_symbols" to base,
                )
            val fallbacks = mapOf("symbols" to "__compat_symbols", "number" to "missing")

            normalizeKeyboardTarget("custom", presets, fallbacks) shouldBe "custom"
            normalizeKeyboardTarget("symbols", presets, fallbacks) shouldBe "__compat_symbols"
            normalizeKeyboardTarget("number", presets, fallbacks) shouldBe "default"
            normalizeKeyboardTarget("unknown", presets, fallbacks) shouldBe "default"
        }

        "a missing default still resolves to an existing compatible letter layout" {
            val presets = linkedMapOf("custom" to base, "__compat_letter" to base)
            normalizeKeyboardTarget("unknown", presets, mapOf("letter" to "__compat_letter")) shouldBe "__compat_letter"
        }

        "landscape targets can reuse compatibility lookup without replacing a valid base" {
            val presets = mapOf("base" to base.copy(landscapeKeyboard = "symbols"), "__compat_symbols" to base)
            val fallbacks = mapOf("symbols" to "__compat_symbols")

            resolveLandscapeKeyboard("base", presets, fallbacks) shouldBe "__compat_symbols"
            resolveLandscapeKeyboard(
                "base",
                presets + ("base" to base.copy(landscapeKeyboard = "missing_landscape")),
                fallbacks,
            ) shouldBe "base"
        }

        for (theme in listOf("trime", "tongwenfeng.trime")) {
            "manual ASCII keeps the schema letter layout after hide show in $theme" {
                val presets = ThemeTestSupport.decodeBuiltinTheme("$theme.yaml").presetKeyboards
                val native = resolveKeyboardConfig("luna_pinyin_t9", presets)!!
                val letter = presets.getValue("letter")
                val actual = presets + ("__compat_letter" to letter)
                val fallbacks = mapOf("letter" to "__compat_letter")
                native.asciiKeyboard shouldBe "letter"
                letter.asciiKeyboard shouldBe ""
                resolveAsciiKeyboard(native.asciiKeyboard, actual, fallbacks) shouldBe "letter"
            }
        }

        "the native schema custom imported ASCII layout precedes theme letter and compatibility" {
            val presets = mapOf(
                "default" to base,
                "letter" to base.copy(asciiMode = 1),
                "custom_alias" to base.copy(importPreset = "custom_ascii"),
                "custom_ascii" to base.copy(asciiMode = 1),
                "__compat_letter" to base.copy(asciiMode = 1),
            )
            resolveAsciiKeyboard("custom_alias", presets, mapOf("letter" to "__compat_letter")) shouldBe "custom_alias"
        }

        "missing or non ASCII schema targets preserve a valid current theme letter layout" {
            val presets = mapOf("default" to base, "letter" to base.copy(asciiMode = 1), "native_only" to base)
            resolveAsciiKeyboard("missing", presets, emptyMap()) shouldBe "letter"
            resolveAsciiKeyboard("native_only", presets, emptyMap()) shouldBe "letter"
        }

        "an invalid current letter can use a valid compatible ASCII fallback" {
            val presets = mapOf("default" to base, "letter" to base, "__compat_letter" to base.copy(asciiMode = 1))
            resolveAsciiKeyboard("", presets, mapOf("letter" to "__compat_letter")) shouldBe "__compat_letter"
        }

        "cyclic ASCII imports remain bounded and fall back to a valid letter" {
            val presets = mapOf("default" to base, "cycle" to base.copy(importPreset = "cycle"), "letter" to base.copy(asciiMode = 1))
            resolveAsciiKeyboard("cycle", presets, emptyMap()) shouldBe "letter"
        }

        "a theme without an ASCII layout retains its existing default fallback" {
            resolveAsciiKeyboard("missing", mapOf("default" to base), emptyMap()) shouldBe "default"
        }

        "landscape native schema configuration selects its own ASCII layout before the final override" {
            val presets = mapOf(
                "default" to base,
                "native" to base.copy(landscapeKeyboard = "native_land"),
                "native_land" to base.copy(asciiKeyboard = "english_land"),
                "letter" to base.copy(asciiMode = 1),
                "english_land" to base.copy(asciiMode = 1, landscapeKeyboard = "english_final"),
                "english_final" to base.copy(asciiMode = 1),
            )
            val native = resolveLandscapeKeyboard("native", presets, emptyMap())
            val ascii = resolveAsciiKeyboard(resolveKeyboardConfig(native, presets)!!.asciiKeyboard, presets, emptyMap())
            resolveLandscapeKeyboard(ascii, presets, emptyMap()) shouldBe "english_final"
        }
    })
