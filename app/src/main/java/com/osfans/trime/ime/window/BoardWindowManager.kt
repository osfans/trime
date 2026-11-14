/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.window

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.transition.Transition
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.osfans.trime.R
import com.osfans.trime.ime.broadcast.InputBroadcaster
import com.osfans.trime.ime.keyboard.KeyboardWindow
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import timber.log.Timber

class BoardWindowManager(override val di: DI) : DIAware {
    private val context by instance<ContextThemeWrapper>()
    private val broadcaster by instance<InputBroadcaster>()

    private val cachedResidentWindows = mutableMapOf<ResidentWindow.Key, Pair<BoardWindow, View?>>()

    private var currentWindow: BoardWindow? = null
    private var currentView: View? = null

    private fun prepareAnimation(
        exitAnimation: Transition?,
        enterAnimation: Transition?,
        remove: View,
        add: View,
    ) {
        enterAnimation?.addTarget(add)
        exitAnimation?.addTarget(remove)
        TransitionManager.beginDelayedTransition(
            view,
            TransitionSet().apply {
                enterAnimation?.let { addTransition(it) }
                exitAnimation?.let { addTransition(it) }
                duration = 100
            },
        )
    }

    fun <R> cacheResidentWindow(
        window: R,
        createView: Boolean = false,
    ) where R : BoardWindow, R : ResidentWindow {
        if (window.key in cachedResidentWindows) {
            if (cachedResidentWindows[window.key]!!.first === window) {
                Timber.d("Skip adding resident window $window")
            } else {
                throw IllegalStateException("${window.key} is already occupied")
            }
        }
        broadcaster.addReceiver(window)
        val view = if (createView) window.onCreateView() else null
        cachedResidentWindows[window.key] = window to view
    }

    fun attachWindow(windowKey: ResidentWindow.Key) {
        cachedResidentWindows[windowKey]?.let { (window, _) ->
            attachWindow(window)
        } ?: throw IllegalStateException("$windowKey is not a known resident window key")
    }

    fun attachWindow(window: BoardWindow) {
        if (window === currentWindow) {
            Timber.d("Skip attaching $window")
        }
        val newView =
            try {
                createWindowView(window)
            } catch (e: Exception) {
                if (window !is ResidentWindow) broadcaster.removeReceiver(window)
                Timber.e(e, "Failed to create the view of $window, staying on $currentWindow")
                return
            }
        if (currentWindow != null) {
            val oldWindow = currentWindow!!
            val oldView = currentView!!
            prepareAnimation(
                oldWindow.exitAnimation(window),
                window.enterAnimation(oldWindow),
                oldView,
                newView,
            )
            oldWindow.onDetached()
            view.removeView(oldView)
            broadcaster.onWindowDetached(oldWindow)
            Timber.d("Detach $oldWindow")
            if (oldWindow !is ResidentWindow) {
                broadcaster.removeReceiver(oldWindow)
            }
        }
        if (window is ResidentWindow) {
            window.beforeAttached()
        }
        view.apply { add(newView, lParams(matchParent, matchParent)) }
        currentView = newView
        currentWindow = window
        Timber.d("Attach $window")
        try {
            window.onAttached()
        } catch (e: Exception) {
            Timber.e(e, "Failed to attach $window")
            recoverFromAttachFailure(window, newView)
            return
        }
        broadcaster.onWindowAttached(window)
    }

    private fun createWindowView(window: BoardWindow): View = if (window is ResidentWindow) {
        cachedResidentWindows[window.key]?.second ?: window
            .onCreateView()
            .also { cachedResidentWindows[window.key] = window to it }
    } else {
        broadcaster.addReceiver(window)
        window.onCreateView()
    }

    /** A window that failed right after being added leaves no view on screen, so put the keyboard back. */
    private fun recoverFromAttachFailure(failedWindow: BoardWindow, failedView: View) {
        view.removeView(failedView)
        currentView = null
        currentWindow = null
        if (failedWindow !is ResidentWindow) {
            broadcaster.removeReceiver(failedWindow)
        }
        if (failedWindow !is KeyboardWindow) {
            attachWindow(KeyboardWindow)
        }
    }

    /** Restyles the window currently on screen after a scheme switch. */
    fun refreshColors() {
        currentWindow?.refreshColors()
    }

    val view: FrameLayout by lazy { context.frameLayout(R.id.input_window) }

    fun isAttached(window: BoardWindow) = currentWindow === window
}
