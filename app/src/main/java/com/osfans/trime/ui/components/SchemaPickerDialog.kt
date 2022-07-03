@file:Suppress("DEPRECATION")

package com.osfans.trime.ui.components

import android.app.ProgressDialog
import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.blankj.utilcode.util.ToastUtils
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import com.osfans.trime.util.ProgressBarDialogIndeterminate
import com.osfans.trime.util.popup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class SchemaPickerDialog(
    private val context: Context
) : CoroutineScope by MainScope() {

    private lateinit var availableSchemaList: List<Map<String, String?>>
    private lateinit var availableSchemaIds: Array<String?>
    private lateinit var checkedList: BooleanArray

    private suspend fun init() = withContext(Dispatchers.IO) {
        availableSchemaList = Rime.get_available_schema_list() ?: listOf()
        val selectedSchemaList = Rime.get_selected_schema_list()
            ?: listOf<Map<String, String?>>()

        val selectedSchemaIds = selectedSchemaList.map { it["schema_id"] }
        availableSchemaIds = availableSchemaList
            .map { it["schema_id"] }.toTypedArray()
        availableSchemaIds.sort()
        checkedList = availableSchemaIds
            .map { id -> selectedSchemaIds.contains(id) }
            .toBooleanArray()
    }

    /** 调用该方法显示对话框 **/
    fun show() = launch {
        val loading =
            context.ProgressBarDialogIndeterminate(R.string.schemas_progress)
                .create()
        loading.popup()
        init()
        loading.dismiss()
        val picker = AlertDialog.Builder(context, R.style.Theme_AppCompat_DayNight_Dialog_Alert)
            .setTitle(R.string.pref_schemas)
            .setNegativeButton(android.R.string.cancel, null)
        if (availableSchemaList.isEmpty()) {
            picker.setMessage(R.string.no_schemas)
        } else {
            picker.setPositiveButton(android.R.string.ok) { _, _ ->
                    val deploying =
                        context.ProgressBarDialogIndeterminate(R.string.deploy_progress)
                            .create()
                    deploying.popup()
                    val schemasToSelect = availableSchemaIds
                        .filterIndexed { index, _ -> checkedList[index] }
                        .toTypedArray()
                    launch {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                Rime.select_schemas(schemasToSelect)
                                Rime.destroy()
                                Rime.get(context, true)
                            }
                        }
                    }
                    ToastUtils.showLong(R.string.deploy_finish)
                    deploying.dismiss()
                }
                .setMultiChoiceItems(
                    availableSchemaIds, checkedList
                ) { _, id, isChecked -> checkedList[id] = isChecked }
        }
        picker.create().popup()
    }
}
