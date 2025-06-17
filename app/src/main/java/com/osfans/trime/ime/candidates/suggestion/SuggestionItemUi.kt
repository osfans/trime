// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.suggestion

import android.content.Context
import android.os.Build
import android.view.View
import androidx.annotation.RequiresApi
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.util.pressHighlightDrawable
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.core.Ui

class SuggestionItemUi(
    override val ctx: Context,
) : Ui {
    override val root =
        constraintLayout {
            background = pressHighlightDrawable(ColorManager.getColor("hilited_candidate_back_color"))
        }

    @RequiresApi(Build.VERSION_CODES.R)
    fun addView(view: View) {
        root.removeAllViews()
        root.addView(view)
    }
}
