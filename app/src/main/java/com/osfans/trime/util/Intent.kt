/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.content.ComponentName
import android.content.Intent
import android.net.Uri

private const val ANDROID_INTENT_ACTION_PREFIX = "android.intent.action"

fun buildIntentFromArgument(argument: String): Intent? = if (argument.contains(':')) { // URI
    Intent.parseUri(argument, Intent.URI_INTENT_SCHEME)
} else if (argument.contains('/')) {
    Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        component = ComponentName.unflattenFromString(argument)
    }
} else {
    appContext.packageManager.getLaunchIntentForPackage(argument) // Package name
}

fun buildIntentFromAction(
    action: String,
    argument: String = "",
): Intent? = when (val fullAction = "$ANDROID_INTENT_ACTION_PREFIX.${action.uppercase()}") {
    Intent.ACTION_WEB_SEARCH, Intent.ACTION_SEARCH -> {
        buildIntentFromArgument(argument)
    }
    Intent.ACTION_SEND -> {
        Intent(fullAction).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, argument)
        }
    }
    else -> Intent(fullAction, Uri.parse(argument))
}
