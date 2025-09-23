/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ToolBar(
    val primaryButton: Button? = null,
    val buttons: List<Button> = emptyList(),
    val buttonSpacing: Int = 18,
    val buttonFont: List<String> = emptyList(),
) : Parcelable {

    @Parcelize
    data class Button(
        val background: Background? = null,
        val foreground: Foreground? = null,
        val action: String = "",
    ) : Parcelable {

        @Parcelize
        data class Background(
            val type: String = "rectangle",
            val cornerRadius: Float = 10f,
            val bgNormal: String = "",
            val bgHighlight: String = "",
            val verticalInset: Int = 4,
            val horizontalInset: Int = 0,
        ) : Parcelable

        @Parcelize
        data class Foreground(
            val style: String = "",
            val optionStyles: List<String> = emptyList(),
            val fgNormal: String? = null,
            val fgHighlight: String? = null,
            val fontSize: Float = 15f,
            val size: List<Int> = emptyList(),
            val padding: Int = 5,
        ) : Parcelable
    }
}
