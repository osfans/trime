/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.util

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/** API 36 adds a third checked state; keyboard toggles have only on and off. */
internal fun AccessibilityNodeInfo.setCheckedState(value: Boolean) {
    if (Build.VERSION.SDK_INT >= 36) {
        setChecked(if (value) AccessibilityNodeInfo.CHECKED_STATE_TRUE else AccessibilityNodeInfo.CHECKED_STATE_FALSE)
    } else {
        // The integer API does not exist on retained Android 5.0–15 devices.
        @Suppress("DEPRECATION")
        isChecked = value
    }
}
