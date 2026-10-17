/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/** Read the actual platform node; older devices expose only its boolean API. */
internal fun AccessibilityNodeInfo.isCheckedForTest(): Boolean = if (Build.VERSION.SDK_INT >= 36) {
    checked == AccessibilityNodeInfo.CHECKED_STATE_TRUE
} else {
    @Suppress("DEPRECATION")
    isChecked
}
