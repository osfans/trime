package com.osfans.trime.settings.components

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import com.osfans.trime.Config
import com.osfans.trime.R

/** 顯示輸入法內置數據列表，並回廠選中的數據 */
class ResetAssetsDialog(private val context: Context) {
    private val config = Config.get(context)
    /** 內置數據列表 */
    private var assetItems: Array<String?> = Config.list(context, "rime")

    /** 列表勾選狀態 */
    private var checkedStatus: BooleanArray = BooleanArray(assetItems.size)

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
        var result = true
        for (i in assetItems.indices) {
            result = if (checkedStatus[i]) {
                config.copyFileOrDir(context, assetItems[i], true)
            } else false
        }
        Toast.makeText(context,
            if (result) R.string.reset_success else R.string.reset_failure,
            Toast.LENGTH_SHORT).show()
    }

    /** 彈出對話框 */
    fun show() = resetDialog.show()
}