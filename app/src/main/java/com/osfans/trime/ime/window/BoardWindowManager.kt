// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.window

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.transition.Transition
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet
import com.osfans.trime.R
import com.osfans.trime.ime.broadcast.InputBroadcaster
import com.osfans.trime.ime.dependency.InputScope
import me.tatarka.inject.annotations.Inject
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import timber.log.Timber

@InputScope
@Inject
class BoardWindowManager(
    private val context: Context,
    private val broadcaster: InputBroadcaster,
) {
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

    @Suppress("BOUNDS_NOT_ALLOWED_IF_BOUNDED_BY_TYPE_PARAMETER")
    fun <W : BoardWindow, E : ResidentWindow, R> cacheResidentWindow(
        window: R,
        createView: Boolean = false,
    ) where R : W, R : E {
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
            if (window is ResidentWindow) {
                cachedResidentWindows[window.key]?.second ?: window
                    .onCreateView()
                    .also { cachedResidentWindows[window.key] = window to it }
            } else {
                broadcaster.addReceiver(window)
                window.onCreateView()
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
        Timber.d("Attach $window")
        window.onAttached()
        currentWindow = window
        broadcaster.onWindowAttached(window)
    }

    val view: FrameLayout by lazy { context.frameLayout(R.id.input_window) }

    fun isAttached(window: BoardWindow) = currentWindow === window
}
