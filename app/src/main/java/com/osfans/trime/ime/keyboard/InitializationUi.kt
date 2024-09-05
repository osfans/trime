// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.content.Context
import android.view.View
import android.widget.ProgressBar
import com.osfans.trime.R
import com.osfans.trime.util.appContext
import com.osfans.trime.util.isStorageAvailable
import splitties.dimensions.dp
import splitties.resources.color
import splitties.views.backgroundColor
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.textColorResource
import splitties.views.textResource

/**
 * Keyboard to be displayed before Rime is deployed completely.
 *
 * It displays a loading screen (if deploying) or error message (if cannot deploy).
 *
 * TODO: Add a help or info button to show what's problem is behind the scene.
 */
class InitializationUi(
    override val ctx: Context,
) : Ui {
    val initial =
        constraintLayout {
            backgroundColor = color(R.color.colorPrimaryDark)
            val textView =
                textView {
                    textResource =
                        if (appContext.isStorageAvailable()) {
                            R.string.deploy_progress
                        } else {
                            R.string.external_storage_permission_not_available
                        }
                    textSize = 24f
                    textAlignment = View.TEXT_ALIGNMENT_CENTER
                    textColorResource = R.color.colorAccent
                }

            val progressBar =
                ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    isIndeterminate = true
                    visibility =
                        if (appContext.isStorageAvailable()) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                }
            add(
                textView,
                lParams(wrapContent, wrapContent) {
                    above(progressBar)
                    topOfParent()
                    centerHorizontally()
                },
            )
            add(
                progressBar,
                lParams(dp(148), dp(24)) {
                    centerHorizontally()
                    bottomOfParent(dp(24))
                },
            )
        }

    override val root =
        constraintLayout {
            add(
                initial,
                lParams(matchParent, (resources.displayMetrics.heightPixels * 0.3).toInt()) {
                    centerHorizontally()
                    bottomOfParent()
                },
            )
        }
}
