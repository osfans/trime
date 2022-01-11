package com.osfans.trime.settings.components

import android.app.Dialog
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.osfans.trime.R
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.setup.Config
import com.osfans.trime.util.createLoadingDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 顯示配色方案列表 */
class ThemePickerDialog(
    private val context: Context
) : CoroutineScope by MainScope() {

    private val config = Config.get(context)
    private val themeKeys: Array<String?>
    private val themeNames: Array<String?>
    private var checkedId: Int = 0
    val pickerDialog: AlertDialog
    @Suppress("DEPRECATION")
    private val progressDialog: AlertDialog

    init {
        val themeFile = config.theme + ".yaml"
        themeKeys = Config.getThemeKeys(true)
        themeKeys.sort()
        checkedId = themeKeys.binarySearch(themeFile)

        val themeMap = HashMap<String, String>().apply {
            put("tongwenfeng", context.getString(R.string.pref_themes_name_tongwenfeng))
            put("trime", context.getString(R.string.pref_themes_name_trime))
        }
        val nameArray = Config.getYamlFileNames(themeKeys)
        themeNames = arrayOfNulls(nameArray.size)

        for (i in nameArray.indices) {
            val themeName = themeMap[nameArray[i]]
            if (themeName == null) {
                themeNames[i] = nameArray[i]
            } else {
                themeNames[i] = themeName
            }
        }
        // Init picker
        pickerDialog = AlertDialog.Builder(context, R.style.AlertDialogTheme).apply {
            setTitle(R.string.looks__selected_theme_title)
            setCancelable(true)
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(android.R.string.ok) { _, _ -> execute() }
            setSingleChoiceItems(
                themeNames, checkedId
            ) { _, id -> checkedId = id }
        }.create()
        // Init progress dialog
        progressDialog = createLoadingDialog(context, R.string.themes_progress)
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

    private fun setTheme() { config.theme = themeKeys[checkedId]?.replace(".yaml", "") }

    /** 调用该方法显示对话框 **/
    fun show() {
        appendDialogParams(pickerDialog)
        pickerDialog.show()
    }

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
        setTheme()
        delay(500) // Simulate async task
        return@withContext "OK"
    }

    private fun onPostExecute() {
        progressDialog.dismiss()
        Trime.getServiceOrNull()?.initKeyboard() // 實時生效
    }
}
