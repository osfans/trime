// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class GeneralStyle(
    val autoCaps: Boolean = false,
    val candidateBorder: Int = 0,
    val candidateBorderRound: Float = 0f,
    val candidateFont: List<String> = emptyList(),
    val candidatePadding: Int = 0,
    val candidateSpacing: Float = 0f,
    val candidateTextSize: Float = 15f,
    val candidateUseCursor: Boolean = false,
    val candidateViewHeight: Int = 28,
    val commentFont: List<String> = emptyList(),
    val commentHeight: Int = 12,
    val commentOnTop: Boolean = false,
    val commentPosition: CommentPosition = CommentPosition.RIGHT,
    val commentTextSize: Float = 15f,
    val hanbFont: List<String> = emptyList(),
    val horizontal: Boolean = true,
    val horizontalGap: Int = 0,
    val keyboardPadding: Int = 0,
    val keyboardPaddingLeft: Int = 0,
    val keyboardPaddingRight: Int = 0,
    val keyboardPaddingBottom: Int = 0,
    val keyboardPaddingLand: Int = 0,
    val keyboardPaddingLandBottom: Int = 0,
    val layout: Layout = Layout(),
    val keyFont: List<String> = emptyList(),
    val keyBorder: Int = 0,
    val keyHeight: Int = 0,
    val keyLongTextSize: Float = 15f,
    val keyTextSize: Float = 15f,
    val keyTextOffsetX: Float = 0f,
    val keyTextOffsetY: Float = 0f,
    val keySymbolOffsetX: Float = 0f,
    val keySymbolOffsetY: Float = 0f,
    val keyHintOffsetX: Float = 0f,
    val keyHintOffsetY: Float = 0f,
    val keyPressOffsetX: Int = 0,
    val keyPressOffsetY: Int = 0,
    val keyWidth: Float = 0f,
    val labelTextSize: Float = 0f,
    val labelFont: List<String> = emptyList(),
    val latinFont: List<String> = emptyList(),
    val keyboardHeight: Int = 0,
    val keyboardHeightLand: Int = 0,
    val previewFont: List<String> = emptyList(),
    val previewHeight: Int = 0,
    val previewOffset: Int = 0,
    val previewTextSize: Float = 0f,
    val proximityCorrection: Boolean = false,
    val resetASCIIMode: Boolean = false,
    val roundCorner: Float = 0f,
    val shadowRadius: Float = 0f,
    val speechOpenccConfig: String = "tw2s.json",
    val symbolFont: List<String> = emptyList(),
    val symbolTextSize: Float = 0f,
    val textFont: List<String> = emptyList(),
    val textSize: Float = 0f,
    val verticalCorrection: Int = 0,
    val verticalGap: Int = 0,
    val longTextFont: List<String> = emptyList(),
    val backgroundFolder: String = "backgrounds",
    val keyLongTextBorder: Int = 0,
    val enterLabelMode: Int = 0,
    val enterLabel: EnterLabel = EnterLabel(),
) : Parcelable {
    enum class CommentPosition {
        UNKNOWN,
        TOP,
        BOTTOM,
        RIGHT,
    }

    @Serializable
    @Parcelize
    data class Layout(
        val border: Int = 0,
        val maxWidth: Int = 0,
        val maxHeight: Int = 0,
        val minWidth: Int = 0,
        val minHeight: Int = 0,
        val marginX: Int = 0,
        val marginY: Int = 0,
        val lineSpacing: Int = 0,
        val lineSpacingMultiplier: Float = 0f,
        val spacing: Int = 0,
        val roundCorner: Float = 0f,
        val alpha: Int = 255,
    ) : Parcelable

    @Serializable
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
