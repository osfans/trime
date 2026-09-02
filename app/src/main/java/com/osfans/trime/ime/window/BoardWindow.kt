// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.window

import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.Transition
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance

sealed class BoardWindow(override val di: DI) : DIAware {

    protected val context: ContextThemeWrapper by instance()

    /**
     * Animation when the window is added to the layout
     */
    open fun enterAnimation(lastWindow: BoardWindow): Transition? = Slide().apply {
        slideEdge = Gravity.TOP
    }

    /**
     * Animation when the window is removed from the layout
     */
    open fun exitAnimation(nextWindow: BoardWindow): Transition? = Fade()

    /**
     * After the window was set up in InputComponent
     */
    abstract fun onCreateView(): View

    /**
     * After the view was added to window manager's layout
     */
    abstract fun onAttached()

    /**
     * Before the view is removed from window manager's layout
     */
    abstract fun onDetached()

    abstract class NoBarBoardWindow(di: DI) : BoardWindow(di) {
        override fun toString(): String = javaClass.name
    }

    abstract class BarBoardWindow(di: DI) : BoardWindow(di) {
        open val showTitle: Boolean = true

        open val title: String = ""

        open fun onCreateBarView(): View? = null

        override fun toString(): String = javaClass.name
    }
}
