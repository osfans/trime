/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import kotlinx.serialization.Serializable

@Serializable(with = KeyActionTokenSerializer::class)
sealed interface KeyActionToken {
    @Serializable
    @JvmInline
    value class Plain(val token: String) : KeyActionToken

    @Serializable
    data class Inline(
        val commit: String? = null,
        val text: String? = null,
        val label: String? = null,
    ) : KeyActionToken
}

/**
 * A blank plain token means "no action": librime's empty scalar is how a theme
 * unsets a behavior inherited from a keyboard preset. The legacy decoder also
 * treated an empty inline mapping as absent, while preserving explicitly empty
 * values such as `commit: ''`.
 */
val KeyActionToken?.orAbsent: KeyActionToken?
    get() = when (this) {
        is KeyActionToken.Plain -> takeUnless { token.isEmpty() }
        is KeyActionToken.Inline -> takeUnless { commit == null && text == null && label == null }
        null -> null
    }
