/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

import android.text.InputType
import android.view.KeyEvent
import com.osfans.trime.core.KeyModifier
import com.osfans.trime.core.KeyModifiers

private val TEXT_KEY_MODIFIERS = KeyModifier.Shift.modifier or KeyModifier.Lock.modifier or KeyModifier.Mod2.modifier
private const val SHORTCUT_META_STATES = KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK or KeyEvent.META_META_MASK or
    KeyEvent.META_SYM_ON or KeyEvent.META_FUNCTION_ON

/** Text-only virtual fallback; null retains the existing key-event path. */
internal fun virtualKeyText(
    inputType: Int?,
    isVirtual: Boolean,
    value: Int,
    keyCode: Int,
    modifiers: KeyModifiers,
    metaState: Int,
    resolveCharacter: (Int, Int) -> Int,
): String? {
    if (!isVirtual || inputType == null || inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL ||
        value !in 0x20..0x7e || (modifiers.modifiers and TEXT_KEY_MODIFIERS.inv()) != 0u ||
        metaState and SHORTCUT_META_STATES != 0
    ) {
        return null
    }
    // Letter keysyms already carry their case. Mapped Shift/Caps punctuation still
    // needs the same character map that the original Android key event would use.
    val character = if (keyCode != KeyEvent.KEYCODE_UNKNOWN &&
        metaState and (KeyEvent.META_SHIFT_MASK or KeyEvent.META_CAPS_LOCK_ON) != 0
    ) {
        resolveCharacter(keyCode, metaState)
    } else {
        value
    }
    // Dead-character flags, controls and non-ASCII values retain key-event behavior.
    return character.takeIf { it in 0x20..0x7e }?.toChar()?.toString()
}
