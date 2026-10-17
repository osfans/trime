/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ui.common

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ListView
import androidx.appcompat.app.AppCompatViewInflater
import androidx.appcompat.widget.AppCompatCheckedTextView

/** Keep the compatible widgets used by the public settings window and its dialogs. */
internal class PublicWindowViewInflater : AppCompatViewInflater() {
    override fun createCheckedTextView(context: Context, attrs: AttributeSet): AppCompatCheckedTextView {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
            return LegacyListItem(context, attrs)
        }
        return super.createCheckedTextView(context, attrs)
    }

    private class LegacyListItem(context: Context, attrs: AttributeSet) : AppCompatCheckedTextView(context, attrs) {
        override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
            // API 21 TextView consumes ACTION_CLICK before View can invoke the ListView item
            // delegate, even though that delegate advertises the row as clickable. API 22
            // moved this handling into performAccessibilityActionInternal and fixes dispatch.
            if (action == AccessibilityNodeInfo.ACTION_CLICK && !isClickable && !isLongClickable) {
                val list = parent as? ListView
                if (list != null) {
                    val position = list.getPositionForView(this)
                    val adapter = list.adapter
                    if (isEnabled && list.isEnabled && list.isClickable && adapter != null &&
                        position in 0 until adapter.count && adapter.isEnabled(position)
                    ) {
                        // Use the same choice-state and listener path as the ListView delegate.
                        // No View click listener is installed, so physical touch stays unchanged.
                        return list.performItemClick(this, position, adapter.getItemId(position))
                    }
                }
            }
            return super.performAccessibilityAction(action, arguments)
        }
    }
}
