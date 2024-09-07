/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core

import android.view.KeyEvent

@JvmInline
value class KeyValue(
    val value: Int,
) {
    val keyCode get() = RimeKeyMapping.valToKeyCode(value)

    override fun toString() = "0x" + value.toString(16).padStart(4, '0')

    companion object {
        fun fromKeyEvent(event: KeyEvent) = KeyValue(RimeKeyMapping.keyCodeToVal(event.keyCode))
    }
}
