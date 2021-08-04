package com.osfans.trime.settings.components

import android.app.AlertDialog
import android.content.Context
import com.osfans.trime.Config
import com.osfans.trime.R
import com.osfans.trime.ime.core.Preferences
import com.osfans.trime.ime.core.Trime

/** 顯示配色方案列表
 *  Show Color Scheme List
 * **/
class ColorPickerDialog(
    context: Context
) {
    val config: Config = Config.get(context)
    private val prefs get() = Preferences.defaultInstance()
    private var colorKeys: Array<String>
    private var checkedColor: Int = 0
    val pickerDialog: AlertDialog

    init {
        val colorScheme = prefs.looks.selectedColor
        colorKeys = config.colorKeys
        colorKeys.sort()
        val colorNames = config.getColorNames(colorKeys)
        checkedColor = colorKeys.binarySearch(colorScheme)

        pickerDialog = AlertDialog.Builder(context).apply {
            setTitle(R.string.looks__selected_color_title)
            setCancelable(true)
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(android.R.string.ok) { _, _ ->
                selectColor()
            }
            setSingleChoiceItems(
                colorNames, checkedColor
            ) {_, id -> checkedColor = id}
        }.create()
    }

    private fun selectColor() {
        if (checkedColor < 0 || checkedColor >= colorKeys.size) return
        val colorKey = colorKeys[checkedColor]
        prefs.looks.selectedColor = colorKey
        Trime.getService()?.initKeyboard() // 立刻重初始化键盘生效
    }

    /** 调用该方法显示对话框 **/
    fun show() = pickerDialog.show()
}