package com.osfans.trime.ui.components

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.blankj.utilcode.util.ResourceUtils
import com.blankj.utilcode.util.ToastUtils
import com.osfans.trime.R
import com.osfans.trime.data.DataManager
import com.osfans.trime.util.appContext
import com.osfans.trime.util.popup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 顯示輸入法內置數據列表，並回廠選中的數據 */
class ResetAssetsDialog(private val context: Context) :
    CoroutineScope by MainScope() {

    /** Internal assets. 内置资源文件列表 **/
    private val assets: Array<String>? = appContext.assets.list("rime")

    /** List to show if items are checked. 检查单 */
    private val checkedList: BooleanArray? = assets?.map { false }?.toBooleanArray()

    private suspend fun selectAssets() = withContext(Dispatchers.IO) {
        if (assets.isNullOrEmpty() || checkedList == null) {
            ToastUtils.showLong(R.string.reset__asset_is_null_or_empty)
            return@withContext
        }
        var res = true
        for ((i, a) in assets.withIndex()) {
            if (checkedList[i]) {
                res = res and (
                    runCatching {
                        ResourceUtils.copyFileFromAssets(
                            "rime/$a",
                            "${DataManager.sharedDataDir.absolutePath}/$a"
                        )
                    }.getOrNull() ?: false
                    )
            }
        }
        ToastUtils.showShort(
            if (res) R.string.reset_success else R.string.reset_failure
        )
    }

    /** 彈出對話框 */
    fun show() {
        AlertDialog.Builder(context)
            .setTitle(R.string.profile_reset)
            .setNegativeButton(android.R.string.cancel, null)
            .setMultiChoiceItems(
                assets, checkedList
            ) { _, id, isChecked -> checkedList?.set(id, isChecked) }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                launch {
                    runCatching { selectAssets() }
                }
            }
            .create().popup()
    }
}
