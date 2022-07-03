package com.osfans.trime.ui.components

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.osfans.trime.R
import com.osfans.trime.data.Config
import com.osfans.trime.util.ProgressBarDialogIndeterminate
import com.osfans.trime.util.popup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
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
        pickerDialog = AlertDialog.Builder(context, R.style.Theme_AppCompat_DayNight_Dialog_Alert).apply {
            setTitle(R.string.keyboard__key_sound_package_title)
            setCancelable(true)
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(android.R.string.ok) { _, _ -> execute() }
            setSingleChoiceItems(
                soundPackageNames, checkedId
            ) { _, id -> checkedId = id }
        }.create()
        // Init progress dialog
        progressDialog = context.ProgressBarDialogIndeterminate(R.string.sound_progress).create()
    }

    private fun setSound() {
        if (checkedId >= 0 && checkedId <soundPackageFiles.size)
            config.soundPackage = soundPackageFiles[checkedId]?.replace(".sound.yaml", "")
    }

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
        setSound()
//        delay(500) // Simulate async task
        return@withContext "OK"
    }

    private fun onPostExecute() {
        progressDialog.dismiss()
    }
}
