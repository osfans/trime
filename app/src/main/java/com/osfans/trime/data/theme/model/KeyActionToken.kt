/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model

import com.osfans.trime.util.string
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
 * unsets a behavior inherited from a keyboard preset. An absent field already
 * decodes to null, so only the blank string case needs normalizing.
 */
val KeyActionToken?.orAbsent: KeyActionToken?
    get() = if (this is KeyActionToken.Plain && token.isEmpty()) null else this
