package com.osfans.trime.ui.components

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.osfans.trime.R
import com.osfans.trime.data.Config
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.util.ProgressBarDialogIndeterminate
import com.osfans.trime.util.popup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
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
        pickerDialog = AlertDialog.Builder(context, R.style.Theme_AppCompat_DayNight_Dialog_Alert).apply {
            setTitle(R.string.looks__selected_theme_title)
            setCancelable(true)
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(android.R.string.ok) { _, _ -> execute() }
            setSingleChoiceItems(
                themeNames, checkedId
            ) { _, id -> checkedId = id }
        }.create()
        // Init progress dialog
        progressDialog = context.ProgressBarDialogIndeterminate(R.string.themes_progress).create()
    }

    private fun setTheme() { config.theme = themeKeys[checkedId]?.replace(".yaml", "") }

    /** 调用该方法显示对话框 **/
    fun show() {
        pickerDialog.popup()
    }

    private fun execute() = launch {
        onPreExecute()
        doInBackground()
        onPostExecute()
    }

    private fun onPreExecute() {
        progressDialog.popup()
    }

    private suspend fun doInBackground(): String = withContext(Dispatchers.IO) {
        setTheme()
//        delay(500) // Simulate async task
        return@withContext "OK"
    }

    private fun onPostExecute() {
        progressDialog.dismiss()
        Trime.getServiceOrNull()?.initKeyboard() // 實時生效
    }
}
