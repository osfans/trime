/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model.v2

import com.osfans.trime.data.theme.GeneralStyleAdapter
import com.osfans.trime.data.theme.ThemeFonts
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.data.theme.model.Preedit
import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.data.theme.model.TextKeyboard
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.data.theme.model.Window
import com.osfans.trime.ime.keyboard.KeyAction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * A theme in the V2 format, aligned with the Hamster / 元书 skin standard.
 *
 * Unlike the legacy [com.osfans.trime.data.theme.Theme], this model is fully
 * decoupled from the librime deployment channel: it is parsed directly from the
 * theme source YAML with semantic camelCase keys grouped into focused sections.
 *
 * The reused sections ([toolbar], [preedit], [window], [keys], [keyboards] and
 * [symbolKeyboard]) share their model types with the legacy format; only the
 * YAML key spelling differs (camelCase instead of snake_case).
 */
@Serializable
data class ThemeV2(
    val name: String = "",
    val author: String = "",
    val description: String = "",
    val keyboard: KeyboardStyle = KeyboardStyle(),
    val candidateBar: CandidateBarStyle = CandidateBarStyle(),
    val popup: PopupStyle = PopupStyle(),
    @SerialName("fonts")
    val fontFaces: Fonts = Fonts(),
    val enterKey: EnterKeyStyle = EnterKeyStyle(),
    val toolbar: ToolBar = ToolBar(),
    val preedit: Preedit = Preedit(),
    val window: Window = Window(),
    val colorSchemas: ColorSchemas = ColorSchemas(),
    val fallbackColors: Map<String, String> = emptyMap(),
    val keys: Map<String, PresetKey> = emptyMap(),
    val keyboards: Map<String, TextKeyboard> = emptyMap(),
    val symbolKeyboard: LiquidKeyboard = LiquidKeyboard(),
) {
    // === 兼容视图：渲染层迁移期的过渡接口（Phase 4 逐节迁移后删除） ===

    /** 旧 `style` 段视图，由 V2 分段反向组装。 */
    val style: GeneralStyle by lazy {
        GeneralStyleAdapter.fromV2(keyboard, candidateBar, popup, fontFaces, enterKey)
    }

    /** 字体加载器（渲染层通过 `theme.fonts.xxx` 访问，返回 [android.graphics.Typeface]）。 */
    val fonts: ThemeFonts by lazy { ThemeFonts(this) }

    /** 旧 `tool_bar` 段别名。 */
    val toolBar: ToolBar
        get() = toolbar

    /** 旧 `liquid_keyboard` 段别名。 */
    val liquidKeyboard: LiquidKeyboard
        get() = symbolKeyboard

    /** 旧 `preset_keys` 段别名。 */
    val presetKeys: Map<String, PresetKey>
        get() = keys

    /** 旧 `preset_keyboards` 段别名。 */
    val presetKeyboards: Map<String, TextKeyboard>
        get() = keyboards

    @Transient
    private val actionCache = lazy { mutableMapOf<KeyActionToken, KeyAction>() }

    /** 解析按键动作 token（旧接口，渲染层经 `theme.resolveAction(...)` 访问）。 */
    fun resolveAction(token: KeyActionToken): KeyAction = actionCache.value.getOrPut(token) { KeyAction(token, keys) }

    fun resolveAction(tokenString: String): KeyAction = resolveAction(KeyActionToken.Plain(tokenString))

    companion object {
        /** Top-level keys a V2 theme may declare. */
        val TOP_LEVEL_KEYS: Set<String> =
            setOf(
                "version",
                "name",
                "author",
                "description",
                "keyboard",
                "candidateBar",
                "popup",
                "fonts",
                "enterKey",
                "toolbar",
                "preedit",
                "window",
                "colorSchemas",
                "fallbackColors",
                "keys",
                "keyboards",
                "symbolKeyboard",
            )
    }
}
