/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import kotlinx.serialization.Serializable

@Serializable
data class ToolBar(
    val primaryButton: Button? = null,
    val buttons: List<Button> = emptyList(),
    val buttonSpacing: Int = 18,
    val buttonFont: MaybeStringList = MaybeStringList.Empty,
    val backStyle: String = "ic@arrow-left",
) {

    @Serializable
    data class Button(
        val background: Background = Background(),
        val foreground: Foreground = Foreground(),
        val action: String = "",
        val longPressAction: String = "",
        @Serializable(with = LenientIntListSerializer::class)
        val size: List<Int> = emptyList(),
    ) {

        @Serializable
        data class Background(
            val type: Type = Type.RECTANGLE,
            val cornerRadius: Float = 10f,
            val normal: String = "",
            val highlight: String = "",
            val verticalInset: Int = 4,
            val horizontalInset: Int = 4,
        ) {
            enum class Type {
                RECTANGLE,
                CIRCLE,
            }
        }

        @Serializable
        data class Foreground(
            val style: String = "",
            @Serializable(with = LenientStringListSerializer::class)
            val optionStyles: List<String> = emptyList(),
            val normal: String = "",
            val highlight: String = "",
            val fontSize: Float = 18f,
            val padding: Int = 4,
        )
    }
}
