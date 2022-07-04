package com.osfans.trime.ui.components

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.osfans.trime.R
import com.osfans.trime.data.Config
import com.osfans.trime.data.sound.SoundManager
import com.osfans.trime.util.ProgressBarDialogIndeterminate
import com.osfans.trime.util.popup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

// show sound schema list
class SoundPickerDialog(
    private val context: Context
) : CoroutineScope by MainScope() {

    private lateinit var allSounds: Array<String>
    private var checkedItem: Int = -1

    private suspend fun init() = withContext(Dispatchers.IO) {
        allSounds = SoundManager.getAllSounds()
            .map { n -> n.substringBeforeLast(".") }
            .toTypedArray()
        Timber.d("allSounds = ${allSounds.joinToString()}")
        allSounds.sort()
        val activeSound = Config.get(context).soundPackage.substringBeforeLast(".")
        Timber.d("activeSound = $activeSound")
        checkedItem = allSounds.binarySearch(activeSound)
    }

    private fun buildAndShowDialog() {
        AlertDialog.Builder(context, R.style.Theme_AppCompat_DayNight_Dialog_Alert)
            .setTitle(R.string.keyboard__key_sound_package_title)
            .setNegativeButton(android.R.string.cancel, null)
            .setSingleChoiceItems(
                allSounds, checkedItem
            ) { _, id -> checkedItem = id }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                launch {
                    val applying = context.ProgressBarDialogIndeterminate(R.string.sound_progress).create()
                    applying.popup()
                    withContext(Dispatchers.IO) {
                        Config.get(context).soundPackage = allSounds[checkedItem] + ".sound"
                    }
                    applying.dismiss()
                }
            }
            .create().popup()
    }

    /** 调用该方法显示对话框 **/
    fun show() = launch {
        init()
        buildAndShowDialog()
    }
}
