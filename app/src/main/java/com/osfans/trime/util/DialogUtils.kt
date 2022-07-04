package com.osfans.trime.util

import android.app.Dialog
import android.content.Context
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.blankj.utilcode.util.SizeUtils
import com.osfans.trime.R
import com.osfans.trime.ime.core.Trime
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

/**
 * Append layout params to a dialog and show it, which allow the dialog to show
 * outside of Activity of this application. This requires overlays permission.
 *
 * 追加布局参数到对话框并显示它，这可以让它在本应用的 Activity 之外显示。要求有悬浮窗权限。
 */
fun Dialog.popup() {
    this.window?.let { window ->
        window.attributes.token = Trime.getServiceOrNull()?.window?.window?.decorView?.windowToken
        window.attributes.type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }
    this.show()
}
