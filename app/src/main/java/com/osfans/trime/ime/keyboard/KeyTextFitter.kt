/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

/**
 * 纯计算的字号适配结果。
 *
 * [textSize] 是最终使用的字号，[textScaleX] 是横向压缩比例。
 * 两者配合可以保证文字在给定内框内尽量完整显示，同时避免把字缩到不可读。
 */
internal data class KeyTextFit(
    val textSize: Float,
    val textScaleX: Float,
)

/**
 * 按键文字适配计算。
 *
 * 这里不依赖 Android 的 Paint，方便在 JVM 单测里直接验证。
 * 调用方先按目标字号测量文字宽高，再根据按键内框计算最终字号和横向压缩比例。
 */
internal object KeyTextFitter {
    /** 横向最多压缩到 80%，再继续缩小字号。 */
    const val MIN_TEXT_SCALE_X = 0.8f

    fun fit(
        desiredSize: Float,
        textWidth: Float,
        textHeight: Float,
        maxWidth: Float,
        maxHeight: Float,
        minSize: Float,
    ): KeyTextFit {
        if (desiredSize <= 0f || textWidth <= 0f || textHeight <= 0f) {
            return KeyTextFit(desiredSize, 1f)
        }

        val floor = minOf(minSize, desiredSize)
        var size = desiredSize

        // 先按高度缩小字号，保证文字块不高于可用区域
        if (maxHeight > 0f && textHeight > maxHeight) {
            size = (size * maxHeight / textHeight).coerceAtLeast(floor)
        }

        // 再处理宽度：优先横向压缩，压缩不够再缩小字号
        var scaleX = 1f
        val widthAtSize = textWidth * size / desiredSize
        if (maxWidth > 0f && widthAtSize > maxWidth) {
            val requiredScaleX = maxWidth / widthAtSize
            if (requiredScaleX >= MIN_TEXT_SCALE_X) {
                scaleX = requiredScaleX
            } else {
                scaleX = MIN_TEXT_SCALE_X
                size = (size * maxWidth / (widthAtSize * MIN_TEXT_SCALE_X)).coerceAtLeast(floor)
            }
        }

        return KeyTextFit(size, scaleX)
    }
}
