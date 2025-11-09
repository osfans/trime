/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.osfans.trime.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun Context.importErrorDialog(message: String) {
    withContext(Dispatchers.Main.immediate) {
        AlertDialog.Builder(this@importErrorDialog)
            .setTitle(R.string.import_error)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .setIconAttribute(android.R.attr.alertDialogIcon)
            .show()
    }
}

suspend fun Context.importErrorDialog(t: Throwable) {
    importErrorDialog(t.localizedMessage ?: t.stackTraceToString())
}
