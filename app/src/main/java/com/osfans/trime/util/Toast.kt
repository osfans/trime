// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

// Adapted from https://github.com/fcitx5-android/fcitx5-android/blob/59558c5b624359455911082b10750f4dcbd10fe8/app/src/main/java/org/fcitx/fcitx5/android/utils/Toast.kt
package com.osfans.trime.util

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import com.osfans.trime.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Context.toast(
    string: String,
    duration: Int = Toast.LENGTH_SHORT,
) {
    Toast.makeText(this, string, duration).show()
}

fun Context.toast(
    @StringRes resId: Int,
    duration: Int = Toast.LENGTH_SHORT,
) {
    Toast.makeText(this, resId, duration).show()
}

fun Context.toast(
    t: Throwable,
    duration: Int = Toast.LENGTH_SHORT,
) {
    toast(t.localizedMessage ?: t.stackTraceToString(), duration)
}

suspend fun <T> Context.toast(
    result: Result<T>,
    duration: Int = Toast.LENGTH_SHORT,
) {
    withContext(Dispatchers.Main.immediate) {
        result
            .onSuccess { toast(R.string.setup__done, duration) }
            .onFailure { toast(it, duration) }
    }
}
