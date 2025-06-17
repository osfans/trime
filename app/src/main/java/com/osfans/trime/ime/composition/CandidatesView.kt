/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.annotation.SuppressLint
import android.graphics.RectF
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.ViewTreeObserver.OnPreDrawListener
import android.view.WindowInsets
import androidx.annotation.Size
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
import com.osfans.trime.ime.core.BaseInputView
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
import splitties.views.setPaddingDp
import splitties.views.verticalPadding
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class CandidatesView(
    service: TrimeInputMethodService,
    rime: RimeSession,
    theme: Theme,
) : BaseInputView(service, rime, theme) {
    private val ctx = context.withTheme(android.R.style.Theme_DeviceDefault_Settings)

    private val position by AppPrefs.defaultInstance().candidates.position

    private var menu = RimeProto.Context.Menu()
    private var inputComposition = RimeProto.Context.Composition()

    private val anchorPosition = RectF()
    private val parentSize = floatArrayOf(0f, 0f)

    private var shouldUpdatePosition = false

    /**
     * layout update may or may not cause [CandidatesView]'s size [onSizeChanged],
     * in either case, we should reposition it
     */
    private val layoutListener =
        OnGlobalLayoutListener {
            shouldUpdatePosition = true
        }

    /**
     * [CandidatesView]'s position is calculated based on it's size,
     * so we need to recalculate the position after layout,
     * and before any actual drawing to avoid flicker
     */
    private val preDrawListener =
        OnPreDrawListener {
            if (shouldUpdatePosition) {
                updatePosition()
            }
            true
        }

    private val preeditUi =
        PreeditUi(
            ctx,
            theme,
            setupPreeditView = { setPaddingDp(3, 1, 3, 1) },
            onMoveCursor = { pos -> rime.launchOnReady { it.moveCursorPos(pos) } },
        )

    private val candidatesUi =
        PagedCandidatesUi(
            ctx,
            theme,
            onCandidateClick = { index -> rime.launchOnReady { it.selectPagedCandidate(index) } },
            onPrevPage = { rime.launchOnReady { it.changeCandidatePage(true) } },
            onNextPage = { rime.launchOnReady { it.changeCandidatePage(false) } },
        )

    private val touchEventReceiverWindow = TouchEventReceiverWindow(this)

    private var bottomInsets = 0

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
        preeditUi.update(inputComposition)
        preeditUi.root.visibility = if (preeditUi.visible) View.VISIBLE else View.INVISIBLE
        // if CandidatesView can be shown, rime engine is ready most of the time,
        // so it should be safety to get option immediately
        val isHorizontalLayout = rime.run { getRuntimeOption("_horizontal") }
        candidatesUi.update(menu, isHorizontalLayout)
        if (evaluateVisibility()) {
            visibility = View.VISIBLE
        } else {
            // RecyclerView won't update its items when ancestor view is GONE
            visibility = View.INVISIBLE
            touchEventReceiverWindow.dismiss()
        }
    }

    private fun updatePosition() {
        if (visibility != View.VISIBLE) return
        val (parentWidth, parentHeight) = parentSize
        if (parentWidth <= 0 || parentHeight <= 0) {
            translationX = 0f
            translationY = 0f
            return
        }
        val (horizontal, top, _, bottom) = anchorPosition
        val w = width
        val h = height
        val selfWidth = w.toFloat()
        val selfHeight = h.toFloat()

        val x: Float
        val y: Float
        val minX = 0f
        val minY = 0f
        val maxX = parentWidth - selfWidth
        val maxY = (if (bottom + selfHeight > parentHeight) top else parentHeight) - selfHeight
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
                val bottomLimit = parentHeight - bottomInsets
                y = if (bottom + selfHeight > bottomLimit) top - selfHeight else bottom
            }
        }
        translationX = x
        translationY = y
        // update touchEventReceiverWindow's position after CandidatesView's
        touchEventReceiverWindow.showAt(x.roundToInt(), y.roundToInt(), w, h)
        shouldUpdatePosition = false
    }

    fun updateCursorAnchor(
        anchorPosition: RectF,
        @Size(2) parent: FloatArray,
    ) {
        this.anchorPosition.set(anchorPosition)
        val (parentWidth, parentHeight) = parent
        parentSize[0] = parentWidth
        parentSize[1] = parentHeight
        updatePosition()
    }

    init {
        visibility = View.INVISIBLE

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

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            bottomInsets = getNavBarBottomInset(insets)
        }
        return insets
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
