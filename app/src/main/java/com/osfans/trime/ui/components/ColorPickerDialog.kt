package com.osfans.trime.ui.components

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import com.osfans.trime.R
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.Config
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.util.popup
import timber.log.Timber

/** 顯示配色方案列表
 *  Show Color Scheme List
 * **/
class ColorPickerDialog(
    context: Context
) {
    val config: Config = Config.get(context)
    private val prefs get() = AppPrefs.defaultInstance()
    private var colorKeys: Array<String>
    private var checkedColorKey: Int = 0
    val pickerDialog: AlertDialog

    init {
        val colorScheme = prefs.looks.selectedColor
        colorKeys = config.colorKeys
        colorKeys.sort()
        val colorNames = config.getColorNames(colorKeys)
        checkedColorKey = colorKeys.binarySearch(colorScheme)

        pickerDialog = AlertDialog.Builder(context, R.style.Theme_AppCompat_DayNight_Dialog_Alert).apply {
            setTitle(R.string.looks__selected_color_title)
            setCancelable(true)
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(android.R.string.ok) { _, _ ->
                selectColor()
            }
            setSingleChoiceItems(
                colorNames, checkedColorKey
            ) { _, id -> checkedColorKey = id }
        }.create()
    }

    private fun selectColor() {
        Timber.i("select")
        if (checkedColorKey !in colorKeys.indices) return
        val colorKey = colorKeys[checkedColorKey]
        prefs.looks.selectedColor = colorKey
        Timber.i("initKeyboard")
        Trime.getServiceOrNull()?.initKeyboard() // 立刻重初始化键盘生效
        Timber.i("done")
    }

    /** 调用该方法显示对话框 **/
    fun show() {
        pickerDialog.popup()
    }
}
