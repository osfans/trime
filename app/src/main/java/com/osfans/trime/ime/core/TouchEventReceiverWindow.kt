/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow

class TouchEventReceiverWindow(
    private val contentView: View,
) {
    private val ctx = contentView.context

    private val window =
        PopupWindow(
            object : View(ctx) {
                @SuppressLint("ClickableViewAccessibility")
                override fun onTouchEvent(event: MotionEvent): Boolean = contentView.dispatchTouchEvent(event)
            },
        )

    private var isWindowShowing = false

    fun showup() {
        isWindowShowing = true
        val location = intArrayOf(0, 0)
        contentView.getLocationInWindow(location)
        val (left, top) = location
        val width = contentView.width
        val height = contentView.height
        if (window.isShowing) {
            window.update(left, top, width, height)
        } else {
            window.width = width
            window.height = height
            window.showAtLocation(contentView, Gravity.NO_GRAVITY, left, top)
        }
    }

    fun dismiss() {
        if (isWindowShowing) {
            isWindowShowing = false
            window.dismiss()
        }
    }
}
