/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

import android.text.InputType
import android.view.inputmethod.EditorInfo

/** Editor restrictions are temporary; they must never become the user's Chinese mode. */
enum class EditorKeyboard { USER, ASCII, NUMBER }

/** Why the current editor must not retain or expose typed content. */
data class PrivacyMode(
    val password: Boolean = false,
    val appRequested: Boolean = false,
    val manual: Boolean = false,
) {
    val active: Boolean
        get() = password || appRequested || manual

    val allowLearning: Boolean
        get() = !active

    val allowContext: Boolean
        get() = !active

    val allowClipboardHistory: Boolean
        get() = !active

    val allowClipboardSuggestion: Boolean
        get() = !active

    val allowSpeech: Boolean
        get() = !active

    val allowContentAccess: Boolean
        get() = !active
}

object EditorPolicy {
    /**
     * Value of API 26's [EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING]. The flag is an inlined
     * integer bit, so reading it through this compatibility constant is safe on API 21 without
     * resolving a framework field that does not exist there.
     */
    internal const val NO_PERSONALIZED_LEARNING_FLAG = 0x01000000

    fun isPassword(inputType: Int): Boolean = when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_NUMBER -> inputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_NUMBER_VARIATION_PASSWORD

        InputType.TYPE_CLASS_TEXT -> inputType and InputType.TYPE_MASK_VARIATION in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )

        else -> false
    }

    fun keyboard(inputType: Int, imeOptions: Int): EditorKeyboard = when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> EditorKeyboard.NUMBER

        else -> when {
            imeOptions and EditorInfo.IME_FLAG_FORCE_ASCII != 0 -> EditorKeyboard.ASCII

            inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
                inputType and InputType.TYPE_MASK_VARIATION in setOf(
                    InputType.TYPE_TEXT_VARIATION_URI,
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                ) -> EditorKeyboard.ASCII

            else -> EditorKeyboard.USER
        }
    }

    fun privacy(
        inputType: Int,
        imeOptions: Int,
        manual: Boolean,
    ): PrivacyMode = PrivacyMode(
        password = isPassword(inputType),
        appRequested = imeOptions and NO_PERSONALIZED_LEARNING_FLAG != 0,
        manual = manual,
    )

    fun privacy(
        info: EditorInfo,
        manual: Boolean,
    ): PrivacyMode = privacy(info.inputType, info.imeOptions, manual)

    /** Null means a real Enter key, including multiline editors with NO_ENTER_ACTION. */
    fun enterAction(inputType: Int, imeOptions: Int, actionId: Int, actionLabel: CharSequence?): Int? {
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

    fun enterAction(info: EditorInfo): Int? = enterAction(info.inputType, info.imeOptions, info.actionId, info.actionLabel)
}

data class EditorModeOverride(
    val schemaId: String,
    val keyboardId: String,
    val asciiMode: Boolean,
    val themeIdentity: Int = 0,
)
