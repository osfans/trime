/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import android.os.Parcelable
import com.charleskorn.kaml.YamlMap
import com.osfans.trime.data.theme.Theme.Companion.decode
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.data.theme.model.Preedit
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.data.theme.model.Window
import com.osfans.trime.ime.keyboard.KeyAction
import com.osfans.trime.util.mapping
import com.osfans.trime.util.pairs
import com.osfans.trime.util.string
import java.util.concurrent.ConcurrentHashMap
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/** 主题和样式配置  */
@Parcelize
data class Theme(
    val name: String,
    val generalStyle: GeneralStyle,
    val preedit: Preedit,
    val window: Window,
    val liquidKeyboard: LiquidKeyboard,
    val presetKeys: Map<String, PresetKey>,
    val presetKeyboards: Map<String, TextKeyboard>,
    val colorSchemes: PresetColorSchemes,
    val fallbackColors: Map<String, String>,
    val toolBar: ToolBar,
) : Parcelable {

    @IgnoredOnParcel
    val fonts by lazy { ThemeFonts(this) }

    @IgnoredOnParcel
    private val actionCache = lazy {
        // Keys are resolved from the keyboard thread and from Rime's own job
        // thread (see CommonKeyboardActionListener), so the cache is concurrent.
        ConcurrentHashMap<KeyActionToken, KeyAction>()
    }

    fun resolveAction(token: KeyActionToken) = actionCache.value.computeIfAbsent(token) {
        KeyAction(it, presetKeys)
    }

    fun resolveAction(tokenString: String) = resolveAction(KeyActionToken.Plain(tokenString))

    companion object {
        /**
         * Top-level keys a theme may declare: the sections [decode] reads plus
         * the metadata librime and the theme picker use. The theme linter
         * reports anything else, since the runtime ignores it.
         */
        internal val TOP_LEVEL_KEYS: Set<String> =
            setOf(
                "config_version",
                "name",
                "author",
                "description",
                "version",
                "style",
                "preedit",
                "window",
                "liquid_keyboard",
                "tool_bar",
                "preset_keys",
                "preset_keyboards",
                "preset_color_schemes",
                "fallback_colors",
            )

        fun decode(node: YamlMap): Theme = Theme(
            name = node.pairs["name"]?.string!!,
            generalStyle = GeneralStyle.decode(node.pairs["style"]!!),
            preedit = Preedit.decode(node.pairs["preedit"]?.mapping),
            window = Window.decode(node.pairs["window"]?.mapping),
            liquidKeyboard = LiquidKeyboard.decode(node.pairs["liquid_keyboard"]?.mapping),
            toolBar = ToolBar.decode(node.pairs["tool_bar"]?.mapping),
            presetKeys = node.pairs["preset_keys"]?.mapping?.pairs?.mapValues {
                PresetKey.decode(it.value.mapping!!)
            } ?: emptyMap(),
            presetKeyboards = node.pairs["preset_keyboards"]?.mapping?.pairs?.mapValues {
                TextKeyboard.decode(it.value.mapping!!)
            } ?: emptyMap(),
            colorSchemes = node.pairs["preset_color_schemes"]?.mapping?.pairs?.mapValues { (_, colorMap) ->
                colorMap.pairs?.mapValues { it.value.string ?: "" } ?: emptyMap()
            } ?: emptyMap(),
            fallbackColors = node.pairs["fallback_colors"]?.mapping?.pairs?.mapValues {
                it.value.string!!
            } ?: emptyMap(),
        )
    }
}
