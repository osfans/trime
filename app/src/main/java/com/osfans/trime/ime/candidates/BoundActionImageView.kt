/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.candidates

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.accessibility.AccessibilityNodeInfo
import androidx.appcompat.widget.AppCompatImageView
import com.osfans.trime.R

/** API 21 ImageView initializes its class after its View delegate has run. */
@SuppressLint("ViewConstructor")
internal class BoundActionImageView(context: Context) :
    AppCompatImageView(ContextThemeWrapper(context, R.style.Theme_TrimeAppTheme)),
    BoundActionOwner {
    override var clickBinding: VersionedClickBinding<*>? = null

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        clickBinding?.populateAccessibilityNodeInfo(info)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = clickBinding?.performBoundAccessibilityAction(action) { super.performAccessibilityAction(action, arguments) }
        ?: super.performAccessibilityAction(action, arguments)
}
