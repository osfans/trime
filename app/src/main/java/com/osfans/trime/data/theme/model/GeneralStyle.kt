// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class GeneralStyle(
    val autoCaps: String,
    val backgroundDimAmount: Float,
    val candidateBorder: Int,
    val candidateBorderRound: Float,
    val candidateFont: List<String>,
    val candidatePadding: Int,
    val candidateSpacing: Float,
    val candidateTextSize: Float,
    val candidateUseCursor: Boolean,
    val candidateViewHeight: Int,
    val colorScheme: String,
    val commentFont: List<String>,
    val commentHeight: Int,
    val commentOnTop: Boolean,
    val commentPosition: CommentPosition,
    val commentTextSize: Float,
    val hanbFont: List<String>,
    val horizontal: Boolean,
    val horizontalGap: Int,
    val keyboardPadding: Int,
    val keyboardPaddingLeft: Int,
    val keyboardPaddingRight: Int,
    val keyboardPaddingBottom: Int,
    val keyboardPaddingLand: Int,
    val keyboardPaddingLandBottom: Int,
    val layout: Layout,
    val keyFont: List<String>,
    val keyBorder: Int,
    val keyHeight: Int,
    val keyLongTextSize: Float,
    val keyTextSize: Float,
    val keyTextOffsetX: Float,
    val keyTextOffsetY: Float,
    val keySymbolOffsetX: Float,
    val keySymbolOffsetY: Float,
    val keyHintOffsetX: Float,
    val keyHintOffsetY: Float,
    val keyPressOffsetX: Int,
    val keyPressOffsetY: Int,
    val keyWidth: Float,
    val keyboards: List<String>,
    val labelTextSize: Float,
    val labelFont: List<String>,
    val latinFont: List<String>,
    val latinLocale: String,
    val locale: String,
    val keyboardHeight: Int,
    val keyboardHeightLand: Int,
    val previewFont: List<String>,
    val previewHeight: Int,
    val previewOffset: Int,
    val previewTextSize: Float,
    val proximityCorrection: Boolean,
    val resetASCIIMode: Boolean,
    val roundCorner: Float,
    val shadowRadius: Float,
    val speechOpenccConfig: String,
    val symbolFont: List<String>,
    val symbolTextSize: Float,
    val textFont: List<String>,
    val textSize: Float,
    val verticalCorrection: Int,
    val verticalGap: Int,
    val longTextFont: List<String>,
    val backgroundFolder: String,
    val keyLongTextBorder: Int,
    val enterLabelMode: Int,
    val enterLabel: EnterLabel,
) : Parcelable {
    enum class CommentPosition {
        UNKNOWN,
        TOP,
        BOTTOM,
        RIGHT,
    }

    @Parcelize
    data class Layout(
        val border: Int,
        val maxWidth: Int,
        val maxHeight: Int,
        val minWidth: Int,
        val minHeight: Int,
        val marginX: Int,
        val marginY: Int,
        val lineSpacing: Int,
        val lineSpacingMultiplier: Float,
        val spacing: Int,
        val roundCorner: Float,
        val alpha: Int,
    ) : Parcelable

    @Parcelize
    data class EnterLabel(
        val go: String = "go",
        val done: String = "done",
        val next: String = "next",
        val pre: String = "pre",
        val search: String = "search",
        val send: String = "send",
        val default: String = "default",
    ) : Parcelable
}
