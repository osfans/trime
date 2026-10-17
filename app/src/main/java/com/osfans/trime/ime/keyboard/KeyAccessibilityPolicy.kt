/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.keyboard

/** Accessibility activation cannot consume an in-flight touch or start a second dispatch. */
internal class GestureAccessibilityActivation {
    private var active = false

    fun begin(enabled: Boolean, touchActive: Boolean, pendingTouchAction: Boolean): Boolean {
        if (!enabled || touchActive || pendingTouchAction || active) return false
        active = true
        return true
    }

    fun finish() {
        active = false
    }
}

/** Only visible key captions are considered; action text, editor text and clipboard are not inputs. */
internal object KeyAccessibilityLabelPolicy {
    private val actionExpression = Regex("\\{[A-Za-z0-9_]+(?:\\+[A-Za-z0-9_]+)*\\}")
    private fun readable(value: String): String = value.trim().takeUnless {
        it.startsWith("ic@") || it == "enter_labels" || actionExpression.matches(it)
    }.orEmpty()

    fun label(display: String, hint: String, symbol: String, functional: String?, fallback: String, preferDisplay: Boolean = false): String {
        val visible = readable(display)
        val meaning = functional?.let(::readable).orEmpty()
        val primary = (if (preferDisplay) visible.ifEmpty { meaning } else meaning.ifEmpty { visible }).ifEmpty { fallback }
        return listOf(primary, readable(hint), readable(symbol)).filter { it.isNotEmpty() }.distinct().joinToString(" ")
    }
}
