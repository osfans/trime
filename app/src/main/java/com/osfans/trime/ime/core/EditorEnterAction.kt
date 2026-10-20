/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** The action shown on Return and the action sent to the editor share this decision. */
internal object EditorEnterAction {
    /** Null means a key event, including a multiline editor's NO_ENTER_ACTION request. */
    fun resolve(inputType: Int, imeOptions: Int, actionId: Int, actionLabel: CharSequence?): Int? {
        if (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL ||
            imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        ) {
            return null
        }
        if (!actionLabel.isNullOrEmpty() && actionId != EditorInfo.IME_ACTION_UNSPECIFIED) return actionId
        return (imeOptions and EditorInfo.IME_MASK_ACTION).takeUnless {
            it == EditorInfo.IME_ACTION_NONE || it == EditorInfo.IME_ACTION_UNSPECIFIED
        }
    }

    /** [mode] retains the theme's enter_label_mode values 0 through 3. */
    fun label(
        actionId: Int?,
        actionLabel: CharSequence?,
        mode: Int,
        defaultLabel: String,
        standardLabel: (Int) -> String?,
    ): String {
        if (actionId == null) return defaultLabel
        val appLabel = actionLabel?.toString()?.takeIf { it.isNotEmpty() }
        return when (mode) {
            // Only app labels: a missing label must not be rendered as the literal "null".
            1 -> appLabel ?: defaultLabel

            // App first, then the theme's label for the action that will actually be sent.
            2 -> appLabel ?: standardLabel(actionId) ?: defaultLabel

            // Theme first, with an app label for actions outside the standard action set.
            3 -> standardLabel(actionId) ?: appLabel ?: defaultLabel

            // Never use app labels, including an invalid theme mode's existing fallback.
            else -> standardLabel(actionId) ?: defaultLabel
        }
    }
}
