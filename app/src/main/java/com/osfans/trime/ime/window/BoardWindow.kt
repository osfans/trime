// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.window

import android.content.Context
import android.view.Gravity
import android.view.View
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.Transition
import com.osfans.trime.ime.dependency.InputDependencyManager
import org.kodein.di.instance

sealed class BoardWindow {
    protected val di = InputDependencyManager.getInstance().di

    protected val context: Context by di.instance()

    /**
     * Whether this window represents the virtual keyboard area. When the user enables
     * "hide virtual keyboard", only windows for which this is `true` are hidden, so that
     * non-keyboard panels (option switcher, unrolled candidates, liquid keyboard, ...)
     * remain visible when opened from the toolbar.
     */
    open val isKeyboardArea: Boolean = false

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

    abstract class NoBarBoardWindow : BoardWindow() {
        override fun toString(): String = javaClass.name
    }

    abstract class BarBoardWindow : BoardWindow() {
        open val showTitle: Boolean = true

        open val title: String = ""

        open fun onCreateBarView(): View? = null

        override fun toString(): String = javaClass.name
    }
}
