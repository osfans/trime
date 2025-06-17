/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates.suggestion

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Size
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.common.ImageViewStyle
import androidx.autofill.inline.common.TextViewStyle
import androidx.autofill.inline.common.ViewStyle
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.core.graphics.ColorUtils
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import splitties.dimensions.dp

object InlineSuggestionHelper {
    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.R)
    fun createRequest(ctx: Context): InlineSuggestionsRequest {
        val firstTextColor = ColorManager.getColor("candidate_text_color")
        val backColor = ColorManager.getColor("hilited_back_color")
        val chipBg =
            Icon.createWithResource(ctx, R.drawable.bg_inline_suggestion).apply {
                setTint(ColorUtils.blendARGB(backColor, firstTextColor, 0.2f))
            }
        val style =
            InlineSuggestionUi
                .newStyleBuilder()
                .setSingleIconChipStyle(
                    ViewStyle
                        .Builder()
                        .setBackgroundColor(Color.TRANSPARENT)
                        .setPadding(0, 0, 0, 0)
                        .build(),
                ).setChipStyle(
                    ViewStyle
                        .Builder()
                        .setBackground(chipBg)
                        .build(),
                ).setTitleStyle(
                    TextViewStyle
                        .Builder()
                        .setLayoutMargin(ctx.dp(4), 0, ctx.dp(4), 0)
                        .setTextColor(firstTextColor)
                        .setTextSize(14f)
                        .build(),
                ).setSubtitleStyle(
                    TextViewStyle
                        .Builder()
                        .setTextColor(
                            ColorUtils.blendARGB(firstTextColor, backColor, 0.3f),
                        ).setTextSize(12f)
                        .build(),
                ).setStartIconStyle(
                    ImageViewStyle
                        .Builder()
                        .setTintList(ColorStateList.valueOf(firstTextColor))
                        .build(),
                ).setEndIconStyle(
                    ImageViewStyle
                        .Builder()
                        .setTintList(ColorStateList.valueOf(firstTextColor))
                        .build(),
                ).build()
        val styleBundle =
            UiVersions
                .newStylesBuilder()
                .addStyle(style)
                .build()
        val spec =
            InlinePresentationSpec
                .Builder(Size(0, 0), Size(ctx.dp(160), Int.MAX_VALUE))
                .setStyle(styleBundle)
                .build()
        return InlineSuggestionsRequest
            .Builder(listOf(spec))
            .setMaxSuggestionCount(InlineSuggestionsRequest.SUGGESTION_COUNT_UNLIMITED)
            .build()
    }
}
