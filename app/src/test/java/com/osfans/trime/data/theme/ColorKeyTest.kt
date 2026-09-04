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
                    listOf("back_color", "text_color", "candidate_border_color")
            }
        }
    })

/**
 * The fallback chains as originally hardcoded in ColorManager (removed in R2),
 * keyed by YAML string. ColorKeyTest asserts the typed graph equals this.
 */
private val LEGACY_BUILTIN_FALLBACK_COLORS =
    mapOf(
        "candidate_text_color" to "text_color",
        "comment_text_color" to "candidate_text_color",
        "border_color" to "back_color",
        "candidate_separator_color" to "border_color",
        "hilited_text_color" to "text_color",
        "hilited_back_color" to "back_color",
        "hilited_candidate_text_color" to "hilited_text_color",
        "hilited_candidate_back_color" to "hilited_back_color",
        "hilited_candidate_button_color" to "hilited_candidate_back_color",
        "hilited_label_color" to "hilited_candidate_text_color",
        "hilited_comment_text_color" to "comment_text_color",
        "hilited_key_back_color" to "hilited_candidate_back_color",
        "hilited_key_border_color" to "key_border_color",
        "hilited_key_text_color" to "hilited_candidate_text_color",
        "hilited_key_symbol_color" to "hilited_comment_text_color",
        "hilited_off_key_back_color" to "hilited_key_back_color",
        "hilited_on_key_back_color" to "hilited_key_back_color",
        "hilited_off_key_border_color" to "hilited_key_border_color",
        "hilited_on_key_border_color" to "hilited_key_border_color",
        "hilited_off_key_text_color" to "hilited_key_text_color",
        "hilited_on_key_text_color" to "hilited_key_text_color",
        "hilited_off_key_symbol_color" to "hilited_key_symbol_color",
        "hilited_on_key_symbol_color" to "hilited_key_symbol_color",
        "key_back_color" to "back_color",
        "key_border_color" to "border_color",
        "key_text_color" to "candidate_text_color",
        "key_symbol_color" to "comment_text_color",
        "label_color" to "candidate_text_color",
        "off_key_back_color" to "key_back_color",
        "off_key_border_color" to "key_border_color",
        "off_key_text_color" to "key_text_color",
        "off_key_symbol_color" to "key_symbol_color",
        "on_key_back_color" to "hilited_key_back_color",
        "on_key_border_color" to "hilited_key_border_color",
        "on_key_text_color" to "hilited_key_text_color",
        "on_key_symbol_color" to "hilited_key_symbol_color",
        "popup_back_color" to "key_back_color",
        "popup_text_color" to "key_text_color",
        "hilited_popup_back_color" to "hilited_key_back_color",
        "hilited_popup_text_color" to "hilited_key_text_color",
        "shadow_color" to "border_color",
        "root_background" to "back_color",
        "candidate_background" to "back_color",
        "keyboard_back_color" to "border_color",
        "keyboard_background" to "keyboard_back_color",
        "liquid_keyboard_background" to "keyboard_back_color",
        "text_back_color" to "back_color",
        "long_text_color" to "key_text_color",
        "long_text_back_color" to "key_back_color",
    ).also {
        check(it.size == 49) { "historical fallback table changed: ${it.size}" }
    }
