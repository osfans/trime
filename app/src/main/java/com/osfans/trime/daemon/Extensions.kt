// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.daemon

import com.osfans.trime.core.RimeApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun RimeSession.launchOnReady(block: suspend CoroutineScope.(RimeApi) -> Unit) {
    lifecycleScope.launch {
        runOnReady { block(this) }
    }
}
