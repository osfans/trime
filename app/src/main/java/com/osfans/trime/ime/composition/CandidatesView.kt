/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.annotation.SuppressLint
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.ViewTreeObserver.OnPreDrawListener
import android.view.inputmethod.CursorAnchorInfo
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.RimeProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.candidates.popup.PagedCandidatesUi
import com.osfans.trime.ime.core.BaseInputMessenger
import com.osfans.trime.ime.core.TouchEventReceiverWindow
import com.osfans.trime.ime.core.TrimeInputMethodService
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.withTheme
import splitties.views.dsl.core.wrapContent
import splitties.views.horizontalPadding
import splitties.views.verticalPadding

@SuppressLint("ViewConstructor")
class CandidatesView(
    service: TrimeInputMethodService,
    rime: RimeSession,
    theme: Theme,
) : BaseInputMessenger(service, rime, theme) {
    var useVirtualKeyboard: Boolean = true

    private val ctx = context.withTheme(android.R.style.Theme_DeviceDefault_Settings)

    private val position by AppPrefs.defaultInstance().candidates.position

    private var menu = RimeProto.Context.Menu()
    private var inputComposition = RimeProto.Context.Composition()

    private val anchorPosition = RectF()
    private val parentSize = floatArrayOf(0f, 0f)

    private var shouldUpdatePosition = false

    private val layoutListener =
        OnGlobalLayoutListener {
            shouldUpdatePosition = true
        }

    private val preDrawListener =
        OnPreDrawListener {
            if (shouldUpdatePosition) {
                updatePosition()
            }
            true
        }

    private val preeditUi =
        PreeditUi(ctx, theme).apply {
            preedit.setOnCursorMoveListener { position ->
                rime.launchOnReady { it.moveCursorPos(position) }
            }
        }

    private val candidatesUi =
        PagedCandidatesUi(ctx, theme).apply {
            setOnClickListener { type, position ->
                when (type) {
                    PagedCandidatesUi.ClickType.CANDIDATE -> {
                        rime.launchOnReady { it.selectPagedCandidate(position) }
                    }
                    PagedCandidatesUi.ClickType.PREV_PAGE -> {
                        rime.launchOnReady { it.changeCandidatePage(true) }
                    }
                    PagedCandidatesUi.ClickType.NEXT_PAGE -> {
                        rime.launchOnReady { it.changeCandidatePage(false) }
                    }
                }
            }
        }

    private val touchEventReceiverWindow = TouchEventReceiverWindow(this)

    override fun handleRimeMessage(it: RimeMessage<*>) {
        if (it is RimeMessage.ResponseMessage) {
            inputComposition = it.data.context.composition
            menu = it.data.context.menu
            updateUi()
        }
    }

    private fun evaluateVisibility(): Boolean =
        !inputComposition.preedit.isNullOrEmpty() ||
            menu.candidates.isNotEmpty()

    private fun updateUi() {
        if (evaluateVisibility()) {
            preeditUi.update(inputComposition)
            preeditUi.root.visibility = if (preeditUi.visible) View.VISIBLE else View.INVISIBLE
            // if CandidatesView can be shown, rime engine is ready most of the time,
            // so it should be safety to get option immediately
            val isHorizontalLayout = rime.run { getRuntimeOption("_horizontal") }
            candidatesUi.update(menu, isHorizontalLayout)
            visibility = View.VISIBLE
        } else {
            touchEventReceiverWindow.dismiss()
            visibility = GONE
        }
    }

    private fun updatePosition() {
        val x: Float
        val y: Float
        val (horizontal, top, _, bottom) = anchorPosition
        val (parentWidth, parentHeight) = parentSize
        if (parentWidth <= 0 || parentHeight <= 0) {
            translationX = 0f
            translationY = 0f
            return
        }
        val selfWidth = width.toFloat()
        val selfHeight = height.toFloat()
        val (_, inputViewHeight) =
            intArrayOf(0, 0)
                .also { service.inputView?.keyboardView?.getLocationInWindow(it) }

        val minX = 0f
        val minY = 0f
        val maxX = parentWidth - selfWidth
        val maxY =
            if (useVirtualKeyboard) {
                inputViewHeight - selfHeight
            } else {
                parentHeight - selfHeight
            }
        when (position) {
            PopupPosition.TOP_RIGHT -> {
                x = maxX
                y = minY
            }
            PopupPosition.TOP_LEFT -> {
                x = minX
                y = minY
            }
            PopupPosition.BOTTOM_RIGHT -> {
                x = maxX
                y = maxY
            }
            PopupPosition.BOTTOM_LEFT -> {
                x = minX
                y = maxY
            }
            PopupPosition.FOLLOW -> {
                x =
                    if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                        val rtlOffset = parentWidth - horizontal
                        if (rtlOffset + selfWidth > parentWidth) selfWidth - parentWidth else -rtlOffset
                    } else {
                        if (horizontal + selfWidth > parentWidth) parentWidth - selfWidth else horizontal
                    }
                y = if (bottom + selfHeight > parentHeight) top - selfHeight else bottom
            }
        }
        translationX = x
        translationY = y
        // update touchEventReceiverWindow's position after CandidatesView's
        if (evaluateVisibility()) {
            touchEventReceiverWindow.showup()
        }
        shouldUpdatePosition = false
    }

    private val decorLocation = floatArrayOf(0f, 0f)

    fun updateCursorAnchor(
        info: CursorAnchorInfo,
        updateDecorLocation: (FloatArray, FloatArray) -> Unit,
    ) {
        val bounds = info.getCharacterBounds(0)
        // update anchorPosition
        if (bounds == null) {
            // composing is disabled in target app or trime settings
            // use the position of the insertion marker instead
            anchorPosition.top = info.insertionMarkerTop
            anchorPosition.left = info.insertionMarkerHorizontal
            anchorPosition.bottom = info.insertionMarkerBottom
            anchorPosition.right = info.insertionMarkerHorizontal
        } else {
            // for different writing system (e.g. right to left languages),
            // we have to calculate the correct RectF
            val horizontal = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) bounds.right else bounds.left
            anchorPosition.top = bounds.top
            anchorPosition.left = horizontal
            anchorPosition.bottom = bounds.bottom
            anchorPosition.right = horizontal
        }
        updateDecorLocation(decorLocation, parentSize)
        @Suppress("KotlinConstantConditions")
        // Any component of anchorPosition can be NaN,
        // meaning it will not equal itself!
        if (anchorPosition != anchorPosition) {
            anchorPosition.set(0f, parentSize[1], 0f, parentSize[1])
            return
        }
        info.matrix.mapRect(anchorPosition)
        val (dX, dY) = decorLocation
        anchorPosition.offset(-dX, -dY)
        updatePosition()
    }

    init {
        visibility = View.GONE

        minWidth = dp(theme.generalStyle.layout.minWidth)
        minHeight = dp(theme.generalStyle.layout.minHeight)
        verticalPadding = dp(theme.generalStyle.layout.marginX)
        horizontalPadding = dp(theme.generalStyle.layout.marginY)
        background =
            ColorManager.getDrawable(
                "text_back_color",
                "border_color",
                dp(theme.generalStyle.layout.border),
                dp(theme.generalStyle.layout.roundCorner),
                theme.generalStyle.layout.alpha,
            )
        add(
            preeditUi.root,
            lParams(wrapContent, wrapContent) {
                topOfParent()
                startOfParent()
            },
        )
        add(
            candidatesUi.root,
            lParams(matchConstraints, wrapContent) {
                matchConstraintMinWidth = wrapContent
                below(preeditUi.root)
                centerHorizontally()
                bottomOfParent()
            },
        )

        isFocusable = false
        layoutParams = ViewGroup.LayoutParams(wrapContent, wrapContent)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        candidatesUi.root.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        viewTreeObserver.addOnPreDrawListener(preDrawListener)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        candidatesUi.root.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        touchEventReceiverWindow.dismiss()
        super.onDetachedFromWindow()
    }
}
