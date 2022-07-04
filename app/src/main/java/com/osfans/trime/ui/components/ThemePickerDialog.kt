package com.osfans.trime.ui.components

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.osfans.trime.R
import com.osfans.trime.data.Config
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.util.ProgressBarDialogIndeterminate
import com.osfans.trime.util.popup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/** 顯示配色方案列表 */
class ThemePickerDialog(
    private val context: Context
) : CoroutineScope by MainScope() {

    private lateinit var allThemes: Array<String>
    private var checkedItem: Int = -1

    private suspend fun init() = withContext(Dispatchers.IO) {
        allThemes = ThemeManager.getAllThemes()
            .map { n -> n.substringBeforeLast(".") }
            .toTypedArray()
        Timber.d("allThemes = ${allThemes.joinToString()}")
        allThemes.sort()
        val activeTheme = Config.get(context).theme.replace(".trime", "")
        Timber.d("activeTheme = $activeTheme")
        checkedItem = allThemes.binarySearch(activeTheme)
    }

    private fun buildAndShowDialog() {
        AlertDialog.Builder(context, R.style.Theme_AppCompat_DayNight_Dialog_Alert)
            .setTitle(R.string.looks__selected_theme_title)
            .setNegativeButton(android.R.string.cancel, null)
            .setSingleChoiceItems(
                allThemes, checkedItem
            ) { _, id -> checkedItem = id }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                launch {
                    val applying =
                        context.ProgressBarDialogIndeterminate(R.string.themes_progress)
                            .create()
                    applying.popup()
                    val selected = if (allThemes[checkedItem].contentEquals("trime")) {
                        "trime"
                    } else { allThemes[checkedItem] + ".trime" }
                    Timber.d("Selected theme = $selected")
                    withContext(Dispatchers.IO) {
                        Config.get(context).theme = selected
                    }
                    applying.dismiss()
                    Trime.getServiceOrNull()?.initKeyboard() // 實時生效
                }
            }.create().popup()
    }

    /** 调用该方法显示对话框 **/
    fun show() = launch {
        init()
        buildAndShowDialog()
    }
}
