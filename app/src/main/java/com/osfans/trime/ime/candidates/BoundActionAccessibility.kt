/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.candidates

import android.view.View
import android.view.accessibility.AccessibilityEvent

/** Invalidate a service's cached node even when the visible text did not change. */
internal fun View.notifyBoundActionChanged() {
    if (isAttachedToWindow && isShown) sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
}
