package com.osfans.trime.ime.window

import android.view.View

sealed class BoardWindow {
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
        open fun onCreateBarView(): View? = null

        override fun toString(): String = javaClass.name
    }
}
