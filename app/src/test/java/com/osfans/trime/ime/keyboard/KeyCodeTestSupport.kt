/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.keyboard

import android.view.KeyEvent

/** Resolves real SDK constants without calling a throwing Android stub method. */
object KeyCodeTestSupport {
    fun androidKeyNameToCode(name: String): Int = try {
        KeyEvent::class.java.getField("KEYCODE_$name").getInt(null)
    } catch (_: NoSuchFieldException) {
        KeyEvent.KEYCODE_UNKNOWN
    }
}
