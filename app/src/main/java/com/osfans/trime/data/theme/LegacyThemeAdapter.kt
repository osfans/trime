/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.v2.CandidateBarStyle
import com.osfans.trime.data.theme.model.v2.ColorSchemas
import com.osfans.trime.data.theme.model.v2.EnterKeyStyle
import com.osfans.trime.data.theme.model.v2.Fonts
import com.osfans.trime.data.theme.model.v2.KeyboardStyle
import com.osfans.trime.data.theme.model.v2.PopupStyle
import com.osfans.trime.data.theme.model.v2.ThemeV2

/**
 * Converts a legacy [Theme] into its [ThemeV2] equivalent, field by field.
 *
 * This is the bridge that makes smooth migration possible: existing third-party
 * themes keep working, while the runtime consumes the unified V2 model. The
 * reused sections ([toolbar], [preedit], [window], [keys], [keyboards] and
 * [symbolKeyboard]) share their types with the legacy format and are copied
 * as-is; only the flat `style` section is split and color keys are renamed
 * snake_case → camelCase.
 */
object LegacyThemeAdapter {
    fun toV2(theme: Theme): ThemeV2 =
        ThemeV2(
            name = theme.name,
            keyboard = toKeyboardStyle(theme.style),
            candidateBar = toCandidateBarStyle(theme.style),
            popup = toPopupStyle(theme.style),
            fontFaces = toFonts(theme.style),
            enterKey = toEnterKeyStyle(theme.style),
            toolbar = theme.toolBar,
            preedit = theme.preedit,
            window = theme.window,
            colorSchemas = toColorSchemas(theme.presetColorSchemes),
            fallbackColors = theme.fallbackColors.mapKeys { (key, _) -> key.snakeToCamel() },
            keys = theme.presetKeys,
            keyboards = theme.presetKeyboards,
            symbolKeyboard = theme.liquidKeyboard,
        )

    private fun toKeyboardStyle(s: GeneralStyle) =
        KeyboardStyle(
            autoCaps = s.autoCaps ?: false,
            horizontalGap = s.horizontalGap,
            padding = s.keyboardPadding,
            paddingLeft = s.keyboardPaddingLeft,
            paddingRight = s.keyboardPaddingRight,
            paddingBottom = s.keyboardPaddingBottom,
            paddingLandscape = s.keyboardPaddingLand,
            paddingLandscapeBottom = s.keyboardPaddingLandBottom,
            keyBorder = s.keyBorder,
            keyHeight = s.keyHeight,
            keyWidth = s.keyWidth,
            textSize = s.keyTextSize,
            longTextSize = s.keyLongTextSize,
            symbolTextSize = s.symbolTextSize,
            labelTextSize = s.labelTextSize,
            textOffsetX = s.keyTextOffsetX,
            textOffsetY = s.keyTextOffsetY,
            symbolOffsetX = s.keySymbolOffsetX,
            symbolOffsetY = s.keySymbolOffsetY,
            hintOffsetX = s.keyHintOffsetX,
            hintOffsetY = s.keyHintOffsetY,
            pressOffsetX = s.keyPressOffsetX,
            pressOffsetY = s.keyPressOffsetY,
            height = s.keyboardHeight,
            heightLandscape = s.keyboardHeightLand,
            roundCorner = s.roundCorner,
            shadowRadius = s.shadowRadius,
            verticalGap = s.verticalGap,
            resetAsciiModeOnFocusChange = s.resetAsciiModeOnFocusChange,
            backgroundFolder = s.backgroundFolder,
        )

    private fun toCandidateBarStyle(s: GeneralStyle) =
        CandidateBarStyle(
            border = s.candidateBorder,
            borderRound = s.candidateBorderRound,
            cornerRadius = s.candidateCornerRadius,
            padding = s.candidatePadding,
            spacing = s.candidateSpacing,
            textSize = s.candidateTextSize,
            textVerticalBias = s.candidateTextVerticalBias,
            viewHeight = s.candidateViewHeight,
            commentHeight = s.commentHeight,
            commentPosition = s.commentPosition.toV2(),
            commentTextSize = s.commentTextSize,
            commentVerticalBias = s.commentVerticalBias,
        )

    private fun toPopupStyle(s: GeneralStyle) =
        PopupStyle(
            bottomMargin = s.popupBottomMargin,
            width = s.popupWidth,
            height = s.popupHeight,
            keyHeight = s.popupKeyHeight,
            textSize = s.popupTextSize,
        )

    private fun toFonts(s: GeneralStyle) =
        Fonts(
            candidate = s.candidateFont,
            comment = s.commentFont,
            hanb = s.hanbFont,
            key = s.keyFont,
            label = s.labelFont,
            latin = s.latinFont,
            popup = s.popupFont,
            symbol = s.symbolFont,
            text = s.textFont,
        )

    private fun toEnterKeyStyle(s: GeneralStyle) =
        EnterKeyStyle(
            mode = s.enterLabelMode,
            labels =
                EnterKeyStyle.EnterLabel(
                    go = s.enterLabels.go,
                    done = s.enterLabels.done,
                    next = s.enterLabels.next,
                    pre = s.enterLabels.pre,
                    search = s.enterLabels.search,
                    send = s.enterLabels.send,
                    default = s.enterLabels.default,
                ),
        )

    private fun GeneralStyle.CommentPosition.toV2(): CandidateBarStyle.CommentPosition = when (this) {
        GeneralStyle.CommentPosition.RIGHT -> CandidateBarStyle.CommentPosition.RIGHT
        GeneralStyle.CommentPosition.TOP -> CandidateBarStyle.CommentPosition.TOP
        GeneralStyle.CommentPosition.OVERLAY -> CandidateBarStyle.CommentPosition.OVERLAY
    }

    /** Meta keys of a legacy scheme that are not color keys. */
    private val COLOR_SCHEMA_META_KEYS = setOf("name", "author", "light_scheme", "dark_scheme")

    /**
     * Collapses the legacy multi-scheme map into the two V2 palettes. The
     * `default` scheme (or the first one) is the base; its `light_scheme` /
     * `dark_scheme` links, when present and resolvable, supply the light and
     * dark palettes, otherwise the base scheme is reused for both.
     */
    private fun toColorSchemas(preset: PresetColorSchemas): ColorSchemas {
        if (preset.isEmpty()) return ColorSchemas()
        val base = preset["default"] ?: preset.values.first()
        val lightSource = base["light_scheme"]?.let { preset[it] } ?: base
        val darkSource = base["dark_scheme"]?.let { preset[it] } ?: base
        return ColorSchemas(
            light = lightSource.toPalette(),
            dark = darkSource.toPalette(),
        )
    }

    /** Drops meta keys and renames the color keys to camelCase. */
    private fun ColorScheme.toPalette(): ColorScheme =
        filterKeys { it !in COLOR_SCHEMA_META_KEYS }.mapKeys { (key, _) -> key.snakeToCamel() }

    /** `back_color` → `backColor`, `light_scheme` → `lightScheme`, `name` → `name`. */
    private fun String.snakeToCamel(): String =
        split("_").let { parts ->
            parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
        }
}
