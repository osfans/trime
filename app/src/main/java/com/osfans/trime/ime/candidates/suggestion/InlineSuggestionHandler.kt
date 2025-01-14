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
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
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
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class InlineSuggestionHandler(
    private val context: Context,
) {
    @SuppressLint("NewApi", "RestrictedApi")
    fun createRequest(): InlineSuggestionsRequest {
        val firstTextColor = ColorManager.getColor("candidate_text_color")!!
        val backColor = ColorManager.getColor("candidate_background")!!

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
                        .setBackground(
                            Icon.createWithResource(context, R.drawable.bg_inline_suggestion).apply {
                                setTint(ColorUtils.blendARGB(backColor, firstTextColor, 0.2f))
                            },
                        ).build(),
                ).setTitleStyle(
                    TextViewStyle
                        .Builder()
                        .setLayoutMargin(context.dp(4), 0, context.dp(4), 0)
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
                .Builder(Size(0, 0), Size(context.dp(160), Int.MAX_VALUE))
                .setStyle(styleBundle)
                .build()
        return InlineSuggestionsRequest
            .Builder(listOf(spec))
            .setMaxSuggestionCount(InlineSuggestionsRequest.SUGGESTION_COUNT_UNLIMITED)
            .build()
    }

    private val suggestionSize by lazy {
        Size(ViewGroup.LayoutParams.WRAP_CONTENT, context.dp(INLINE_SUGGESTION_HEIGHT))
    }

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun inflateSuggestion(response: InlineSuggestionsResponse): List<View> =
        response.inlineSuggestions.map {
            suspendCoroutine { c ->
                it.inflate(context, suggestionSize, directExecutor) { v ->
                    c.resume(v)
                }
            }
        }

    companion object {
        private const val INLINE_SUGGESTION_HEIGHT = 40

        private val directExecutor by lazy {
            Executor { it.run() }
        }
    }
}
