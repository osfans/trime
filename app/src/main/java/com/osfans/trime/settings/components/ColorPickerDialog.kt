package com.osfans.trime.settings.components

import android.app.AlertDialog
import android.content.Context
import com.osfans.trime.Config
import com.osfans.trime.R
import com.osfans.trime.Trime

/** 顯示配色方案列表
 *  Show Color Scheme List
 * **/
class ColorPickerDialog(
    context: Context
) {
    val config: Config = Config.get(context)
    private var colorKeys: Array<String>
    private var checkedColor: Int = 0
    val pickerDialog: AlertDialog

    init {
        val colorScheme = config.colorScheme
        colorKeys = config.colorKeys
        colorKeys.sort()
        val colorNames = config.getColorNames(colorKeys)
        checkedColor = colorKeys.binarySearch(colorScheme)

        pickerDialog = AlertDialog.Builder(context).apply {
            setTitle(R.string.pref_colors)
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
        config.setColor(colorKey)
        Trime.getService()?.initKeyboard() // 立刻重初始化键盘生效
    }

    /** 调用该方法显示对话框 **/
    fun show() = pickerDialog.show()
}