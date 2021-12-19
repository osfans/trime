package com.osfans.trime.settings.components

import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.osfans.trime.R
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.setup.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 顯示配色方案列表 */
class SoundPickerDialog(
    private val context: Context
) : CoroutineScope by MainScope() {

    private val config = Config.get(context)
    private val themeKeys: Array<String?>
    private val themeNames: Array<String?>
    private var checkedId: Int = 0
    val pickerDialog: AlertDialog

    @Suppress("DEPRECATION")
    private val progressDialog: ProgressDialog

    init {
        val themeFile = config.soundPackage + ".sound.yaml"
        themeKeys = Config.getSoundPackages()
        themeKeys.sort()
        checkedId = themeKeys.binarySearch(themeFile)

        themeNames = Config.getYamlFileNames(themeKeys)

        // Init picker
        pickerDialog = AlertDialog.Builder(context, R.style.AlertDialogTheme).apply {
            setTitle(R.string.keyboard__key_sound_package_title)
            setCancelable(true)
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(android.R.string.ok) { _, _ -> execute() }
            setSingleChoiceItems(
                themeNames, checkedId
            ) { _, id -> checkedId = id }
        }.create()
        // Init progress dialog
        @Suppress("DEPRECATION")
        progressDialog = ProgressDialog(context).apply {
            setMessage(context.getString(R.string.sound_progress))
            setCancelable(false)
        }
    }

    private fun appendDialogParams(dialog: Dialog) {
        dialog.window?.let { window ->
            window.attributes.token =
                Trime.getServiceOrNull()?.window?.window?.decorView?.windowToken
            window.attributes.type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
    }

    private fun setSound() {
        config.soundPackage = themeKeys[checkedId]?.replace(".sound.yaml", "")
    }

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
        setSound()
        delay(500) // Simulate async task
        return@withContext "OK"
    }

    private fun onPostExecute() {
        progressDialog.dismiss()
    }
}
