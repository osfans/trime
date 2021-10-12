package com.osfans.trime.settings.components

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import com.osfans.trime.R
import com.osfans.trime.Rime
import com.osfans.trime.settings.PrefMainActivity
import com.osfans.trime.setup.Config
import com.osfans.trime.util.RimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

class SchemaPickerDialog(
    private val context: Context,
    private val token: IBinder?
) : CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private lateinit var schemaItems: Array<String?>
    private lateinit var checkedStatus: BooleanArray
    private var schemaMapList: List<Map<String?, String?>>? = Rime.get_available_schema_list()
    private lateinit var schemaNames: Array<String?>
    var pickerDialogBuilder: AlertDialog.Builder? = null

    @Suppress("DEPRECATION")
    private var progressDialog: ProgressDialog

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

    constructor(context: Context) : this(context, null)

    init {
        @Suppress("DEPRECATION")
        progressDialog = ProgressDialog(context).apply {
            setMessage(context.getString(R.string.schemas_progress))
            setCancelable(false)
        }
    }

    private fun showPickerDialog() {
        pickerDialogBuilder = AlertDialog.Builder(context).apply {
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
                    @Suppress("DEPRECATION")
                    progressDialog = ProgressDialog(context).apply {
                        setMessage(context.getString(R.string.deploy_progress))
                    }.also {
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
                            val intent = Intent(context, PrefMainActivity::class.java)
                            intent.flags = (
                                Intent.FLAG_ACTIVITY_NEW_TASK
                                    or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                )
                            context.startActivity(intent)
                            android.os.Process.killProcess(android.os.Process.myPid())
                            exitProcess(0) // 清理内存
                        }
                    }
                }
                setMultiChoiceItems(
                    schemaNames, checkedStatus
                ) { _, id, isChecked -> checkedStatus[id] = isChecked }
            }
        }
        val pickerDialog = pickerDialogBuilder!!.create()
        if (token != null) appendDialogParams(pickerDialog)
        pickerDialog.show()
    }

    private fun appendDialogParams(dialog: AlertDialog) {
        val window = dialog.window
        val lp = window?.attributes
        lp?.let {
            it.token = token
            it.type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            }
        }
        window?.let {
            it.attributes = lp
            it.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
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

    private fun setSchema() {
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
        if (token != null) appendDialogParams(progressDialog)
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
