/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.content.res.Resources
import android.view.WindowInsets
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.data.theme.ThemePrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import splitties.dimensions.dp
import kotlin.math.max

abstract class BaseInputView(
    val service: TrimeInputMethodService,
    val rime: RimeSession,
    val theme: Theme,
) : ConstraintLayout(service) {
    protected abstract fun handleRimeMessage(it: RimeMessage<*>)

    private var messageHandlerJob: Job? = null

    private fun setupRimeMessageHandler() {
        messageHandlerJob =
            service.lifecycleScope.launch {
                rime.run { messageFlow }.collect {
                    handleRimeMessage(it)
                }
            }
    }

    var handleMessages = false
        set(value) {
            field = value
            if (field) {
                if (messageHandlerJob == null) {
                    setupRimeMessageHandler()
                }
            } else {
                messageHandlerJob?.cancel()
                messageHandlerJob = null
            }
        }

    private val navBarBackground by ThemeManager.prefs.navbarBackground

    private val navBarFrameHeight: Int
        get() {
            @SuppressLint("DiscouragedApi")
            val resId = resources.getIdentifier("navigation_bar_frame_height", "dimen", "android")
            return try {
                resources.getDimensionPixelSize(resId)
            } catch (e: Resources.NotFoundException) {
                dp(FALLBACK_NAVBAR_HEIGHT)
            }
        }

    protected fun getNavBarBottomInset(windowInsets: WindowInsets): Int {
        if (navBarBackground != ThemePrefs.NavbarBackground.FULL) {
            return 0
        }
        val insets = WindowInsetsCompat.toWindowInsetsCompat(windowInsets)
        val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        val mandatory = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
        // If navBars is 0 but mandatory is non-zero, likely gesture nav - don't add inset
        if (navBars.bottom == 0 && mandatory.bottom > 0) {
            return 0
        }
        var insetsBottom = max(navBars.bottom, mandatory.bottom)
        if (insetsBottom <= 0) {
            val gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures())
            val gesturesBottom = gestures.bottom
            if (gesturesBottom > 0) {
                insetsBottom = max(gesturesBottom, navBarFrameHeight)
            }
        }
        return insetsBottom
    }

    override fun onDetachedFromWindow() {
        handleMessages = false
        super.onDetachedFromWindow()
    }

    companion object {
        private const val FALLBACK_NAVBAR_HEIGHT = 48
    }
}
