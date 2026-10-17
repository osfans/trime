/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.core.view.children
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.broadcast.EnterKeyDisplayDelegate
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.popup.PopupDelegate

// TODO: move layout calculation responsibilities from Keyboard to KeyboardView using ConstraintLayout
@SuppressLint("ViewConstructor")
class KeyboardView(
    context: Context,
    private val theme: Theme,
    private val keyboard: Keyboard,
    val popup: PopupDelegate,
    val service: TrimeInputMethodService,
    private val keyboardActionListener: KeyboardActionListener,
    private val enterKeyDisplay: EnterKeyDisplayDelegate,
) : FrameLayout(context) {

    private val keys get() = keyboard.keys

    internal val labelEnter: String
        get() = enterKeyDisplay.keyLabel
    internal val keyTextSize = theme.style.keyTextSize
    internal val keyLongTextSize = theme.style.keyLongTextSize.takeIf { it > 0 } ?: keyTextSize
    internal val symbolTextSize = theme.style.symbolTextSize.takeIf { it > 0 } ?: keyTextSize
    internal val popupOnKeyPress by AppPrefs.defaultInstance().keyboard.popupOnKeyPress
    internal val hookShiftArrow: Boolean by AppPrefs.defaultInstance().keyboard.hookShiftArrow
    internal val hookShiftLock: Boolean by AppPrefs.defaultInstance().keyboard.hookShiftLock
    internal val hideKeySymbol: Boolean by AppPrefs.defaultInstance().keyboard.hideKeySymbol
    internal val hideKeyHint: Boolean by AppPrefs.defaultInstance().keyboard.hideKeyHint

    init {
        setWillNotDraw(false)
        buildKeyViews()
    }

    private fun buildKeyViews() {
        removeAllViews()

        keys.forEachIndexed { index, key ->
            val keyView = createKeyView(index, key)
            addView(keyView)
        }
    }

    private fun createKeyView(index: Int, key: Key): KeyView = KeyView(context, theme, key, keyboard, this, keyboardActionListener).apply {
        id = index

        val totalWidth = key.width + key.extraWidthLeft + key.extraWidthRight
        layoutParams = LayoutParams(totalWidth, key.height)

        setPadding(
            keyboard.horizontalGap / 2 + key.extraWidthLeft,
            keyboard.verticalGap / 2,
            keyboard.horizontalGap / 2 + key.extraWidthRight,
            keyboard.verticalGap / 2,
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fullWidth = keyboard.minWidth + paddingLeft + paddingRight
        val fullHeight = keyboard.height + paddingTop + paddingBottom

        val measuredWidth = minOf(
            MeasureSpec.getSize(widthMeasureSpec),
            fullWidth,
        )

        measureChildren(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(measuredWidth, fullHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // Model coordinates are absolute keyboard positions, independent of RTL.
        // Physical bounds must match the drawn keys: requestRectangleOnScreen
        // walks parent offsets without applying a child's translation.
        keys.forEachIndexed { index, key ->
            getChildAt(index)?.let { child ->
                if (child.visibility != GONE) {
                    val keyLeft = paddingLeft + key.x - key.extraWidthLeft
                    val keyTop = paddingTop + key.y
                    child.layout(keyLeft, keyTop, keyLeft + child.measuredWidth, keyTop + child.measuredHeight)
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

    fun invalidateAllKeys() {
        children.forEach { (it as? KeyView)?.refreshAppearance() ?: it.invalidate() }
    }

    fun updateEnterLabel() {
        keys.forEachIndexed { index, key ->
            if (key.click?.code == KeyEvent.KEYCODE_ENTER) {
                getChildAt(index)?.apply {
                    contentDescription = labelEnter
                    (this as? KeyView)?.refreshAppearance() ?: invalidate()
                }
            }
        }
    }

    fun invalidateKeyByIndex(index: Int) {
        getChildAt(index)?.let { (it as? KeyView)?.refreshAppearance() ?: it.invalidate() }
    }

    val isCapsOn: Boolean
        get() = keyboard.mShiftKey?.isOn == true

    /** Cancel gestures and erase per-key undo text at every editor or privacy boundary. */
    fun clearTransientContent() {
        children.filterIsInstance<GestureFrame>().forEach { it.cancelGesture() }
        popup.dismissAll()
    }

    fun onDetach() {
        clearTransientContent()
    }
}
