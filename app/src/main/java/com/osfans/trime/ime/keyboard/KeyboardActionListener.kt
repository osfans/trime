/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

interface KeyboardActionListener {
    /**
     * Called when the user presses a key. This is sent before the [.onKey] is called. For
     * keys that repeat, this is only called once.
     *
     * @param keyEventCode the unicode of the key being pressed. If the touch is not on a valid key,
     * the value will be zero.
     */
    fun onPress(keyEventCode: Int)

    /**
     * Called when the user releases a key. This is sent after the [.onKey] is called. For
     * keys that repeat, this is only called once.
     *
     * @param keyEventCode the code of the key that was released
     */
    fun onRelease(keyEventCode: Int)

    fun onEvent(event: Event)

    /**
     * Send a key press to the listener.
     *
     * @param keyEventCode this is the key that was pressed
     * @param metaState the codes for all the possible alternative keys with the primary code being the
     * first. If the primary key code is a single character such as an alphabet or number or
     * symbol, the alternatives will include other characters that may be on the same key or
     * adjacent keys. These codes are useful to correct for accidental presses of a key adjacent
     * to the intended key.
     */
    fun onKey(
        keyEventCode: Int,
        metaState: Int,
    )

    /**
     * Sends a sequence of characters to the listener.
     *
     * @param text the sequence of characters to be displayed.
     */
    fun onText(text: CharSequence)
}
