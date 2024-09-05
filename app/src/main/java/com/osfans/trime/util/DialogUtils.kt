// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.ui.components.log.LogView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.dimensions.dp
import splitties.systemservices.clipboardManager
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalMargin
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.styles.AndroidStyles
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.verticalMargin

// Adapted from https://github.com/fcitx5-android/fcitx5-android/blob/e37f5513239bab279a9e58cf0c9b163e0dbf5efb/app/src/main/java/org/fcitx/fcitx5/android/ui/common/Preset.kt#L60
fun Context.progressBarDialogIndeterminate(
    @StringRes titleId: Int,
): AlertDialog.Builder {
    val androidStyles = AndroidStyles(this)
    return AlertDialog
        .Builder(this)
        .setTitle(titleId)
        .setView(
            verticalLayout {
                add(
                    androidStyles.progressBar.horizontal {
                        isIndeterminate = true
                    },
                    lParams {
                        width = matchParent
                        verticalMargin = dp(26)
                        horizontalMargin = dp(20)
                    },
                )
            },
        ).setCancelable(false)
}

// Adapted from https://github.com/fcitx5-android/fcitx5-android/blob/e37f5513239bab279a9e58cf0c9b163e0dbf5efb/app/src/main/java/org/fcitx/fcitx5/android/ui/common/Preset.kt#L76
fun LifecycleCoroutineScope.withLoadingDialog(
    context: Context,
    thresholds: Long = 200L,
    @StringRes titleId: Int = R.string.loading,
    action: suspend () -> Unit,
) {
    val loading = context.progressBarDialogIndeterminate(titleId).create()
    val job =
        launch {
            delay(thresholds)
            loading.show()
        }
    launch {
        action()
        job.cancelAndJoin()
        if (loading.isShowing) {
            loading.dismiss()
        }
    }
}

suspend fun Context.briefResultLogDialog(
    tag: String,
    priority: String,
    thresholds: Int,
) = withContext(Dispatchers.Main.immediate) {
    val log =
        withContext(Dispatchers.IO) {
            Runtime
                .getRuntime()
                .exec(arrayOf("logcat", "-d", "-v", "time", "-s", "$tag:$priority"))
                .inputStream
                .bufferedReader()
                .readLines()
        }
    if (log.size > thresholds) {
        val logView =
            LogView(this@briefResultLogDialog).apply {
                fromCustomLogLines(log)
                layoutParams =
                    MarginLayoutParams(MarginLayoutParams.MATCH_PARENT, MarginLayoutParams.WRAP_CONTENT).apply {
                        setPadding(dp(20), paddingTop, dp(20), paddingBottom)
                    }
            }
        AlertDialog
            .Builder(this@briefResultLogDialog)
            .setTitle(R.string.setup__done)
            .setMessage(R.string.found_some_problems)
            .setView(logView)
            .setNeutralButton(androidx.preference.R.string.copy) { _, _ ->
                val logText = ClipData.newPlainText("log", log.joinToString("\n"))
                clipboardManager.setPrimaryClip(logText)
                this@briefResultLogDialog.toast(R.string.copy_done)
            }.setPositiveButton(R.string.setup__skip_hint_yes, null)
            .show()
    } else {
        toast(R.string.setup__done)
    }
}

suspend fun Context.rimeActionWithResultDialog(
    tag: String,
    priority: String,
    thresholds: Int,
    action: suspend () -> Boolean,
) {
    withContext(Dispatchers.Main.immediate) {
        withContext(Dispatchers.IO) {
            Runtime.getRuntime().exec(arrayOf("logcat", "-c"))
        }
        val result =
            withContext(Dispatchers.IO) {
                action()
            }
        if (result) {
            briefResultLogDialog(tag, priority, thresholds)
        } else {
            toast("Failed", Toast.LENGTH_LONG)
        }
    }
}
