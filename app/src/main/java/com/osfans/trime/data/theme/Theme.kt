// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.osfans.trime.data.theme.deserializer.ColorSchemeDeserializer
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.data.theme.model.TextKeyboard

typealias ColorScheme = Map<String, String>

/** 主题和样式配置  */
data class Theme(
    @field:JsonIgnore
    val id: String = "",
    val name: String = "",
    @field:JsonProperty("style")
    val generalStyle: GeneralStyle,
    val liquidKeyboard: LiquidKeyboard = LiquidKeyboard(),
    val presetKeys: Map<String, PresetKey> = emptyMap(),
    val presetKeyboards: Map<String, TextKeyboard> = emptyMap(),
    @field:JsonProperty("preset_color_schemes")
    @field:JsonDeserialize(contentUsing = ColorSchemeDeserializer::class)
    val colorSchemes: Map<String, ColorScheme> = emptyMap(),
    val fallbackColors: Map<String, String> = emptyMap(),
)
