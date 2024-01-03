package com.osfans.trime.util

import android.content.Context
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.annotation.StringRes
import androidx.appcompat.R.style.Theme_AppCompat_DayNight_Dialog_Alert
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.blankj.utilcode.util.ToastUtils
import com.osfans.trime.R
import com.osfans.trime.ui.components.log.LogView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Adapted from https://github.com/fcitx5-android/fcitx5-android/blob/e37f5513239bab279a9e58cf0c9b163e0dbf5efb/app/src/main/java/org/fcitx/fcitx5/android/ui/common/Preset.kt#L60
fun Context.progressBarDialogIndeterminate(
    @StringRes titleId: Int,
): AlertDialog.Builder {
    return AlertDialog.Builder(this, Theme_AppCompat_DayNight_Dialog_Alert)
        .setTitle(titleId)
        .setView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    ProgressBar(
                        this@progressBarDialogIndeterminate,
                        null,
                        android.R.attr.progressBarStyleHorizontal,
                    ).apply {
                        isIndeterminate = true
                    },
                    MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        val verticalMargin = dp2px(26)
                        val horizontalMargin = dp2px(20)
                        topMargin = horizontalMargin
                        bottomMargin = horizontalMargin
                        leftMargin = verticalMargin
                        rightMargin = verticalMargin
                    },
                )
            },
        )
        .setCancelable(false)
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
            Runtime.getRuntime()
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
                        setPadding(dp2px(20), paddingTop, dp2px(20), paddingBottom)
                    }
            }
        AlertDialog.Builder(this@briefResultLogDialog)
            .setTitle(R.string.setup__done)
            .setMessage(R.string.found_some_problems)
            .setView(logView)
            .setPositiveButton(R.string.setup__skip_hint_yes, null)
            .show()
    } else {
        ToastUtils.showShort(R.string.setup__done)
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
            ToastUtils.showLong("Failed")
        }
    }
}
