// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates.unrolled

import android.annotation.SuppressLint
import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.core.add
import splitties.views.dsl.recyclerview.recyclerView

@SuppressLint("ViewConstructor")
class UnrolledCandidateLayout(
    context: Context,
    theme: Theme,
) : ConstraintLayout(context) {
    val recyclerView =
        recyclerView {
            isVerticalScrollBarEnabled = false
        }

    init {
        id = R.id.unrolled_candidate_view
        background =
            ColorManager.getDrawable(
                "candidate_background",
                "candidate_border_color",
                dp(theme.generalStyle.candidateBorder),
                dp(theme.generalStyle.candidateBorderRound),
            )

        add(
            recyclerView,
            lParams {
                centerInParent()
            },
        )
    }

    fun resetPosition() {
        recyclerView.scrollToPosition(0)
    }
}
