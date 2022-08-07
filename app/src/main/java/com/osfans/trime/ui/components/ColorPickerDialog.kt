package com.osfans.trime.ui.components

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.osfans.trime.R
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.Config
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.util.popup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import timber.log.Timber

/** 顯示配色方案列表
 *  Show Color Scheme List
 * **/
class ColorPickerDialog(
    private val context: Context
) : CoroutineScope by MainScope() {

    private val prefs get() = AppPrefs.defaultInstance()
    private lateinit var allColorKeys: Array<String>
    private lateinit var allColorNames: Array<String>
    private var checkedItem: Int = -1

    private fun buildAndShowDialog() {
        AlertDialog.Builder(context, R.style.Theme_AppCompat_DayNight_Dialog_Alert)
            .setTitle(R.string.looks__selected_color_title)
            .setNegativeButton(android.R.string.cancel, null)
            .setSingleChoiceItems(
                allColorNames, checkedItem
            ) { _, id -> checkedItem = id }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (checkedItem in allColorKeys.indices) {
                    launch {
                        Timber.i("Applying color scheme ...")
                        prefs.looks.selectedColor = allColorKeys[checkedItem]
                        Timber.i("Initializing keyboard ...")
                        Trime.getServiceOrNull()?.initKeyboard() // 立刻重初始化键盘生效
                        Timber.i("Applying done")
                    }
                }
            }
            .create().popup()
    }

    private fun init() {
        val allColors = Config.get(context).presetColorSchemes.toTypedArray()
        allColorKeys = allColors.map { a -> a.first }.toTypedArray()
        allColorNames = allColors.map { a -> a.second }.toTypedArray()
        val activeColor = prefs.looks.selectedColor
        Timber.d("activeColor = $activeColor")
        checkedItem = allColorKeys.indexOf(activeColor)
    }

    /** 调用该方法显示对话框 **/
    fun show() = launch {
        init()
        buildAndShowDialog()
    }
}
