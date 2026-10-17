/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ui.setup

internal fun needsInputSetup(
    started: Boolean,
    freshInputPending: Boolean,
    inputConfirmed: Boolean,
    trialCompleted: Boolean,
    deferred: Boolean,
): Boolean = !deferred && (started || freshInputPending) && (!inputConfirmed || !trialCompleted)
