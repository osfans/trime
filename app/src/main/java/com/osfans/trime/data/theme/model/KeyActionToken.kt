/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import android.os.Parcelable
import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.string
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
        fun decode(node: Node?): KeyActionToken? = when (node) {
            is Node.Scalar -> Plain(node.string)
            is Node.Mapping -> Inline(
                Inline.Token(
                    commit = node["commit"]?.string,
                    text = node["text"]?.string,
                    label = node["label"]?.string,
                ),
            )
            else -> null
        }
    }
}
