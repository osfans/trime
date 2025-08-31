/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.preview

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.updateLayoutParams
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.keyboard.Key
import me.tatarka.inject.annotations.Inject
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import java.util.ArrayDeque

@InputScope
@Inject
class KeyPreviewChoreographer(
    private val context: Context,
    private val theme: Theme,
) {
    private val freeKeyPreviewUi = ArrayDeque<KeyPreviewUi>()
    private val showingKeyPreviewUi = hashMapOf<Key, KeyPreviewUi>()

    val root by lazy {
        context.frameLayout {
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            isClickable = false
            isFocusable = false
        }
    }

    fun getKeyPreviewUi(key: Key): KeyPreviewUi = showingKeyPreviewUi.remove(key)
        ?: freeKeyPreviewUi.poll()
        ?: KeyPreviewUi(context, theme).also {
            root.add(it.root, MarginLayoutParams(0, 0))
        }

    fun isShowingKeyPreview(key: Key): Boolean = showingKeyPreviewUi.containsKey(key)

    fun dismissKeyPreview(key: Key) {
        val keyPreviewUi = showingKeyPreviewUi[key] ?: return
        showingKeyPreviewUi.remove(key)
        keyPreviewUi.root.visibility = View.INVISIBLE
        freeKeyPreviewUi.add(keyPreviewUi)
    }

    fun placeAndShowKeyPreview(
        key: Key,
        keyPreviewText: String,
        keyboardViewWidth: Int,
        keyboardOrigin: IntArray,
    ) {
        val keyPreviewUi = getKeyPreviewUi(key)
        placeKeyPreview(
            key,
            keyPreviewUi,
            keyPreviewText,
            keyboardViewWidth,
            keyboardOrigin,
        )
        showKeyPreview(key, keyPreviewUi)
    }

    private fun placeKeyPreview(
        key: Key,
        keyPreviewUi: KeyPreviewUi,
        keyPreviewText: String,
        keyboardViewWidth: Int,
        originCoords: IntArray,
    ) {
        keyPreviewUi.setPreviewText(keyPreviewText)
        keyPreviewUi.root.measure(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        val previewWidth = context.dp(38)
        val previewHeight = context.dp(theme.generalStyle.previewHeight)
        val keyDrawWidth = key.width
        // The key preview is horizontally aligned with the center of the visible part of the
        // parent key. If it doesn't fit in this [KeyboardView], it is moved inward to fit and
        // the left/right background is used if such background is specified.
        val keyPreviewPosition: KeyPreviewUi.Position
        var previewX: Int = (
            key.x - (previewWidth - keyDrawWidth) / 2 +
                originCoords[0]
            )
        if (previewX < 0) {
            previewX = 0
            keyPreviewPosition = KeyPreviewUi.Position.LEFT
        } else if (previewX > keyboardViewWidth - previewWidth) {
            previewX = keyboardViewWidth - previewWidth
            keyPreviewPosition = KeyPreviewUi.Position.RIGHT
        } else {
            keyPreviewPosition = KeyPreviewUi.Position.MIDDLE
        }
        keyPreviewUi.setPreviewBackground(keyPreviewPosition)
        // The key preview is placed vertically above the top edge of the parent key with an
        // arbitrary offset.
        val previewY: Int = (
            key.y - previewHeight + theme.generalStyle.previewOffset +
                originCoords[1]
            )
        keyPreviewUi.root.updateLayoutParams<MarginLayoutParams> {
            width = previewWidth
            height = previewHeight
            setMargins(previewX, previewY, 0, 0)
        }
        keyPreviewUi.root.pivotX = previewWidth / 2.0f
        keyPreviewUi.root.pivotY = previewHeight.toFloat()
    }

    fun showKeyPreview(
        key: Key,
        keyPreviewUi: KeyPreviewUi,
    ) {
        keyPreviewUi.root.visibility = View.VISIBLE
        showingKeyPreviewUi[key] = keyPreviewUi
    }
}
