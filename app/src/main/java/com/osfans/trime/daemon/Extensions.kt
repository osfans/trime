// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.daemon

import com.osfans.trime.core.RimeApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A closed owner cancels queued work; unrelated operation failures remain visible. */
fun RimeSession.launchOnReady(block: suspend CoroutineScope.(RimeApi) -> Unit) {
    val scope = try {
        lifecycleScope
    } catch (_: RimeSessionClosedException) {
        return
    }
    scope.launch {
        try {
            runOnReady { block(this) }
        } catch (closed: RimeSessionClosedException) {
            throw CancellationException(closed.message).apply { initCause(closed) }
        }
    }
}
