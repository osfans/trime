/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.data.theme.model.v2.CandidateBarStyle
import com.osfans.trime.data.theme.model.v2.EnterKeyStyle
import com.osfans.trime.data.theme.model.v2.Fonts
import com.osfans.trime.data.theme.model.v2.KeyboardStyle
import com.osfans.trime.data.theme.model.v2.PopupStyle

/**
 * Reassembles the legacy [GeneralStyle] view from the focused V2 sections.
 *
 * This keeps the legacy `theme.style` accessor working for the render layer
 * while it is migrated section by section to the V2 fields. The mapping is the
 * exact inverse of [LegacyThemeAdapter]'s style split, so the two compose to
 * the identity on every field.
 */
object GeneralStyleAdapter {
    fun fromV2(
        keyboard: KeyboardStyle,
        candidateBar: CandidateBarStyle,
        popup: PopupStyle,
        fonts: Fonts,
        enterKey: EnterKeyStyle,
    ): GeneralStyle =
        GeneralStyle(
            autoCaps = keyboard.autoCaps,
            candidateBorder = candidateBar.border,
            candidateBorderRound = candidateBar.borderRound,
            candidateFont = fonts.candidate,
            candidatePadding = candidateBar.padding,
            candidateSpacing = candidateBar.spacing,
            candidateTextSize = candidateBar.textSize,
            candidateTextVerticalBias = candidateBar.textVerticalBias,
            candidateViewHeight = candidateBar.viewHeight,
            candidateCornerRadius = candidateBar.cornerRadius,
            commentFont = fonts.comment,
            commentHeight = candidateBar.commentHeight,
            commentPosition = candidateBar.commentPosition.toLegacy(),
            commentTextSize = candidateBar.commentTextSize,
            commentVerticalBias = candidateBar.commentVerticalBias,
            hanbFont = fonts.hanb,
            horizontalGap = keyboard.horizontalGap,
            keyboardPadding = keyboard.padding,
            keyboardPaddingLeft = keyboard.paddingLeft,
            keyboardPaddingRight = keyboard.paddingRight,
            keyboardPaddingBottom = keyboard.paddingBottom,
            keyboardPaddingLand = keyboard.paddingLandscape,
            keyboardPaddingLandBottom = keyboard.paddingLandscapeBottom,
            keyFont = fonts.key,
            keyBorder = keyboard.keyBorder,
            keyHeight = keyboard.keyHeight,
            keyLongTextSize = keyboard.longTextSize,
            keyTextSize = keyboard.textSize,
            keyTextOffsetX = keyboard.textOffsetX,
            keyTextOffsetY = keyboard.textOffsetY,
            keySymbolOffsetX = keyboard.symbolOffsetX,
            keySymbolOffsetY = keyboard.symbolOffsetY,
            keyHintOffsetX = keyboard.hintOffsetX,
            keyHintOffsetY = keyboard.hintOffsetY,
            keyPressOffsetX = keyboard.pressOffsetX,
            keyPressOffsetY = keyboard.pressOffsetY,
            keyWidth = keyboard.keyWidth,
            labelTextSize = keyboard.labelTextSize,
            labelFont = fonts.label,
            latinFont = fonts.latin,
            keyboardHeight = keyboard.height,
            keyboardHeightLand = keyboard.heightLandscape,
            popupBottomMargin = popup.bottomMargin,
            popupWidth = popup.width,
            popupHeight = popup.height,
            popupKeyHeight = popup.keyHeight,
            popupFont = fonts.popup,
            popupTextSize = popup.textSize,
            resetAsciiModeOnFocusChange = keyboard.resetAsciiModeOnFocusChange,
            roundCorner = keyboard.roundCorner,
            shadowRadius = keyboard.shadowRadius,
            symbolFont = fonts.symbol,
            symbolTextSize = keyboard.symbolTextSize,
            textFont = fonts.text,
            verticalGap = keyboard.verticalGap,
            backgroundFolder = keyboard.backgroundFolder,
            enterLabelMode = enterKey.mode,
            enterLabels =
                GeneralStyle.EnterLabel(
                    go = enterKey.labels.go,
                    done = enterKey.labels.done,
                    next = enterKey.labels.next,
                    pre = enterKey.labels.pre,
                    search = enterKey.labels.search,
                    send = enterKey.labels.send,
                    default = enterKey.labels.default,
                ),
        )

    private fun CandidateBarStyle.CommentPosition.toLegacy(): GeneralStyle.CommentPosition = when (this) {
        CandidateBarStyle.CommentPosition.RIGHT -> GeneralStyle.CommentPosition.RIGHT
        CandidateBarStyle.CommentPosition.TOP -> GeneralStyle.CommentPosition.TOP
        CandidateBarStyle.CommentPosition.OVERLAY -> GeneralStyle.CommentPosition.OVERLAY
    }
}
