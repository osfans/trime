/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class CancellableDelay {
    private var deferred: CompletableDeferred<Unit>? = null

    suspend fun delay(timeMillis: Long): Boolean {
        deferred = CompletableDeferred()

        return try {
            withTimeout(timeMillis) {
                deferred?.await()
            }
            false
        } catch (e: TimeoutCancellationException) {
            false
        } catch (e: CancellationException) {
            true
        } finally {
            deferred = null
        }
    }

    fun skipDelay() {
        deferred?.complete(Unit)
    }
}
