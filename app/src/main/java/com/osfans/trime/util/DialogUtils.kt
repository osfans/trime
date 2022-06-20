package com.osfans.trime.util

import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.os.Build
import android.view.WindowManager
import com.osfans.trime.ime.core.Trime

@Suppress("DEPRECATION")
fun createLoadingDialog(context: Context, textId: Int): ProgressDialog {
    return ProgressDialog(context).apply {
        setMessage(context.getText(textId))
        setCancelable(false)
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
