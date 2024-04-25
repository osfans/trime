// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

/**
 * Adapted from [fcitx5-android/Exception](https://github.com/fcitx5-android/fcitx5-android/blob/e44c1c7/app/src/main/java/org/fcitx/fcitx5/android/utils/Exception.kt)
 */
package com.osfans.trime.util

import androidx.annotation.StringRes

inline fun <T : Throwable> errorT(
    cons: (String) -> T,
    @StringRes messageTemplate: Int,
    messageArg: String? = null,
): Nothing =
    throw cons(
        messageArg?.let {
            appContext.getString(messageTemplate, it)
        } ?: appContext.getString(
            messageTemplate,
        ),
    )

fun errorState(
    @StringRes messageTemplate: Int,
    messageArg: String? = null,
): Nothing = errorT(::IllegalStateException, messageTemplate, messageArg)

fun errorArg(
    @StringRes messageTemplate: Int,
    messageArg: String? = null,
): Nothing = errorT(::IllegalArgumentException, messageTemplate, messageArg)

fun errorRuntime(
    @StringRes messageTemplate: Int,
    messageArg: String? = null,
): Nothing = errorT(::RuntimeException, messageTemplate, messageArg)
