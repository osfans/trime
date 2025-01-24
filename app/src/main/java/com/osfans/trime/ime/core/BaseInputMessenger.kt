/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.Theme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

abstract class BaseInputMessenger(
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

    override fun onDetachedFromWindow() {
        handleMessages = false
        super.onDetachedFromWindow()
    }
}
