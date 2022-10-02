package com.osfans.trime.ui.main

import android.content.Context
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.Config
import com.osfans.trime.data.sound.SoundManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.core.Trime
import com.osfans.trime.ui.components.CoroutineChoiceDialog
import kotlinx.coroutines.Dispatchers

suspend fun Context.themePicker(
    @StyleRes themeResId: Int = 0
): AlertDialog {
    return CoroutineChoiceDialog(this, themeResId).apply {
        title = getString(R.string.looks__selected_theme_title)
        initDispatcher = Dispatchers.IO
        onInit {
            items = ThemeManager.getAllThemes()
                .map { it.substringBeforeLast('.') }
                .toTypedArray()
            val current = Config.get().theme.substringBeforeLast('.')
            checkedItem = items.indexOf(current)
        }
        postiveDispatcher = Dispatchers.Default
        onOKButton {
            with(items[checkedItem].toString()) {
                Config.get().theme =
                    if (this == "trime") this else "$this.trime"
            }
            Trime.getServiceOrNull()?.initKeyboard()
        }
    }.create()
}

suspend fun Context.colorPicker(
    @StyleRes themeResId: Int = 0
): AlertDialog {
    val prefs by lazy { AppPrefs.defaultInstance() }
    return CoroutineChoiceDialog(this, themeResId).apply {
        title = getString(R.string.looks__selected_color_title)
        initDispatcher = Dispatchers.Default
        onInit {
            val all = Config.get().presetColorSchemes
            items = all.map { it.second }.toTypedArray()
            val current = prefs.looks.selectedColor
            val source = all.map { it.first }.toTypedArray()
            checkedItem = source.indexOf(current)
        }
        postiveDispatcher = Dispatchers.Default
        onOKButton {
            prefs.looks.selectedColor = items[checkedItem].toString()
            Trime.getServiceOrNull()?.initKeyboard() // 立刻重初始化键盘生效
        }
    }.create()
}

suspend fun Context.schemaPicker(
    @StyleRes themeResId: Int = 0
): AlertDialog {
    return CoroutineChoiceDialog(this, themeResId).apply {
        title = getString(R.string.pref_select_schemas)
        initDispatcher = Dispatchers.IO
        onInit {
            items = Rime.get_available_schema_list()
                ?.map { it["schema_id"]!! }
                ?.toTypedArray() ?: arrayOf()
            val checked = Rime.get_selected_schema_list()
                ?.map { it["schema_id"]!! }
                ?.toTypedArray() ?: arrayOf()
            checkedItems = items.map { checked.contains(it) }.toBooleanArray()
        }
        postiveDispatcher = Dispatchers.Default
        onOKButton {
            Rime.select_schemas(
                items.filterIndexed { index, _ ->
                    checkedItems[index]
                }.map { it.toString() }.toTypedArray()
            )
            Rime.destroy()
            Rime.get(true)
        }
    }.create()
}

suspend fun Context.soundPicker(
    @StyleRes themeResId: Int = 0
): AlertDialog {
    return CoroutineChoiceDialog(this, themeResId).apply {
        title = getString(R.string.keyboard__key_sound_package_title)
        initDispatcher = Dispatchers.IO
        onInit {
            items = SoundManager.getAllSounds()
                .map { it.substringBeforeLast('.') }
                .toTypedArray()
            val current = Config.get().soundPackage
                .substringBeforeLast('.')
            checkedItem = items.indexOf(current)
        }
        postiveDispatcher = Dispatchers.Default
        onOKButton {
            Config.get().soundPackage = "${items[checkedItem]}.sound"
        }
    }.create()
}
