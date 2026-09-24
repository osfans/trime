/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import com.osfans.trime.util.pairs
import com.osfans.trime.util.string
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class KeyActionToken : Parcelable {
    data class Plain(val token: String) : KeyActionToken()
    data class Inline(val token: Token) : KeyActionToken() {
        @Parcelize
        data class Token(
            val commit: String?,
            val text: String?,
            val label: String?,
        ) : Parcelable
    }

    companion object {
        fun decode(node: YamlNode?): KeyActionToken? = when (node) {
            is YamlScalar -> Plain(node.content)

            is YamlMap -> Inline(
                Inline.Token(
                    commit = node.pairs["commit"]?.string,
                    text = node.pairs["text"]?.string,
                    label = node.pairs["label"]?.string,
                ),
            )

            else -> null
        }
    }
}
