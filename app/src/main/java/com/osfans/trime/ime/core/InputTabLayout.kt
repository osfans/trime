/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import androidx.viewpager2.widget.ViewPager2
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.ime.keyboard.GestureFrame
import com.osfans.trime.util.alpha
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.view
import splitties.views.gravityCenter

class InputTabLayout(context: Context) : LinearLayout(context) {
    inner class TabUi : Ui {
        override val ctx: Context = this@InputTabLayout.context

        var position: Int = -1

        val label = textView()

        override val root = view(::GestureFrame) {
            add(
                label,
                lParams {
                    gravity = gravityCenter
                },
            )
        }

        fun setLabel(str: String) {
            label.text = str
        }

        fun setActive(active: Boolean) {
            val color = label.currentTextColor
                .alpha(if (active) 1f else 0.5f)
            label.setTextColor(color)
        }
    }

    private var tabs: Array<TabUi> = arrayOf()
    private var selected = -1

    private var onPageChangeCallback: ViewPager2.OnPageChangeCallback? = null

    fun onConfigureTab(pager: ViewPager2, setup: (TabUi, Int) -> Unit) {
        onPageChangeCallback?.let { pager.unregisterOnPageChangeCallback(it) }
        tabs.forEach { removeView(it.root) }
        selected = -1
        val adapter = pager.adapter
        if (adapter == null) return
        val adapterCount = adapter.itemCount
        if (adapterCount <= 0) return
        tabs = Array(adapterCount) {
            TabUi().apply {
                position = it
                setup(this, it)
                setActive(false)
            }
        }
        tabs.forEachIndexed { i, tabUi ->
            add(
                tabUi.root,
                lParams {
                    gravity = Gravity.CENTER_VERTICAL
                    setMargins(dp(4), 0, dp(4), 0)
                },
            )
            tabUi.root.setOnClickListener {
                pager.setCurrentItem(i, false)
            }
        }
        val callback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                activateTab(position)
            }
        }
        onPageChangeCallback = callback
        pager.registerOnPageChangeCallback(callback)
    }

    private fun activateTab(index: Int) {
        if (index == selected) return
        if (selected >= 0) {
            tabs[selected].setActive(false)
        }
        tabs[index].setActive(true)
        selected = index
    }
}
