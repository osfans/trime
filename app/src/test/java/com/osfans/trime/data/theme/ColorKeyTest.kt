/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch

/**
 * Pins ColorKey against the historical built-in fallback chains that lived in
 * ColorManager, so future edits cannot silently change resolution semantics.
 */
class ColorKeyTest :
    BehaviorSpec({
        Given("the ColorKey enum") {
            Then("every entry maps to its lowercased YAML name and back") {
                for (entry in ColorKey.entries) {
                    ColorKey.from(entry.key) shouldBe entry
                }
            }
            Then("unknown strings are not typed color keys") {
                ColorKey.from("no_such_key").shouldBeNull()
                ColorKey.from("").shouldBeNull()
            }
            Then("every key follows the <role>_[state_]<part>_color convention") {
                val canonical =
                    Regex("^[a-z][a-z_]*_(?:(?:on_pressed|pressed|on|selected)_)?(?:back|border|separator|text|symbol|shadow)_color$")
                // 角色本身含 part 字样的键，省略重复的 part 段
                val roleOnly = setOf("label_color", "long_text_color")
                for (entry in ColorKey.entries) {
                    if (entry.key in roleOnly) continue
                    entry.key shouldMatch canonical
                }
            }
            Then("legacy spellings never shadow a canonical key") {
                for (entry in ColorKey.entries) {
                    LegacyColorAliases.resolve(entry.key).shouldBeNull()
                }
            }
            Then("legacy spellings map back to the canonical key they replace") {
                for (entry in ColorKey.entries) {
                    for (legacy in LegacyColorAliases.aliasesOf(entry.key)) {
                        LegacyColorAliases.resolve(legacy) shouldBe entry.key
                    }
                }
            }
        }
        Given("the built-in fallback chain table") {
            val historical = LEGACY_BUILTIN_FALLBACK_COLORS
            Then("it matches the historical table that lived in ColorManager") {
                ColorKey.builtinFallbackColors.mapKeys { it.key.key }
                    .mapValues { it.value.key } shouldBe historical
            }
            Then("the graph is acyclic and every chain terminates") {
                val reachable = mutableSetOf<ColorKey>()
                for (entry in ColorKey.entries) {
                    var current: ColorKey? = entry
                    val seen = mutableSetOf<ColorKey>()
                    while (current != null && seen.add(current)) {
                        reachable.add(current)
                        current = ColorKey.fallbackOf(current)
                    }
                    current.shouldBeNull()
                    check(seen.size <= ColorKey.entries.size) { "cycle detected from ${entry.key}" }
                }
                reachable.size shouldBe ColorKey.entries.size
            }
            Then("keys without a chain entry are the terminals requested by code") {
                val terminals = ColorKey.entries.filter { ColorKey.fallbackOf(it) == null }
                terminals.map { it.key } shouldContainExactlyInAnyOrder
                    listOf(
                        "keyboard_back_color",
                        "preedit_text_color",
                        "candidate_border_color",
                        "preview_back_color",
                        "preview_text_color",
                    )
            }
        }
    })

/**
 * The fallback chains as originally hardcoded in ColorManager (removed in R2),
 * keyed by YAML string. ColorKeyTest asserts the typed graph equals this.
 */
private val LEGACY_BUILTIN_FALLBACK_COLORS =
    mapOf(
        "candidate_text_color" to "preedit_text_color",
        "comment_text_color" to "candidate_text_color",
        "border_back_color" to "keyboard_back_color",
        "candidate_separator_color" to "border_back_color",
        "preedit_selected_text_color" to "preedit_text_color",
        "preedit_selected_back_color" to "keyboard_back_color",
        "candidate_selected_text_color" to "preedit_selected_text_color",
        "candidate_selected_back_color" to "preedit_selected_back_color",
        "candidate_button_back_color" to "candidate_back_color",
        "candidate_button_selected_back_color" to "candidate_selected_back_color",
        "candidate_button_text_color" to "candidate_text_color",
        "candidate_button_selected_text_color" to "candidate_selected_text_color",
        "label_selected_text_color" to "candidate_selected_text_color",
        "comment_selected_text_color" to "comment_text_color",
        "key_pressed_back_color" to "candidate_selected_back_color",
        "key_pressed_border_color" to "key_border_color",
        "key_pressed_text_color" to "candidate_selected_text_color",
        "key_pressed_symbol_color" to "comment_selected_text_color",
        "function_key_pressed_back_color" to "key_pressed_back_color",
        "function_key_on_pressed_back_color" to "key_pressed_back_color",
        "function_key_pressed_border_color" to "key_pressed_border_color",
        "function_key_on_pressed_border_color" to "key_pressed_border_color",
        "function_key_pressed_text_color" to "key_pressed_text_color",
        "function_key_on_pressed_text_color" to "key_pressed_text_color",
        "function_key_pressed_symbol_color" to "key_pressed_symbol_color",
        "function_key_on_pressed_symbol_color" to "key_pressed_symbol_color",
        "key_back_color" to "candidate_back_color",
        "key_border_color" to "border_back_color",
        "key_text_color" to "candidate_text_color",
        "key_symbol_color" to "comment_text_color",
        "label_color" to "candidate_text_color",
        "function_key_back_color" to "key_back_color",
        "function_key_border_color" to "key_border_color",
        "function_key_text_color" to "key_text_color",
        "function_key_symbol_color" to "key_symbol_color",
        "function_key_on_back_color" to "key_pressed_back_color",
        "function_key_on_border_color" to "key_pressed_border_color",
        "function_key_on_text_color" to "key_pressed_text_color",
        "function_key_on_symbol_color" to "key_pressed_symbol_color",
        "popup_back_color" to "key_back_color",
        "popup_text_color" to "key_text_color",
        "popup_selected_back_color" to "key_pressed_back_color",
        "popup_selected_text_color" to "key_pressed_text_color",
        "key_shadow_color" to "border_back_color",
        "root_back_color" to "keyboard_back_color",
        "candidate_back_color" to "keyboard_back_color",
        "liquid_keyboard_back_color" to "keyboard_back_color",
        "preedit_back_color" to "keyboard_back_color",
        "long_text_color" to "key_text_color",
        "long_text_back_color" to "key_back_color",
    ).also {
        check(it.size == 50) { "historical fallback table changed: ${it.size}" }
    }
