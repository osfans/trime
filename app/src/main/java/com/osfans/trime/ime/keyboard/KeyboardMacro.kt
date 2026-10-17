/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.keyboard

/** Preserve the existing mixed-key syntax, including Escape prefixes and literal braces. */
internal fun keyboardMacroParts(input: String): List<String> {
    val escaped = input.replace("{}", "{braceleft}{braceright}")
    val result = mutableListOf<String>()
    var index = 0
    while (index < escaped.length) {
        val value = KEYBOARD_MACRO_PATTERN.matchEntire(escaped.substring(index))?.groupValues?.get(1)
            ?: escaped[index].toString()
        result += value
        index += value.length
    }
    return result
}

private val KEYBOARD_MACRO_PATTERN = """^((?:\{Escape\})?[^{}]+|\{[^{}]+\}).*$""".toRegex()

/**
 * Expand braced text aliases only when the complete macro contains a language action.
 * The caller resolves commit/text priority; null retains the legacy macro dispatcher.
 * Limits reject cyclic/unbounded configurations before any partial command is accepted.
 */
internal fun expandLanguageMacro(
    input: String,
    isLanguageAction: (String) -> Boolean,
    textOfAction: (String) -> String?,
): List<String>? {
    if ('{' !in input) return null
    // First inspect the finite theme alias graph with cycle suppression. Macros
    // without language actions keep their old behavior, even beyond expansion limits.
    val pending = ArrayDeque<String>()
    val inspectedAliases = mutableSetOf<String>()
    pending.addLast(input)
    var hasLanguage = false
    while (pending.isNotEmpty() && !hasLanguage) {
        for (part in keyboardMacroParts(pending.removeFirst())) {
            if (!part.startsWith('{') || !part.endsWith('}')) continue
            val token = part.removeSurrounding("{", "}")
            if (isLanguageAction(token)) {
                hasLanguage = true
                break
            }
            if (inspectedAliases.add(token)) {
                textOfAction(token)?.takeIf { it.isNotEmpty() }?.let(pending::addLast)
            }
        }
    }
    if (!hasLanguage) return null
    var expandedCharacters = 0
    var visitedParts = 0
    data class Expansion(val parts: List<String>, val hasLanguage: Boolean)
    fun expand(value: String, ancestors: Set<String>): Expansion {
        check(ancestors.size <= 8) { "Keyboard text macro exceeds eight alias levels" }
        val parts = mutableListOf<String>()
        var language = false
        for (part in keyboardMacroParts(value)) {
            check(++visitedParts <= 4096) { "Keyboard text macro exceeds 4096 inspected parts" }
            if (part.startsWith('{') && part.endsWith('}')) {
                val token = part.removeSurrounding("{", "}")
                if (isLanguageAction(token)) {
                    language = true
                    parts += part
                    continue
                }
                val nested = textOfAction(token)
                if (!nested.isNullOrEmpty()) {
                    check(token !in ancestors) { "Cyclic keyboard text macro alias" }
                    expandedCharacters += nested.length
                    check(expandedCharacters <= 65536) { "Keyboard text macro alias expansion exceeds 64 KiB" }
                    val child = expand(nested, ancestors + token)
                    if (child.hasLanguage || '{' in nested) {
                        language = language || child.hasLanguage
                        parts += child.parts
                        continue
                    }
                }
            }
            parts += part
        }
        return Expansion(parts, language)
    }
    val result = expand(input, emptySet())
    return result.parts.takeIf { result.hasLanguage }
}
