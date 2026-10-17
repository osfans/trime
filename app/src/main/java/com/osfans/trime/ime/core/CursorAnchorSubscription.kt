/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

/** Only unsubscribe a cursor monitor that this editor actually accepted. */
internal class CursorAnchorSubscription {
    private var enabled = false

    fun update(enable: Boolean, request: (Boolean) -> Boolean): Boolean {
        if (enabled == enable) return enabled
        val accepted = request(enable)
        enabled = enable && accepted
        return enabled
    }

    // A replaced connection owns its own subscriptions. Never cancel through it.
    fun reset() {
        enabled = false
    }
}
