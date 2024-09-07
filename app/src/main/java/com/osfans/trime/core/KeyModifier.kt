/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core

import android.view.KeyEvent
import splitties.bitflags.hasFlag

/**
 * translated from
 * [librime/key_table.h](https://github.com/rime/librime/blob/1.11.2/src/rime/key_table.h)
 */
enum class KeyModifier(
    val modifier: UInt,
) {
    None(0u),
    Shift(1u shl 0),
    Lock(1u shl 1), // CapsLock
    Control(1u shl 2),
    Mod1(1u shl 3),
    Alt(Mod1),
    Mod2(1u shl 4), // NumLock
    Mod3(1u shl 5),
    Mod4(1u shl 6),
    Mod5(1u shl 7),
    Button1(1u shl 8),
    Button2(1u shl 9),
    Button3(1u shl 10),
    Button4(1u shl 11),
    Button5(1u shl 12),
    Handled(1u shl 24),
    Forward(1u shl 25),
    Ignored(Forward),
    Super(1u shl 26),
    Hyper(1u shl 27),
    Meta(1u shl 28),
    Release(1u shl 30),
    Modifier(0x5f001fffu),
    ;

    constructor(other: KeyModifier) : this(other.modifier)

    infix fun or(other: KeyModifier): UInt = modifier or other.modifier

    infix fun or(other: UInt): UInt = modifier or other
}

operator fun UInt.plus(other: KeyModifier) = or(other.modifier)

operator fun UInt.minus(other: KeyModifier) = and(other.modifier.inv())

@JvmInline
value class KeyModifiers(
    val modifiers: UInt,
) {
    constructor(vararg modifiers: KeyModifier) : this(mergeModifiers(modifiers))

    fun has(modifier: KeyModifier) = modifiers.hasFlag(modifier.modifier)

    val alt get() = has(KeyModifier.Alt)
    val ctrl get() = has(KeyModifier.Control)
    val shift get() = has(KeyModifier.Shift)
    val meta get() = has(KeyModifier.Meta)
    val capsLock get() = has(KeyModifier.Lock)

    val metaState: Int get() {
        var metaState = 0
        if (alt) metaState = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (ctrl) metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (shift) metaState = metaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (meta) metaState = metaState or KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON
        if (capsLock) metaState = metaState or KeyEvent.META_CAPS_LOCK_ON
        return metaState
    }

    fun toInt() = modifiers.toInt()

    companion object {
        val Empty = KeyModifiers(0u)

        fun of(v: Int) = KeyModifiers(v.toUInt())

        fun fromKeyEvent(event: KeyEvent): KeyModifiers {
            var states = KeyModifier.None.modifier
            event.apply {
                if (action == KeyEvent.ACTION_UP) {
                    states += KeyModifier.Release
                } else {
                    if (isAltPressed) states += KeyModifier.Alt
                    if (isCtrlPressed) states += KeyModifier.Control
                    if (isShiftPressed) states += KeyModifier.Shift
                    if (isCapsLockOn) states += KeyModifier.Lock
                    if (isMetaPressed) states += KeyModifier.Meta
                }
            }
            return KeyModifiers(states)
        }

        fun mergeModifiers(arr: Array<out KeyModifier>): UInt = arr.fold(KeyModifier.None.modifier) { acc, it -> acc or it.modifier }
    }
}
