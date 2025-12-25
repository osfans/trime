/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

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
import com.osfans.trime.R
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.util.ColorUtils
import splitties.dimensions.dp

object InlineSuggestions {
    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.R)
    fun createRequest(ctx: Context): InlineSuggestionsRequest {
        val textColor = ColorManager.getColor("candidate_text_color")
        val altTextColor = ColorManager.getColor("comment_text_color")
        val isDark = ColorUtils.isContrastedDark(ColorManager.getColor("back_color"))
        val chipDrawable = if (isDark) {
            R.drawable.bg_inline_suggestion_dark
        } else {
            R.drawable.bg_inline_suggestion_light
        }
        val chipBg = Icon.createWithResource(ctx, chipDrawable).apply {
            setTint(textColor)
        }
        val style = InlineSuggestionUi.newStyleBuilder()
            .setSingleIconChipStyle(
                ViewStyle.Builder()
                    .setBackgroundColor(Color.TRANSPARENT)
                    .setPadding(0, 0, 0, 0)
                    .build(),
            ).setChipStyle(
                ViewStyle.Builder()
                    .setBackground(chipBg)
                    .setPadding(ctx.dp(10), 0, ctx.dp(10), 0)
                    .build(),
            ).setTitleStyle(
                TextViewStyle.Builder()
                    .setLayoutMargin(ctx.dp(4), 0, ctx.dp(4), 0)
                    .setTextColor(textColor)
                    .setTextSize(14f)
                    .build(),
            ).setSubtitleStyle(
                TextViewStyle.Builder()
                    .setTextColor(altTextColor)
                    .setTextSize(12f)
                    .build(),
            ).setStartIconStyle(
                ImageViewStyle.Builder()
                    .setTintList(ColorStateList.valueOf(altTextColor))
                    .build(),
            ).setEndIconStyle(
                ImageViewStyle.Builder()
                    .setTintList(ColorStateList.valueOf(altTextColor))
                    .build(),
            ).build()
        val styleBundle = UiVersions.newStylesBuilder()
            .addStyle(style)
            .build()
        val spec = InlinePresentationSpec
            .Builder(Size(0, 0), Size(Int.MAX_VALUE, Int.MAX_VALUE))
            .setStyle(styleBundle)
            .build()
        return InlineSuggestionsRequest.Builder(listOf(spec))
            .setMaxSuggestionCount(InlineSuggestionsRequest.SUGGESTION_COUNT_UNLIMITED)
            .build()
    }
}
