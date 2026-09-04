/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.composition

import android.annotation.SuppressLint
import android.graphics.RectF
import android.os.Build
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.ViewTreeObserver.OnPreDrawListener
import android.view.WindowInsets
import androidx.annotation.Size
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import com.osfans.trime.core.Candidates
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ThemeScope
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
    scope: ThemeScope,
) : BaseInputView(service, rime, scope) {
    private val ctx = context.withTheme(android.R.style.Theme_DeviceDefault_Settings)

    private val layout by AppPrefs.defaultInstance().candidates.layout
    private val position by AppPrefs.defaultInstance().candidates.position

    private var candidates = Candidates.Paged()
    private var composition = CompositionProto()

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
            scope,
            setupPreeditView = { setPaddingDp(3, 1, 3, 1) },
            onMoveCursor = { pos -> rime.launchOnReady { it.moveCursorPos(pos) } },
        )

    private val candidatesUi =
        PagedCandidatesUi(
            ctx,
            scope,
            onCandidateClick = { index -> rime.launchOnReady { it.selectCandidate(index, global = false) } },
            onCandidateAction = { index, text, view -> showCandidateActionMenu(index, text, view, global = false) },
            onPrevPage = { rime.launchOnReady { it.changeCandidatePage(true) } },
            onNextPage = { rime.launchOnReady { it.changeCandidatePage(false) } },
        )

    private val touchEventReceiverWindow = TouchEventReceiverWindow(this)

    private var bottomInsets = 0

    override fun handleRimeMessage(it: RimeMessage<*>) {
        when (it) {
            is RimeMessage.CompositionMessage -> {
                composition = it.data
                updateUi()
            }
            is RimeMessage.PagedCandidatesMessage -> {
                candidates = it.data
                updateUi()
            }
            else -> {}
        }
    }

    private fun evaluateVisibility(): Boolean = !composition.preedit.isNullOrEmpty() ||
        candidates.candidates.isNotEmpty()

    private fun updateUi() {
        preeditUi.update(composition)
        preeditUi.root.visibility = if (preeditUi.visible) VISIBLE else GONE
        // the candidate layout is queried natively with the page itself
        candidatesUi.update(candidates, layout)
        visibility = if (evaluateVisibility()) {
            VISIBLE
        } else {
            // RecyclerView won't update its items when ancestor view is GONE
            INVISIBLE
        }
    }

    /** Restyles after a scheme switch without rebuilding this view. */
    fun refreshColors() {
        background =
            scope.decorDrawable(
                colorKey = "text_back_color",
                borderColorKey = "candidate_border_color",
                borderPx = dp(theme.window.border),
                cornerRadius = dp(theme.window.cornerRadius),
            )
        preeditUi.refreshColors()
        preeditUi.update(composition)
        candidatesUi.refreshColors()
    }

    private fun updatePosition() {
        if (visibility != VISIBLE) return
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
        val spacingDp = dp(SPACING)
        val anchorTop = top - spacingDp
        val anchorBottom = bottom + spacingDp
        val bottomLimit = parentHeight - bottomInsets
        val bottomSpace = bottomLimit - anchorBottom

        val tX: Float
        val tY: Float

        val minX = spacingDp
        val minY = spacingDp
        val maxX = parentWidth - selfWidth - spacingDp
        val flipAbove = anchorBottom + selfHeight > bottomLimit && // bottom space is not enough
            anchorTop > bottomSpace // top space is larger than bottom
        val maxY = if (flipAbove) anchorTop - selfHeight else bottomLimit - selfHeight - spacingDp
        when (position) {
            PopupPosition.TOP_RIGHT -> {
                tX = maxX
                tY = minY
            }
            PopupPosition.TOP_LEFT -> {
                tX = minX
                tY = minY
            }
            PopupPosition.BOTTOM_RIGHT -> {
                tX = maxX
                tY = maxY
            }
            PopupPosition.BOTTOM_LEFT -> {
                tX = minX
                tY = maxY
            }
            PopupPosition.FOLLOW -> {
                tX =
                    if (layoutDirection == LAYOUT_DIRECTION_RTL) {
                        val rtlOffset = parentWidth - horizontal
                        if (rtlOffset + selfWidth > parentWidth - spacingDp) {
                            selfWidth - parentWidth + spacingDp
                        } else {
                            -rtlOffset
                        }
                    } else {
                        if (horizontal + selfWidth > parentWidth - spacingDp) {
                            parentWidth - selfWidth - spacingDp
                        } else {
                            horizontal
                        }
                    }
                tY = if (flipAbove) anchorTop - selfHeight else anchorBottom
            }
        }
        translationX = tX
        translationY = tY
        // update touchEventReceiverWindow's position after CandidatesView's
        touchEventReceiverWindow.showAt(tX.roundToInt(), tY.roundToInt(), w, h)
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

    /**
     * Anchor candidates view to bottom-left corner, takes navbar bottom insets into consideration.
     * Should only be used when [CursorAnchorInfo][android.view.inputmethod.CursorAnchorInfo] is invalid
     */
    fun updateCursorAnchor(@Size(2) parent: FloatArray) {
        val (parentWidth, parentHeight) = parent
        val bottom = parentHeight - bottomInsets
        anchorPosition.set(0f, bottom, 0f, bottom)
        parentSize[0] = parentWidth
        parentSize[1] = parentHeight
        updatePosition()
    }

    init {
        visibility = INVISIBLE

        minWidth = dp(theme.window.minWidth)
        verticalPadding = dp(theme.window.insets.vertical)
        horizontalPadding = dp(theme.window.insets.horizontal)
        alpha = theme.window.alpha
        background =
            scope.decorDrawable(
                colorKey = "text_back_color",
                borderColorKey = "candidate_border_color",
                borderPx = dp(theme.window.border),
                cornerRadius = dp(theme.window.cornerRadius),
            )
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        elevation = dp(theme.window.shadow)
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Reserve SPACING on both sides so that when updatePosition() docks the
        // window to a parent edge, its own spacing budget (minX/maxX = spacingDp)
        // is always achievable. Only cap when the spec constrains the width —
        // an UNSPECIFIED spec has no parent edges to reserve spacing from.
        val newWidthMeasureSpec =
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                widthMeasureSpec
            } else {
                val maxWidth = MeasureSpec.getSize(widthMeasureSpec) -
                    dp(2 * SPACING).roundToInt()
                MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST)
            }
        super.onMeasure(newWidthMeasureSpec, heightMeasureSpec)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            bottomInsets = getNavBarBottomInset(insets)
        }
        return insets
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        viewTreeObserver.addOnPreDrawListener(preDrawListener)
    }

    override fun setVisibility(visibility: Int) {
        if (visibility != VISIBLE) {
            touchEventReceiverWindow.dismiss()
        }
        super.setVisibility(visibility)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        touchEventReceiverWindow.dismiss()
        super.onDetachedFromWindow()
    }

    companion object {
        /**
         * Spacing in density-independent pixels (dp) kept between the candidate
         * window and the parent edges whenever the window docks to them.
         */
        private const val SPACING = 5f
    }
}
