/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.model.v2

import kotlinx.serialization.Serializable

/**
 * Candidate bar styling, in the V2 theme format. Groups the `candidate_*` and
 * `comment_*` keys that the legacy format spread across `style`.
 */
@Serializable
data class CandidateBarStyle(
    val border: Int = 0,
    val borderRound: Float = 0f,
    val cornerRadius: Float = 5f,
    val padding: Int = 0,
    val spacing: Float = 0f,
    val textSize: Float = 15f,
    val textVerticalBias: Float = 1f,
    val viewHeight: Int = 28,
    val commentHeight: Int = 12,
    val commentPosition: CommentPosition = CommentPosition.RIGHT,
    val commentTextSize: Float = 10f,
    val commentVerticalBias: Float = 0f,
) {
    enum class CommentPosition {
        RIGHT,
        TOP,
        OVERLAY,
    }
}
