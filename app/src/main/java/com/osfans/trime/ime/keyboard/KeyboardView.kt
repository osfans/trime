/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.view.Gravity
import android.widget.FrameLayout
import androidx.core.view.children
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.broadcast.EnterKeyDisplayDelegate
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.ime.t9.T9CandidateView
import com.osfans.trime.ime.t9.T9InputController

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
    private val t9Controller: T9InputController? = null,
) : FrameLayout(context) {

    private val keys get() = keyboard.keys

    internal val labelEnter: String
        get() = enterKeyDisplay.keyLabel
    internal val keyTextSize = theme.generalStyle.keyTextSize
    internal val keyLongTextSize = theme.generalStyle.keyLongTextSize.takeIf { it > 0 } ?: keyTextSize
    internal val symbolTextSize = theme.generalStyle.symbolTextSize.takeIf { it > 0 } ?: keyTextSize
    internal val popupOnKeyPress by AppPrefs.defaultInstance().keyboard.popupOnKeyPress
    internal val hookShiftArrow: Boolean by AppPrefs.defaultInstance().keyboard.hookShiftArrow
    internal val hideKeySymbol: Boolean by AppPrefs.defaultInstance().keyboard.hideKeySymbol
    internal val hideKeyHint: Boolean by AppPrefs.defaultInstance().keyboard.hideKeyHint

    private var t9CandidateView: T9CandidateView? = null

    init {
        setWillNotDraw(false)
        buildKeyViews()
        if (keyboard.isT9Mode && t9Controller != null) {
            createT9CandidateView()
        }
    }

    private fun createT9CandidateView() {
        val controller = t9Controller ?: return
        createT9CandidateViewWithController(controller)
    }

    fun getT9Controller(): T9InputController? = t9Controller

    fun updateT9Controller(controller: T9InputController?) {
        t9CandidateView?.let { removeView(it) }
        t9CandidateView = null
        if (controller != null && keyboard.isT9Mode) {
            createT9CandidateViewWithController(controller)
        }
    }

    private fun createT9CandidateViewWithController(controller: T9InputController) {
        t9CandidateView = T9CandidateView(context, null, theme, keyboard).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                keyboard.t9CandidateHeight,
            )
            translationY = 0f
            translationX = 0f

            onItemSelected = { token ->
                controller.onSelectPinyin(token.pos, token.raw, token.pinYin)
            }
        }

        controller.onCandidatesChanged = { tokens ->
            t9CandidateView?.post {
                t9CandidateView?.updateItems(tokens)
            }
        }

        addView(t9CandidateView, 0)
    }

    private fun buildKeyViews() {
        removeAllViews()

        keys.forEachIndexed { index, key ->
            val keyView = createKeyView(index, key)
            addView(keyView)
        }
    }

    private fun createKeyView(index: Int, key: Key): KeyView = KeyView(context, key = key, keyboard = keyboard, keyboardView = this, keyboardActionListener = keyboardActionListener).apply {
        id = index

        val totalWidth = key.width + key.extraWidthLeft + key.extraWidthRight
        layoutParams = LayoutParams(totalWidth, key.height)

        translationX = (key.x - key.extraWidthLeft).toFloat()
        translationY = key.y.toFloat()

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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

    fun invalidateAllKeys() {
        children.forEach { it.invalidate() }
    }

    fun invalidateKeyByIndex(index: Int) {
        getChildAt(index)?.invalidate()
    }

    val isCapsOn: Boolean
        get() = keyboard.mShiftKey?.isOn == true

    fun onDetach() {
        popup.dismissAll()
    }
}
