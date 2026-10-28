/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.osfans.trime.R

/** Ask before opening a picker, which also persists access to the selected folder. */
fun Context.confirmExternalSync(
    @StringRes positiveButton: Int = android.R.string.ok,
    @StringRes introMessage: Int? = null,
    onConfirmed: () -> Unit,
) {
    val message = listOfNotNull(
        introMessage?.let { getString(it) },
        getString(R.string.external_sync_confirmation_message),
    ).joinToString("\n\n")
    AlertDialog
        .Builder(this)
        .setTitle(R.string.sync_from_external)
        .setMessage(message)
        .setPositiveButton(positiveButton) { _, _ -> onConfirmed() }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}
