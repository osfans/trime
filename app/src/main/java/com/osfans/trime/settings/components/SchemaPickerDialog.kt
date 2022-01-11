package com.osfans.trime.settings.components

import android.app.Dialog
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.osfans.trime.R
import com.osfans.trime.Rime
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.setup.Config
import com.osfans.trime.util.RimeUtils
import com.osfans.trime.util.createLoadingDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.CoroutineContext

class SchemaPickerDialog(
    private val context: Context
) : CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private lateinit var schemaItems: Array<String?>
    private lateinit var checkedStatus: BooleanArray
    private var schemaMapList: List<Map<String?, String?>>? = Rime.get_available_schema_list()
    private lateinit var schemaNames: Array<String?>
    var pickerDialogBuilder: AlertDialog.Builder? = null

    private var progressDialog: AlertDialog

    companion object {
        private class SortByName : Comparator<Map<String?, String?>> {
            override fun compare(o1: Map<String?, String?>, o2: Map<String?, String?>): Int {
                val s1 = o1["schema_id"]
                val s2 = o2["schema_id"]
                return if (s1 != null && s2 !== null) {
                    s1.compareTo(s2)
                } else -1
            }
        }
    }

    init {
        progressDialog = createLoadingDialog(context, R.string.schemas_progress)
    }

    private fun showPickerDialog() {
        pickerDialogBuilder = AlertDialog.Builder(context, R.style.AlertDialogTheme).apply {
            setTitle(R.string.pref_schemas)
            setCancelable(true)
            setPositiveButton(android.R.string.ok, null)
        }
        if (schemaMapList.isNullOrEmpty()) {
            pickerDialogBuilder!!.setMessage(R.string.no_schemas)
        } else {
            pickerDialogBuilder!!.apply {
                setNegativeButton(android.R.string.cancel, null)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    progressDialog = createLoadingDialog(context, R.string.deploy_progress).also {
                        appendDialogParams(it)
                        it.show()
                    }
                    launch {
                        try {
                            setSchema()
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to set schema")
                        } finally {
                            progressDialog.dismiss()
                        }
                    }
                }
                setMultiChoiceItems(
                    schemaNames, checkedStatus
                ) { _, id, isChecked -> checkedStatus[id] = isChecked }
            }
        }
        val pickerDialog = pickerDialogBuilder!!.create()
        appendDialogParams(pickerDialog)
        pickerDialog.show()
    }

    private fun appendDialogParams(dialog: Dialog) {
        dialog.window?.let { window ->
            window.attributes.token = Trime.getServiceOrNull()?.window?.window?.decorView?.windowToken
            window.attributes.type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
    }

    private fun initSchemas() {
        if (schemaMapList.isNullOrEmpty()) {
            // 尝试一次配置文件重置 缺失 default.custom.yaml 导致方案为空
            Config.get(context).prepareRime(context)
            schemaMapList = Rime.get_available_schema_list()
            if (schemaMapList.isNullOrEmpty()) return
        }
        schemaMapList!!.sortedWith(SortByName())
        val selectedSchemas = Rime.get_selected_schema_list()
        val selectedIds = ArrayList<String>()
        schemaMapList!!.size.let {
            schemaNames = arrayOfNulls(it)
            checkedStatus = BooleanArray(it)
            schemaItems = arrayOfNulls(it)
        }

        if (selectedSchemas.size > 0) {
            for (m in selectedSchemas) {
                m["schema_id"]?.let { selectedIds.add(it) }
            }
        }
        for ((i, m) in schemaMapList!!.withIndex()) {
            schemaNames[i] = m["name"]
            m["schema_id"].let { // schema_id
                schemaItems[i] = it
                checkedStatus[i] = selectedIds.contains(it)
            }
        }
    }

    private suspend fun setSchema() {
        val checkedIds = ArrayList<String>()
        for ((i, b) in checkedStatus.withIndex()) {
            if (b) schemaItems[i]?.let { checkedIds.add(it) }
        }
        if (checkedIds.size > 0) {
            val schemaIdList = arrayOfNulls<String>(checkedIds.size)
            checkedIds.toArray(schemaIdList)
            Rime.select_schemas(schemaIdList)
            RimeUtils.deploy(context)
        }
    }

    /** 调用该方法显示对话框 **/
    fun show() = execute()

    private fun execute() = launch {
        onPreExecute()
        doInBackground()
        onPostExecute()
    }

    private fun onPreExecute() {
        appendDialogParams(progressDialog)
        progressDialog.show()
    }

    private suspend fun doInBackground(): String = withContext(Dispatchers.IO) {
        initSchemas()
        delay(1000) // Simulate async task
        return@withContext "OK"
    }

    private fun onPostExecute() {
        progressDialog.dismiss()
        showPickerDialog()
    }
}
