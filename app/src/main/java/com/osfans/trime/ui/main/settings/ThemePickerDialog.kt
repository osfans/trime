// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main.settings

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import com.osfans.trime.data.sync.RimeDataSync
import com.osfans.trime.data.theme.ThemeItem
import com.osfans.trime.data.theme.ThemeLoader
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.util.ColorUtils
import com.osfans.trime.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.dimensions.dp
import timber.log.Timber

object ThemePickerDialog {
    /** 预览用到的关键颜色键：背景、按键字、正文、候选高亮、边框。 */
    private val SWATCH_KEYS =
        listOf(
            "backColor",
            "keyTextColor",
            "textColor",
            "hilitedCandidateTextColor",
            "borderColor",
        )

    private data class ThemePreview(
        val item: ThemeItem,
        val light: List<Int>,
        val dark: List<Int>,
    )

    suspend fun build(
        scope: LifecycleCoroutineScope,
        context: Context,
        afterConfirm: (suspend () -> Unit)? = null,
    ): AlertDialog {
        val previews = withContext(Dispatchers.IO) { loadPreviews() }
        val selectedTheme by ThemeManager.prefs.selectedTheme
        val selectedIndex = previews.indexOfFirst { it.item.configId == selectedTheme }
        return AlertDialog
            .Builder(context)
            .apply {
                setTitle(R.string.selected_theme)
                if (previews.isEmpty()) {
                    setMessage(R.string.no_theme_to_select)
                } else {
                    setSingleChoiceItems(ThemeListAdapter(context, previews), selectedIndex) { dialog, which ->
                        scope.launch {
                            afterConfirm?.invoke()
                            val newItem = previews[which].item
                            withContext(Dispatchers.IO) {
                                if (RimeDataSync.usesExternalSync()) {
                                    RimeDataSync.importThemeToLocal(newItem.configId)
                                        .onFailure { Timber.w(it, "Theme import failed for ${newItem.configId}") }
                                }
                            }
                            val resolvedThemeId = ThemeManager.selectTheme(newItem.configId)
                            dialog.dismiss()
                            if (resolvedThemeId != newItem.configId) {
                                val fallbackName =
                                    previews.firstOrNull { it.item.configId == resolvedThemeId }?.item?.name
                                        ?: resolvedThemeId
                                context.toast(
                                    context.getString(
                                        R.string.theme_unavailable_fallback,
                                        newItem.name,
                                        fallbackName,
                                    ),
                                    Toast.LENGTH_LONG,
                                )
                            }
                        }
                    }
                }
                setNegativeButton(android.R.string.cancel, null)
            }.create()
    }

    /** Loads every theme and resolves its light/dark swatch colors. */
    private fun loadPreviews(): List<ThemePreview> =
        ThemeManager.getAllThemes().map { item ->
            val theme = (ThemeLoader.loadTheme(item.configId) as? ThemeLoader.ThemeLoadResult.Success)?.theme
            ThemePreview(
                item = item,
                light = theme?.colorSchemas?.light?.toSwatches() ?: emptyList(),
                dark = theme?.colorSchemas?.dark?.toSwatches() ?: emptyList(),
            )
        }

    private fun Map<String, String>.toSwatches(): List<Int> =
        SWATCH_KEYS.mapNotNull { key ->
            this[key]?.let { raw -> runCatching { ColorUtils.parseColor(raw) }.getOrNull() }
        }

    private class ThemeListAdapter(
        private val context: Context,
        private val previews: List<ThemePreview>,
    ) : BaseAdapter() {
        override fun getCount(): Int = previews.size

        override fun getItem(position: Int): Any = previews[position].item

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup,
        ): View {
            val preview = previews[position]
            val root =
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(10), dp(16), dp(10))
                }
            root.addView(
                TextView(context).apply {
                    text = preview.item.name
                    textSize = 16f
                },
            )
            root.addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, 0)
                    addView(makeLabel(R.string.day))
                    preview.light.forEach { addView(makeSwatch(it)) }
                    // light 与 dark 之间的分隔
                    addView(
                        View(context).apply {
                            layoutParams = LinearLayout.LayoutParams(dp(12), 1)
                        },
                    )
                    addView(makeLabel(R.string.night))
                    preview.dark.forEach { addView(makeSwatch(it)) }
                },
            )
            return root
        }

        private fun makeLabel(textRes: Int): TextView =
            TextView(context).apply {
                text = context.getString(textRes)
                textSize = 11f
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, dp(4), 0)
            }

        private fun makeSwatch(color: Int): View {
            val size = dp(22)
            return View(context).apply {
                layoutParams =
                    LinearLayout.LayoutParams(size, size).apply {
                        marginEnd = dp(3)
                    }
                background =
                    GradientDrawable().apply {
                        setColor(color)
                        cornerRadius = dp(4).toFloat()
                    }
            }
        }
    }
}
