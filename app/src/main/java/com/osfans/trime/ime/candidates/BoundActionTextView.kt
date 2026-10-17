/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.candidates

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.appcompat.widget.AppCompatTextView

/** A recycled action keeps its semantics independently of RecyclerView's delegate. */
@SuppressLint("ViewConstructor")
internal open class BoundActionTextView(context: Context) :
    AppCompatTextView(context),
    BoundActionOwner {
    override var clickBinding: VersionedClickBinding<*>? = null

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        // API 21 TextView overwrites a delegate's class after super returns.
        // Keep collection metadata from RecyclerView's ItemDelegate and apply
        // the bound action after the complete widget initialization.
        super.onInitializeAccessibilityNodeInfo(info)
        clickBinding?.populateAccessibilityNodeInfo(info)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = clickBinding?.performBoundAccessibilityAction(action) { super.performAccessibilityAction(action, arguments) }
        ?: super.performAccessibilityAction(action, arguments)
}
