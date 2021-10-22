package com.osfans.trime.settings.components

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.blankj.utilcode.util.ToastUtils
import com.osfans.trime.R
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.setup.Config

/** 顯示輸入法內置數據列表，並回廠選中的數據 */
class ResetAssetsDialog(context: Context) {
    private val config = Config.get(context)
    /** 內置數據列表 */
    private var assetItems: Array<String>? = context.assets.list("rime")

    /** 列表勾選狀態 */
    private var checkedStatus: BooleanArray = BooleanArray(assetItems!!.size)

    /** 回廠對話框 */
    val resetDialog: AlertDialog

    init {
        resetDialog = AlertDialog.Builder(context).apply {
            setTitle(R.string.conf__reset_title)
            setCancelable(true)
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(android.R.string.ok) { _, _ ->
                selectAssets()
            }
            setMultiChoiceItems(
                assetItems, checkedStatus
            ) { _, id, isChecked -> checkedStatus[id] = isChecked }
        }.create()
    }

    private fun selectAssets() {
        var res = true
        for (i in assetItems?.indices!!) {
            res = if (checkedStatus[i]) {
                config.copyFileOrDir(assetItems!![i], true)
            } else false
        }
        ToastUtils.showShort(
            if (res) R.string.reset_success else R.string.reset_failure
        )
    }

    /** 彈出對話框 */
    fun show() {
        resetDialog.window?.let { window ->
            window.attributes.token = Trime.getServiceOrNull()?.window?.window?.decorView?.windowToken
            window.attributes.type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
        resetDialog.show()
    }
}
