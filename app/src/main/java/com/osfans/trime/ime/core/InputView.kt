/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Outline
import android.graphics.Rect
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.bar.InputBarDelegate
import com.osfans.trime.ime.broadcast.EnterKeyDisplayDelegate
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.broadcast.InputBroadcaster
import com.osfans.trime.ime.candidates.compact.CompactCandidateDelegate
import com.osfans.trime.ime.candidates.popup.PopupCandidatesMode
import com.osfans.trime.ime.composition.PreeditDelegate
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.keyboard.FloatingKeyboardGeometry
import com.osfans.trime.ime.keyboard.FloatingKeyboardMode
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.ime.symbol.LiquidWindow
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.allInstances
import org.kodein.di.bindInstance
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.centerInParent
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.endToStartOf
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable
import kotlin.math.roundToInt

/**
 * Successor of the old InputRoot
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class InputView(
    service: TrimeInputMethodService,
    rime: RimeSession,
    scope: ThemeScope,
) : BaseInputView(service, rime, scope),
    DIAware,
    InputBroadcastReceiver {
    private val keyboardBackground =
        imageView {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
    private val placeholderListener = OnClickListener { }

    private val prefs = AppPrefs.defaultInstance()

    private val floatingDragPill =
        view(::View) {
            alpha = 0.4f
            // This runs during property initialization, before the shared
            // color helper can read floatingDragPill.
            setBackgroundColor(scope.colors.keyTextColor)
        }

    private val floatingCollapseButton =
        ImageView(context).apply {
            setImageResource(R.drawable.ic_baseline_expand_less_24)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(
                dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
                dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
                dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
                dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
            )
            contentDescription = context.getString(R.string.floating_keyboard_collapse)
            imageTintList = ColorStateList.valueOf(scope.colors.keyTextColor)
            isClickable = true
            isFocusable = true
            setOnClickListener { toggleFloatingCollapsed() }
        }

    private val floatingDragHandle =
        FrameLayout(context).apply {
            id = R.id.floating_keyboard_handle
            visibility = View.GONE
            contentDescription = context.getString(R.string.floating_keyboard_drag_handle)
            setOnTouchListener { _, event -> handleFloatingDrag(event) }
            // The button must stay clear of the rounded corners of the card.
            setPadding(
                dp(FLOATING_CARD_CONTENT_PADDING_DP),
                0,
                dp(FLOATING_CARD_CONTENT_PADDING_DP),
                0,
            )
            addView(
                floatingDragPill,
                FrameLayout.LayoutParams(
                    dp(FLOATING_DRAG_PILL_WIDTH_DP),
                    dp(FLOATING_DRAG_PILL_HEIGHT_DP),
                    Gravity.CENTER,
                ),
            )
            addView(
                floatingCollapseButton,
                FrameLayout.LayoutParams(
                    dp(FLOATING_HANDLE_BUTTON_SIZE_DP),
                    dp(FLOATING_HANDLE_BUTTON_SIZE_DP),
                    Gravity.START or Gravity.CENTER_VERTICAL,
                ),
            )
        }

    /** Hosts the composer text inside the floating card so it never splits the input bar. */
    private val preeditHost =
        FrameLayout(context).apply {
            id = R.id.floating_preedit_host
        }

    private val floatingResizeHandle =
        ImageView(context).apply {
            setImageResource(R.drawable.ic_baseline_open_in_full_24)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(
                dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
                dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
                dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
                dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
            )
            contentDescription = context.getString(R.string.floating_keyboard_resize)
            imageTintList = ColorStateList.valueOf(scope.colors.keyTextColor)
            alpha = 0.6f
            visibility = View.GONE
            setOnTouchListener { _, event -> handleFloatingResize(event) }
        }

    private val floatingOutlineProvider =
        object : ViewOutlineProvider() {
            override fun getOutline(
                view: View,
                outline: Outline,
            ) {
                outline.setRoundRect(
                    0,
                    0,
                    view.width,
                    view.height,
                    dp(FLOATING_CARD_CORNER_RADIUS_DP).toFloat(),
                )
            }
        }

    private val floatingKeyboardBoundsLocation = IntArray(2)

    private var floatingSystemInsets = Insets.NONE

    private var floatingActive = false

    /**
     * Set when the user docks the card in landscape. The next time the window
     * enters landscape the keyboard floats again, so this is a session
     * preference, not a persisted one.
     */
    private var landscapeDockOverride = false

    private var lastWindowLandscape: Boolean? = null

    private var floatingCollapsed = false

    private var dockedBottomInset = 0

    private val navBarBottomInset: Int
        get() = if (floatingActive) floatingSystemInsets.bottom else dockedBottomInset

    private var floatingDragging = false

    private var keyboardRefreshPosted = false

    /** Whether the posted rebuild also has to re-evaluate the landscape variant. */
    private var keyboardRefreshReevaluatesKeyboard = false

    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartTranslationX = 0f
    private var dragStartTranslationY = 0f

    private var resizeStartRawX = 0f
    private var resizeStartWidth = 0

    private val leftPaddingSpace =
        view(::View) {
            isFocusable = false
            setOnClickListener(placeholderListener)
        }

    private val rightPaddingSpace =
        view(::View) {
            isFocusable = false
            setOnClickListener(placeholderListener)
        }

    private val bottomPaddingSpace =
        view(::View) {
            isFocusable = false
            setOnClickListener(placeholderListener)
        }

    private val updateWindowViewHeightJob: Job

    override val di = DI {
        bindInstance<InputView> { this@InputView }
        bindInstance<ContextThemeWrapper> { themedContext }
        bindInstance<ThemeScope> { scope }
        bindInstance<Theme> { scope.theme }
        bindInstance<TrimeInputMethodService> { service }
        bindInstance<RimeSession> { rime }
        bindSingleton { InputBroadcaster() }
        bindSingleton { PopupDelegate(di) }
        bindSingleton { EnterKeyDisplayDelegate(di) }
        bindSingleton { PreeditDelegate(di) }
        bindSingleton { CommonKeyboardActionListener(di) }
        bindSingleton { BoardWindowManager(di) }
        bindSingleton { InputBarDelegate(di) }
        bindSingleton { CompactCandidateDelegate(di) }
        bindSingleton { KeyboardWindow(di) }
        bindSingleton { LiquidWindow(di) }
    }

    private val broadcaster: InputBroadcaster by instance()
    private val popup: PopupDelegate by instance()
    private val enterKeyDisplay: EnterKeyDisplayDelegate by instance()
    private val preedit: PreeditDelegate by instance()
    private val windowManager: BoardWindowManager by instance()
    private val inputBar: InputBarDelegate by instance()
    private val keyboardWindow: KeyboardWindow by instance()
    private val liquidWindow: LiquidWindow by instance()

    private val candidatesMode by AppPrefs.defaultInstance().candidates.mode

    private val keyboardSidePadding = theme.style.keyboardPadding
    private val keyboardSidePaddingLandscape = theme.style.keyboardPaddingLand
    private val keyboardBottomPadding = theme.style.keyboardPaddingBottom
    private val keyboardBottomPaddingLandscape = theme.style.keyboardPaddingLandBottom

    private val keyboardSidePaddingPx: Int
        get() {
            val value =
                if (context.isLandscapeMode()) keyboardSidePaddingLandscape else keyboardSidePadding
            return dp(value)
        }

    private var lastAppearanceState = Triple(false, false, false)

    private var lastKeyboardWidth = 0
    private var lastKeyboardHeight = 0

    private fun broadcastKeyAppearanceUpdate() {
        val composing = rime.run { statusCached.isComposing }
        val hasMenu = rime.run { hasMenu }
        val paging = rime.run { paging }
        val current = Triple(composing, hasMenu, paging)
        if (current != lastAppearanceState) {
            lastAppearanceState = current
            broadcaster.onKeyAppearanceUpdate(current.first, current.second, current.third)
        }
    }

    private val keyboardBottomPaddingPx: Int
        get() {
            val value =
                if (context.isLandscapeMode()) keyboardBottomPaddingLandscape else keyboardBottomPadding
            return dp(value)
        }

    val keyboardView: View

    /** Restyles colors after a scheme switch without rebuilding the view tree. */
    fun refreshColors() {
        refreshKeyboardBackground()
        setFloatingControlColors()
        popup.refreshColors()
        keyboardWindow.refreshColors()
        inputBar.refreshColors()
        preedit.refreshColors()
        windowManager.refreshColors()
    }

    /** Samples `keyboard_background` for the current keyboard size. */
    private fun refreshKeyboardBackground() {
        val width = keyboardView.width
        val height = keyboardView.height
        if (width <= 0 || height <= 0) return
        keyboardBackground.imageDrawable = scope.drawable("keyboard_background", width, height)
    }

    /** Releases the built keyboards that are not currently shown. */
    fun trimMemory() {
        keyboardWindow.trimCache()
    }

    init {
        // Landscape starts in the floating card. The measured window shape
        // takes over in onSizeChanged(), but this keeps the first layout from
        // building the docked keyboard in the common case.
        floatingActive =
            prefs.floatingKeyboard.portraitFloat.getValue() || configurationIsLandscape()
        floatingCollapsed = prefs.floatingKeyboard.collapsed.getValue()
        if (floatingActive) {
            keyboardWindow.floatingScalePercent = floatingScalePreference()
        }

        // MUST call before any operation
        val receivers: List<InputBroadcastReceiver> by allInstances()
        receivers.forEach { broadcaster.addReceiver(it) }
        // The card must expand when another window is attached, otherwise the
        // new window would stay hidden inside the collapsed card.
        broadcaster.addReceiver(this)

        keyboardWindow.isFloating = floatingActive
        windowManager.cacheResidentWindow(keyboardWindow, createView = true)
        windowManager.cacheResidentWindow(liquidWindow)
        // show KeyboardWindow by default
        windowManager.attachWindow(KeyboardWindow)

        keyboardView =
            constraintLayout {
                isMotionEventSplittingEnabled = true
                add(
                    keyboardBackground,
                    lParams(matchParent, matchParent) {
                        centerInParent()
                    },
                )
                add(
                    floatingDragHandle,
                    lParams(matchParent, dp(FloatingKeyboardGeometry.DRAG_HANDLE_HEIGHT_DP)) {
                        topOfParent()
                        centerHorizontally()
                    },
                )
                add(
                    preeditHost,
                    lParams(matchParent, wrapContent) {
                        below(floatingDragHandle)
                        centerHorizontally()
                    },
                )
                add(
                    inputBar.view,
                    lParams(matchParent, dp(inputBar.themedHeight)) {
                        below(preeditHost)
                        centerHorizontally()
                    },
                )
                add(
                    leftPaddingSpace,
                    lParams {
                        below(inputBar.view)
                        startOfParent()
                        bottomOfParent()
                    },
                )
                add(
                    rightPaddingSpace,
                    lParams {
                        below(inputBar.view)
                        endOfParent()
                        bottomOfParent()
                    },
                )
                add(
                    windowManager.view,
                    lParams {
                        below(inputBar.view)
                        above(bottomPaddingSpace)
                    },
                )
                add(
                    bottomPaddingSpace,
                    lParams {
                        startToEndOf(leftPaddingSpace)
                        endToStartOf(rightPaddingSpace)
                        bottomOfParent()
                    },
                )
                add(
                    floatingResizeHandle,
                    lParams(
                        dp(FLOATING_HANDLE_BUTTON_SIZE_DP),
                        dp(FLOATING_HANDLE_BUTTON_SIZE_DP),
                    ) {
                        // In the top control row, so it never covers a key.
                        endOfParent()
                        topOfParent()
                        marginEnd = dp(FLOATING_CARD_CONTENT_PADDING_DP)
                    },
                )
            }

        // The background can only be sampled once the keyboard has a size, which
        // rotation and height changes alter.
        keyboardView.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            if (v.width != lastKeyboardWidth || v.height != lastKeyboardHeight) {
                lastKeyboardWidth = v.width
                lastKeyboardHeight = v.height
                refreshKeyboardBackground()
            }
        }

        updateWindowViewHeightJob =
            service.lifecycleScope.launch {
                keyboardWindow.currentKeyboardHeight.collect {
                    windowManager.view.updateLayoutParams {
                        height = it
                    }
                }
            }

        add(
            preedit.ui.root,
            lParams(wrapContent, wrapContent) {
                above(keyboardView)
                startOfParent()
            },
        )

        add(
            keyboardView,
            lParams(matchParent, wrapContent) {
                centerHorizontally()
                bottomOfParent()
            },
        )

        // Must run after keyboardView has layout params; it may reset the
        // floating geometry of the card.
        updateKeyboardSize()

        add(
            popup.root,
            lParams(matchParent, matchParent) {
                centerInParent()
            },
        )

        keyboardView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (!floatingActive) return@addOnLayoutChangeListener
            // The card is anchored to the bottom, so collapsing it moves the top
            // and not the bottom. Compare every edge, otherwise the strip would
            // stay at the expanded position.
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                positionFloatingCard()
                service.requestInputViewInsetsUpdate()
            }
        }
    }

    val isFloatingKeyboardActive: Boolean
        get() = floatingActive

    fun getFloatingKeyboardBounds(out: Rect): Boolean {
        if (!floatingActive) return false
        keyboardView.getLocationInWindow(floatingKeyboardBoundsLocation)
        out.set(
            floatingKeyboardBoundsLocation[0],
            floatingKeyboardBoundsLocation[1],
            floatingKeyboardBoundsLocation[0] + keyboardView.width,
            floatingKeyboardBoundsLocation[1] + keyboardView.height,
        )
        return true
    }

    private fun setFloatingControlColors() {
        val color = ColorStateList.valueOf(scope.colors.keyTextColor)
        floatingDragPill.setBackgroundColor(scope.colors.keyTextColor)
        floatingCollapseButton.imageTintList = color
        floatingResizeHandle.imageTintList = color
    }

    /**
     * Landscape detection from the measured window. It is more accurate than
     * the configuration in split screen, where the input method window follows
     * the app window and not the device.
     */
    private fun isLandscapeWindow(): Boolean = width > 0 && height > 0 && width > height

    private fun configurationIsLandscape(): Boolean = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun floatingSafeWidth(): Int = (width - floatingSystemInsets.left - floatingSystemInsets.right).coerceAtLeast(1)

    /**
     * The card is always as wide as a portrait screen, even in landscape; the
     * user's scale is a percentage of this width.
     */
    private fun floatingBaseWidth(): Int {
        val metrics = context.resources.displayMetrics
        return minOf(metrics.widthPixels, metrics.heightPixels)
            .coerceAtMost(floatingSafeWidth())
            .coerceAtLeast(1)
    }

    private fun floatingScalePreference(): Int {
        val landscape = isLandscapeWindow() || (width <= 0 && configurationIsLandscape())
        return if (landscape) {
            prefs.floatingKeyboard.scaleLandscape.getValue()
        } else {
            prefs.floatingKeyboard.scalePortrait.getValue()
        }
    }

    private fun setFloatingScalePreference(percent: Int) {
        if (isLandscapeWindow()) {
            prefs.floatingKeyboard.scaleLandscape.setValue(percent)
        } else {
            prefs.floatingKeyboard.scalePortrait.setValue(percent)
        }
    }

    /** Coalesces rebuilds requested while a layout pass is still running. */
    private fun scheduleKeyboardRefresh(reevaluateKeyboard: Boolean = false) {
        keyboardRefreshReevaluatesKeyboard = keyboardRefreshReevaluatesKeyboard || reevaluateKeyboard
        if (keyboardRefreshPosted) return
        keyboardRefreshPosted = true
        keyboardView.post {
            keyboardRefreshPosted = false
            val reevaluate = keyboardRefreshReevaluatesKeyboard
            keyboardRefreshReevaluatesKeyboard = false
            if (reevaluate) {
                keyboardWindow.refreshKeyboardsForFloatingMode()
            } else {
                keyboardWindow.refreshKeyboards()
            }
        }
    }

    private fun clampFloatingX(
        translation: Float,
        cardWidth: Int,
        baseX: Int,
    ) = FloatingKeyboardGeometry.clampTranslation(
        translation = translation,
        containerStart = floatingSystemInsets.left,
        containerEnd = (width - floatingSystemInsets.right).coerceAtLeast(floatingSystemInsets.left),
        viewSize = cardWidth,
        basePosition = baseX,
    )

    private fun clampFloatingY(
        translation: Float,
        cardHeight: Int,
        baseY: Int,
    ) = FloatingKeyboardGeometry.clampTranslation(
        translation = translation,
        containerStart = floatingSystemInsets.top,
        containerEnd = (height - floatingSystemInsets.bottom).coerceAtLeast(floatingSystemInsets.top),
        viewSize = cardHeight,
        basePosition = baseY,
    )

    private fun floatingDefaultTranslationX(cardWidth: Int): Float {
        val safeStart = floatingSystemInsets.left
        val safeEnd = (width - floatingSystemInsets.right).coerceAtLeast(safeStart)
        val targetX =
            FloatingKeyboardGeometry.defaultPosition(
                containerStart = safeStart,
                containerEnd = safeEnd,
                viewSize = cardWidth,
                margin = dp(FLOATING_CARD_EDGE_MARGIN_DP),
                center = isLandscapeWindow(),
            )
        return (targetX - keyboardView.left).toFloat()
    }

    private fun floatingDefaultTranslationY(cardHeight: Int): Float {
        val safeStart = floatingSystemInsets.top
        val safeEnd = (height - floatingSystemInsets.bottom).coerceAtLeast(safeStart)
        val targetY =
            FloatingKeyboardGeometry.defaultPosition(
                containerStart = safeStart,
                containerEnd = safeEnd,
                viewSize = cardHeight,
                margin = dp(FLOATING_CARD_EDGE_MARGIN_DP),
                // Landscape leaves the same space above and below the card;
                // portrait rests it on the bottom, where a keyboard is
                // usually expected.
                center = isLandscapeWindow(),
            )
        return (targetY - keyboardView.top).toFloat()
    }

    private fun applyFloatingGeometry() {
        if (!floatingActive || width <= 0) return
        val baseWidth = floatingBaseWidth()
        val targetWidth =
            FloatingKeyboardGeometry.widthFor(
                containerWidth = baseWidth,
                percent = floatingScalePreference(),
                // The keys stay readable even when the card is narrow.
                minWidth = dp(FLOATING_CARD_MIN_WIDTH_DP),
            )
        val effectiveScale = FloatingKeyboardGeometry.percentFor(baseWidth, targetWidth)
        if (keyboardWindow.floatingScalePercent != effectiveScale) {
            keyboardWindow.floatingScalePercent = effectiveScale
            scheduleKeyboardRefresh()
        }
        keyboardView.updateLayoutParams<LayoutParams> {
            width = targetWidth
            // The card rests on the navigation bar; the space below the keys is
            // the bottom padding inside the card.
            bottomMargin = navBarBottomInset
        }
        floatingDragHandle.visibility = View.VISIBLE
        keyboardView.clipToOutline = true
        keyboardView.outlineProvider = floatingOutlineProvider
        keyboardView.elevation = dp(FLOATING_CARD_ELEVATION_DP).toFloat()
        positionFloatingCard()
    }

    private fun resetFloatingGeometry() {
        floatingDragging = false
        keyboardView.updateLayoutParams<LayoutParams> {
            width = LayoutParams.MATCH_PARENT
            bottomMargin = 0
        }
        keyboardView.translationX = 0f
        keyboardView.translationY = 0f
        keyboardView.elevation = 0f
        keyboardView.clipToOutline = false
        keyboardView.outlineProvider = null
        floatingDragHandle.visibility = View.GONE
        floatingDragHandle.translationY = 0f
        updatePreeditTranslation()
    }

    /** A collapsed card is a strip with the handle and the input bar only. */
    private fun applyFloatingCollapsedState() {
        val collapsed = floatingActive && floatingCollapsed
        windowManager.view.visibility = if (collapsed) View.GONE else View.VISIBLE
        floatingResizeHandle.visibility =
            if (floatingActive && !collapsed) View.VISIBLE else View.GONE
        floatingCollapseButton.setImageResource(
            if (collapsed) R.drawable.ic_baseline_expand_more_24 else R.drawable.ic_baseline_expand_less_24,
        )
        floatingCollapseButton.contentDescription =
            context.getString(
                if (collapsed) R.string.floating_keyboard_expand else R.string.floating_keyboard_collapse,
            )
    }

    private fun toggleFloatingCollapsed() {
        setFloatingCollapsed(!floatingCollapsed)
    }

    private fun setFloatingCollapsed(collapsed: Boolean) {
        if (floatingCollapsed == collapsed) return
        floatingCollapsed = collapsed
        prefs.floatingKeyboard.collapsed.setValue(collapsed)
        applyFloatingCollapsedState()
        positionFloatingCard()
        service.requestInputViewInsetsUpdate()
    }

    private fun expandFloatingKeyboard() {
        setFloatingCollapsed(false)
    }

    /**
     * Keeps the drag handle reachable when the card is taller than the space
     * available on the screen: the card may start below the top safe edge, so
     * the handle is moved down to the first visible row.
     */
    private fun updateDragHandlePosition() {
        if (!floatingActive) {
            floatingDragHandle.translationY = 0f
            return
        }
        val cardTop = keyboardView.top + keyboardView.translationY
        floatingDragHandle.translationY = (floatingSystemInsets.top - cardTop).coerceAtLeast(0f)
    }

    /**
     * The position of the card, remembered per orientation.
     *
     * The window shape is used instead of the configuration, because in split
     * screen the input method window follows the app window and not the device.
     */
    private fun savedFloatingPosition(): Pair<Int, Int> = if (isLandscapeWindow()) {
        prefs.floatingKeyboard.xLandscape.getValue() to
            prefs.floatingKeyboard.yLandscape.getValue()
    } else {
        prefs.floatingKeyboard.xPortrait.getValue() to
            prefs.floatingKeyboard.yPortrait.getValue()
    }

    private fun positionFloatingCard() {
        if (!floatingActive) return
        val cardWidth = keyboardView.width
        val cardHeight = keyboardView.height
        if (cardWidth <= 0 || cardHeight <= 0) return
        val baseX = keyboardView.left
        val baseY = keyboardView.top
        if (floatingDragging) {
            // The layout may change while the card is being dragged (for example when
            // the keyboard height is updated). Keep the finger-anchored position and
            // only clamp it, instead of restoring the previously saved position.
            keyboardView.translationX = clampFloatingX(keyboardView.translationX, cardWidth, baseX)
            keyboardView.translationY = clampFloatingY(keyboardView.translationY, cardHeight, baseY)
            updateDragHandlePosition()
            updatePreeditTranslation()
            return
        }
        val (savedX, savedY) = savedFloatingPosition()
        keyboardView.translationX =
            if (savedX == UNSET_FLOATING_POSITION) {
                floatingDefaultTranslationX(cardWidth)
            } else {
                clampFloatingX(
                    translation = (savedX - baseX).toFloat(),
                    cardWidth = cardWidth,
                    baseX = baseX,
                )
            }
        keyboardView.translationY =
            if (savedY == UNSET_FLOATING_POSITION) {
                floatingDefaultTranslationY(cardHeight)
            } else {
                clampFloatingY(
                    translation = (savedY - baseY).toFloat(),
                    cardHeight = cardHeight,
                    baseY = baseY,
                )
            }
        updateDragHandlePosition()
        updatePreeditTranslation()
    }

    /**
     * The composer text belongs above the candidate bar. Inside the floating
     * card it is hosted below the drag strip, so the drag strip never splits
     * the two; in docked mode it stays directly above the keyboard.
     */
    private fun updatePreeditParent() {
        val preeditView = preedit.ui.root
        val target: ViewGroup = if (floatingActive) preeditHost else this
        if (preeditView.parent === target) return
        (preeditView.parent as? ViewGroup)?.removeView(preeditView)
        if (target === preeditHost) {
            preeditHost.addView(
                preeditView,
                FrameLayout.LayoutParams(
                    wrapContent,
                    wrapContent,
                    Gravity.TOP or Gravity.START,
                ),
            )
        } else {
            val params =
                lParams(wrapContent, wrapContent) {
                    above(keyboardView)
                    startOfParent()
                }
            // Keep the preedit below the popup layer, which is added last.
            val popupIndex = indexOfChild(popup.root)
            addView(preeditView, if (popupIndex >= 0) popupIndex else childCount, params)
        }
    }

    /** Re-anchors the preedit touch receiver after the card or composer moves. */
    private fun updatePreeditTranslation() {
        val preeditView = preedit.ui.root
        preeditView.translationX = 0f
        preeditView.translationY = 0f
        preedit.updateTouchWindowPosition()
    }

    private fun handleFloatingDrag(event: MotionEvent): Boolean {
        if (!floatingActive) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                floatingDragging = true
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                dragStartTranslationX = keyboardView.translationX
                dragStartTranslationY = keyboardView.translationY
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                keyboardView.translationX =
                    clampFloatingX(
                        translation = dragStartTranslationX + (event.rawX - dragStartRawX),
                        cardWidth = keyboardView.width,
                        baseX = keyboardView.left,
                    )
                keyboardView.translationY =
                    clampFloatingY(
                        translation = dragStartTranslationY + (event.rawY - dragStartRawY),
                        cardHeight = keyboardView.height,
                        baseY = keyboardView.top,
                    )
                updatePreeditTranslation()
                return true
            }

            MotionEvent.ACTION_UP -> {
                floatingDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                snapFloatingCard()
                saveFloatingPosition()
                service.requestInputViewInsetsUpdate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                // The gesture was interrupted, so the card goes back to the
                // position it was saved at instead of a half finished drag.
                floatingDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                positionFloatingCard()
                return true
            }
        }
        return false
    }

    /**
     * Resizes the card by dragging the grip in its top control row.
     *
     * Only the width changes: the height follows the keyboard theme, and the
     * position is kept by the regular layout listener.
     */
    private fun handleFloatingResize(event: MotionEvent): Boolean {
        if (!floatingActive || floatingCollapsed) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resizeStartRawX = event.rawX
                resizeStartWidth = keyboardView.width
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val baseWidth = floatingBaseWidth()
                val minWidth = dp(FLOATING_CARD_MIN_WIDTH_DP).coerceAtMost(baseWidth)
                val width =
                    (resizeStartWidth + (event.rawX - resizeStartRawX))
                        .roundToInt()
                        .coerceIn(minWidth, baseWidth)
                if (width != keyboardView.layoutParams.width) {
                    keyboardWindow.floatingScalePercent =
                        FloatingKeyboardGeometry.percentFor(baseWidth, width)
                    keyboardView.updateLayoutParams { this.width = width }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                setFloatingScalePreference(
                    FloatingKeyboardGeometry.percentFor(
                        floatingBaseWidth(),
                        keyboardView.width,
                    ),
                )
                applyFloatingGeometry()
                // Wait for the new width to be laid out before remembering
                // where the card is.
                keyboardView.post {
                    saveFloatingPosition()
                    service.requestInputViewInsetsUpdate()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                applyFloatingGeometry()
                return true
            }
        }
        return false
    }

    private fun snapFloatingCard() {
        val cardWidth = keyboardView.width
        val cardHeight = keyboardView.height
        if (cardWidth <= 0 || cardHeight <= 0) return
        val threshold = dp(FLOATING_SNAP_THRESHOLD_DP)
        keyboardView.translationX =
            FloatingKeyboardGeometry.snapTranslation(
                translation = keyboardView.translationX,
                containerStart = floatingSystemInsets.left,
                containerEnd = (width - floatingSystemInsets.right).coerceAtLeast(floatingSystemInsets.left),
                viewSize = cardWidth,
                basePosition = keyboardView.left,
                threshold = threshold,
                includeCentre = true,
            )
        keyboardView.translationY =
            FloatingKeyboardGeometry.snapTranslation(
                translation = keyboardView.translationY,
                containerStart = floatingSystemInsets.top,
                containerEnd = (height - floatingSystemInsets.bottom).coerceAtLeast(floatingSystemInsets.top),
                viewSize = cardHeight,
                basePosition = keyboardView.top,
                threshold = threshold,
            )
        updatePreeditTranslation()
    }

    private fun saveFloatingPosition() {
        val x = (keyboardView.left + keyboardView.translationX).roundToInt()
        val y = (keyboardView.top + keyboardView.translationY).roundToInt()
        if (isLandscapeWindow()) {
            prefs.floatingKeyboard.xLandscape.setValue(x)
            prefs.floatingKeyboard.yLandscape.setValue(y)
        } else {
            prefs.floatingKeyboard.xPortrait.setValue(x)
            prefs.floatingKeyboard.yPortrait.setValue(y)
        }
    }

    /**
     * Whether the current window should show the floating card.
     *
     * Landscape always starts floating. The user can dock the card for the
     * current landscape session, but the next landscape entry floats again.
     * Portrait keeps the persisted preference, which defaults to the normal
     * docked keyboard.
     */
    private fun shouldUseFloatingMode(): Boolean = FloatingKeyboardMode.shouldFloat(
        landscape = isLandscapeWindow(),
        dockedForSession = landscapeDockOverride,
        portraitPreference = prefs.floatingKeyboard.portraitFloat.getValue(),
    )

    private fun applyEffectiveFloatingMode(reevaluateKeyboard: Boolean = false) {
        val shouldFloat = shouldUseFloatingMode()
        if (shouldFloat == floatingActive) {
            // The mode did not change, but a docked keyboard can still need
            // another landscape variant after an orientation change.
            if (reevaluateKeyboard) scheduleKeyboardRefresh(reevaluateKeyboard = true)
            return
        }
        floatingActive = shouldFloat
        keyboardWindow.isFloating = shouldFloat
        if (!shouldFloat) {
            // The docked keyboard always shows its keys.
            setFloatingCollapsed(false)
        }
        updateKeyboardSize()
        // The layout depends on the floating flag (a card is never split), and
        // the card width differs from the docked width. Rebuild the keyboards
        // after the new size has been laid out instead of using the old width.
        // The landscape variant also depends on the new floating mode.
        scheduleKeyboardRefresh(reevaluateKeyboard = true)
        service.requestInputViewInsetsUpdate()
    }

    /**
     * Re-applies the mode after the persisted preference changed.
     *
     * The preference is the portrait/manual mode; landscape is always
     * floating unless the user docked the card for the current session.
     */
    fun updateFloatingMode() {
        if (prefs.floatingKeyboard.portraitFloat.getValue()) {
            landscapeDockOverride = false
        }
        applyEffectiveFloatingMode(reevaluateKeyboard = true)
    }

    /**
     * Moves the keyboard into a floating window or back.
     *
     * In landscape this only overrides the automatic mode for the current
     * session, so docking does not change the portrait preference. In
     * portrait the choice is persisted.
     */
    fun setFloatingMode(floating: Boolean) {
        if (isLandscapeWindow()) {
            landscapeDockOverride = !floating
            applyEffectiveFloatingMode(reevaluateKeyboard = true)
        } else {
            prefs.floatingKeyboard.portraitFloat.setValue(floating)
            applyEffectiveFloatingMode(reevaluateKeyboard = true)
        }
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == oldw && h == oldh) return
        if (w <= 0 || h <= 0) return
        val landscape = w > h
        val previousLandscape = lastWindowLandscape
        lastWindowLandscape = landscape
        if (previousLandscape == null || previousLandscape != landscape) {
            if (previousLandscape != null) {
                // Entering an orientation starts in that orientation's default
                // mode, so a manual dock in landscape does not leak into the
                // next landscape visit.
                landscapeDockOverride = false
            }
            // On the first layout, replace the configuration guess with the
            // measured window shape. Later orientation changes also need to
            // re-resolve the keyboard layout while the card stays docked.
            applyEffectiveFloatingMode(reevaluateKeyboard = previousLandscape != null)
        }
        // The window shape is known here, so the card is placed again for the
        // new size and the position of the new orientation is restored.
        if (floatingActive) {
            applyFloatingGeometry()
        }
    }

    private fun updateBottomPaddingMargin() {
        bottomPaddingSpace.updateLayoutParams<LayoutParams> {
            // The floating card itself is lifted above the navigation bar; the docked
            // keyboard only reserves the inset inside its bottom padding.
            bottomMargin = if (floatingActive) 0 else navBarBottomInset
        }
    }

    private fun updateKeyboardSize() {
        updatePreeditParent()
        bottomPaddingSpace.updateLayoutParams {
            height = if (floatingActive) dp(FloatingKeyboardGeometry.CARD_BOTTOM_PADDING_DP) else keyboardBottomPaddingPx
        }
        updateBottomPaddingMargin()
        // The floating card has rounded corners, so its contents are kept away
        // from the edges instead of being clipped by the outline.
        val sidePadding =
            if (floatingActive) dp(FLOATING_CARD_CONTENT_PADDING_DP) else keyboardSidePaddingPx
        val unset = LayoutParams.UNSET
        if (sidePadding == 0) {
            // hide side padding space views when unnecessary
            leftPaddingSpace.visibility = View.GONE
            rightPaddingSpace.visibility = View.GONE
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToEnd = unset
                endToStart = unset
                startOfParent()
                endOfParent()
            }
        } else {
            leftPaddingSpace.visibility = View.VISIBLE
            rightPaddingSpace.visibility = View.VISIBLE
            leftPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            rightPaddingSpace.updateLayoutParams {
                width = sidePadding
            }
            windowManager.view.updateLayoutParams<LayoutParams> {
                startToStart = unset
                endToEnd = unset
                startToEndOf(leftPaddingSpace)
                endToStartOf(rightPaddingSpace)
            }
        }
        preedit.ui.root.setPadding(sidePadding, 0, sidePadding, 0)
        inputBar.view.setPadding(sidePadding, 0, sidePadding, 0)
        inputBar.setFloatingActive(floatingActive)
        applyFloatingCollapsedState()
        if (floatingActive) {
            applyFloatingGeometry()
        } else {
            resetFloatingGeometry()
        }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val compat = WindowInsetsCompat.toWindowInsetsCompat(insets)
        val types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        floatingSystemInsets = FloatingKeyboardGeometry.mergeSystemInsets(
            current = compat.getInsets(types),
            stable = compat.getInsetsIgnoringVisibility(types),
        )
        dockedBottomInset = getNavBarBottomInset(insets)
        updateBottomPaddingMargin()
        if (floatingActive) {
            applyFloatingGeometry()
        }
        return insets
    }

    fun startInput(
        info: EditorInfo,
        restarting: Boolean = false,
    ) {
        updateEnterKeyLabel(info)
        broadcaster.onStartInput(info)
        if (!restarting) {
            windowManager.attachWindow(KeyboardWindow)
        }
    }

    override fun onWindowAttached(window: BoardWindow) {
        // Another window has to be visible, so a collapsed card is expanded
        // again. The keyboard window itself does not need it, because the card
        // may be collapsed on purpose.
        if (window is KeyboardWindow) return
        expandFloatingKeyboard()
    }

    fun updateEnterKeyLabel(info: EditorInfo) {
        enterKeyDisplay.updateLabelOnEditorInfo(info)
    }

    override fun handleRimeMessage(it: RimeMessage<*>) {
        when (it) {
            is RimeMessage.SchemaMessage -> {
                broadcaster.onRimeSchemaUpdated(it.data)

                windowManager.attachWindow(KeyboardWindow)
            }

            is RimeMessage.OptionMessage -> {
                broadcaster.onRimeOptionUpdated(it.data)

                if (it.data.option == "_liquid_keyboard") {
                    ContextCompat.getMainExecutor(service).execute {
                        windowManager.attachWindow(LiquidWindow)
                        liquidWindow.setDataByIndex(0)
                    }
                }
            }

            is RimeMessage.CompositionMessage -> {
                val data = if (candidatesMode == PopupCandidatesMode.ALWAYS_SHOW) {
                    CompositionProto()
                } else {
                    it.data
                }
                broadcaster.onCompositionUpdate(data)
            }

            is RimeMessage.BulkCandidatesMessage -> {
                broadcaster.onCandidateListUpdate(it.data)
            }

            else -> {}
        }
        broadcastKeyAppearanceUpdate()
    }

    fun updateSelection(
        start: Int,
        end: Int,
    ) {
        broadcaster.onSelectionUpdate(start, end)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun handleInlineSuggestions(response: InlineSuggestionsResponse): Boolean = inputBar.handleInlineSuggestions(response)

    override fun onDetachedFromWindow() {
        ViewCompat.setOnApplyWindowInsetsListener(this, null)
        // cancel the notification job and clear all broadcast receivers,
        // implies that InputView should not be attached again after detached.
        updateWindowViewHeightJob.cancel()
        popup.root.removeAllViews()
        broadcaster.clear()
        // A drag may be interrupted by the window going away; the next layout
        // must not treat the card as still being dragged.
        floatingDragging = false
        super.onDetachedFromWindow()
    }

    companion object {
        private const val FLOATING_DRAG_PILL_WIDTH_DP = 36
        private const val FLOATING_DRAG_PILL_HEIGHT_DP = 4
        private const val FLOATING_HANDLE_BUTTON_SIZE_DP = 32
        private const val FLOATING_HANDLE_BUTTON_PADDING_DP = 4
        private const val FLOATING_CARD_CORNER_RADIUS_DP = 12

        /** Keeps the card contents clear of the rounded corners. */
        private const val FLOATING_CARD_CONTENT_PADDING_DP = 12
        private const val FLOATING_CARD_ELEVATION_DP = 8

        /** The gap between the card and the safe screen edge when it first opens. */
        private const val FLOATING_CARD_EDGE_MARGIN_DP = 12

        /** The narrowest the card may get, so the keys stay readable. */
        private const val FLOATING_CARD_MIN_WIDTH_DP = 260

        /** How close the card must be to an edge to snap to it. */
        private const val FLOATING_SNAP_THRESHOLD_DP = 24
        private const val UNSET_FLOATING_POSITION = -1
    }
}
