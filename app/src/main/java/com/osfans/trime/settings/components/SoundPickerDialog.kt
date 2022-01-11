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

// show sound schema list
class SoundPickerDialog(
    context: Context
) : CoroutineScope by MainScope() {

    private val config = Config.get(context)
    private val soundPackageFiles: Array<String?>
    private val soundPackageNames: Array<String?>
    private var checkedId: Int = 0
    val pickerDialog: AlertDialog

    private val progressDialog: AlertDialog

    init {
        soundPackageFiles = Config.getSoundPackages()
        soundPackageFiles.sort()
        checkedId = soundPackageFiles.binarySearch(config.soundPackage + ".sound.yaml")

        soundPackageNames = Config.getYamlFileNames(soundPackageFiles)

        // Init picker
        pickerDialog = AlertDialog.Builder(context, R.style.AlertDialogTheme).apply {
            setTitle(R.string.keyboard__key_sound_package_title)
            setCancelable(true)
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(android.R.string.ok) { _, _ -> execute() }
            setSingleChoiceItems(
                soundPackageNames, checkedId
            ) { _, id -> checkedId = id }
        }.create()
        // Init progress dialog
        progressDialog = createLoadingDialog(context, R.string.sound_progress)
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
        if (checkedId >= 0 && checkedId <soundPackageFiles.size)
            config.soundPackage = soundPackageFiles[checkedId]?.replace(".sound.yaml", "")
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
