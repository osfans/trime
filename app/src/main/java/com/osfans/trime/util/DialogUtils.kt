package com.osfans.trime.util

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.blankj.utilcode.util.SizeUtils
import com.osfans.trime.R
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Suppress("FunctionName")
// Adapted from https://github.com/fcitx5-android/fcitx5-android/blob/e37f5513239bab279a9e58cf0c9b163e0dbf5efb/app/src/main/java/org/fcitx/fcitx5/android/ui/common/Preset.kt#L60
fun Context.ProgressBarDialogIndeterminate(@StringRes titleId: Int): AlertDialog.Builder {
    return AlertDialog.Builder(this, R.style.Theme_AppCompat_DayNight_Dialog_Alert)
        .setTitle(titleId)
        .setView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    ProgressBar(
                        this@ProgressBarDialogIndeterminate,
                        null, android.R.attr.progressBarStyleHorizontal
                    ).apply {
                        isIndeterminate = true
                    },
                    ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        val verticalMargin = SizeUtils.dp2px(26F)
                        val horizontalMargin = SizeUtils.dp2px(20F)
                        topMargin = horizontalMargin
                        bottomMargin = horizontalMargin
                        leftMargin = verticalMargin
                        rightMargin = verticalMargin
                    }
                )
            }
        )
        .setCancelable(false)
}

// Adapted from https://github.com/fcitx5-android/fcitx5-android/blob/e37f5513239bab279a9e58cf0c9b163e0dbf5efb/app/src/main/java/org/fcitx/fcitx5/android/ui/common/Preset.kt#L76
fun LifecycleCoroutineScope.withLoadingDialog(
    context: Context,
    thresholds: Long = 200L,
    @StringRes titleId: Int = R.string.loading,
    action: suspend () -> Unit
) {
    val loading = context.ProgressBarDialogIndeterminate(titleId).create()
    val job = launch {
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
