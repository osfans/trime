/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.clipboard

import android.annotation.SuppressLint
import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.viewpager2.widget.ViewPager2
import com.osfans.trime.data.theme.Theme
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.add
import splitties.views.dsl.core.view

@SuppressLint("ViewConstructor")
class ClipboardLayout(context: Context, theme: Theme) : ConstraintLayout(context) {

    val viewPager = view(::ViewPager2) {}

    val titleUi = ClipboardTitleUi(context, theme)

    init {
        add(
            viewPager,
            lParams {
                centerInParent()
            },
        )
    }
}
