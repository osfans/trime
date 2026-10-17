/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

/** Window-coordinate safe areas not already excluded by the system's layout. */
internal data class ImeHorizontalInsets(val left: Int = 0, val right: Int = 0) {
    companion object {
        fun remaining(windowWidth: Int, viewLeft: Int, viewWidth: Int, insetLeft: Int, insetRight: Int): ImeHorizontalInsets {
            if (windowWidth <= 0 || viewWidth <= 0) return ImeHorizontalInsets()
            val left = (insetLeft - viewLeft).coerceIn(0, viewWidth)
            val right = (insetRight - (windowWidth - viewLeft - viewWidth)).coerceIn(0, viewWidth - left)
            return ImeHorizontalInsets(left, right)
        }
    }
}
