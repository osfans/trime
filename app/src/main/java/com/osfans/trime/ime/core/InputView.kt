/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.os.Build
import android.text.TextPaint
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ScrollView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.T9StateProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
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
import com.osfans.trime.ime.composition.T9DisambiguationView
import com.osfans.trime.ime.keyboard.CommonKeyboardActionListener
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.keyboard.T9LayoutPolicy
import com.osfans.trime.ime.performance.InputLatencyLog
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
import kotlin.math.ceil
import splitties.systemservices.windowManager as systemWindowManager

/**
 * Successor of the old InputRoot
 */
@SuppressLint("ViewConstructor")
class InputView(
    service: TrimeInputMethodService,
    rime: RimeSession,
    scope: ThemeScope,
) : BaseInputView(service, rime, scope),
    DIAware {
    private val keyboardBackground =
        imageView {
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    private val placeholderListener = OnClickListener { }

    private val leftPaddingSpace =
        view(::View) {
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnClickListener(placeholderListener)
        }

    private val rightPaddingSpace =
        view(::View) {
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnClickListener(placeholderListener)
        }

    private val bottomPaddingSpace =
        view(::View) {
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
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
    private var acceptsPreeditTouches = false
    internal val touchablePreeditView: View?
        get() = if (acceptsPreeditTouches) preedit.ui.root else null
    private val t9 = T9DisambiguationView(themedContext, scope) { revision, action, start, end, spelling ->
        service.postRimeJob { t9Action(revision, action, start, end, spelling) }
    }
    private val windowManager: BoardWindowManager by instance()
    private val inputBar: InputBarDelegate by instance()
    private val keyboardWindow: KeyboardWindow by instance()
    private val liquidWindow: LiquidWindow by instance()

    private val candidatesMode by AppPrefs.defaultInstance().candidates.mode

    private val keyboardSidePadding = theme.style.keyboardPadding
    private val keyboardSidePaddingLandscape = theme.style.keyboardPaddingLand
    private val keyboardBottomPadding = theme.style.keyboardPaddingBottom
    private val keyboardBottomPaddingLandscape = theme.style.keyboardPaddingLandBottom

    private var windowSafeLeft = 0
    private var windowSafeRight = 0
    private var windowNavBottom = 0
    private var windowSystemBottom = 0
    private var panelTouchActive = false
    private var sizeUpdatePending = false
    private val windowLocation = IntArray(2)

    private fun horizontalSafeInsets(): ImeHorizontalInsets {
        getLocationInWindow(windowLocation)
        return ImeHorizontalInsets.remaining(rootView.width, windowLocation[0], width, windowSafeLeft, windowSafeRight)
    }

    private val keyboardSidePaddingPx: Int
        get() {
            val value =
                if (context.isLandscapeMode()) keyboardSidePaddingLandscape else keyboardSidePadding
            return dp(value)
        }

    private var lastAppearanceState = Triple(false, false, false)

    private fun broadcastKeyAppearanceUpdate() {
        val prediction = rime.run { candidateSnapshotCached.isPrediction }
        val composing = rime.run { statusCached.isComposing } && !prediction
        val hasMenu = rime.run { hasMenu } && !prediction
        val paging = rime.run { paging } && !prediction
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
    internal val keyboardViewport: ScrollView

    /** Restyles colors after a scheme switch without rebuilding the view tree. */
    fun refreshColors() {
        keyboardBackground.imageDrawable = scope.drawable("keyboard_background")
        popup.refreshColors()
        keyboardWindow.refreshColors()
        inputBar.refreshColors()
        preedit.refreshColors()
        t9.refreshColors()
        windowManager.refreshColors()
    }

    init {
        // MUST call before any operation
        val receivers: List<InputBroadcastReceiver> by allInstances()
        receivers.forEach { broadcaster.addReceiver(it) }

        windowManager.cacheResidentWindow(keyboardWindow, createView = true)
        windowManager.cacheResidentWindow(liquidWindow)
        // show KeyboardWindow by default
        windowManager.attachWindow(KeyboardWindow)

        keyboardBackground.imageDrawable = scope.drawable("keyboard_background")

        keyboardView =
            constraintLayout {
                isMotionEventSplittingEnabled = true
                add(
                    keyboardBackground,
                    lParams {
                        centerInParent()
                    },
                )
                add(
                    inputBar.view,
                    lParams(matchParent, dp(inputBar.themedHeight)) {
                        topOfParent()
                        centerHorizontally()
                    },
                )
                add(
                    t9,
                    lParams(matchParent, wrapContent) {
                        below(inputBar.view)
                        centerHorizontally()
                    },
                )
                add(
                    leftPaddingSpace,
                    lParams {
                        below(t9)
                        startOfParent()
                        bottomOfParent()
                    },
                )
                add(
                    rightPaddingSpace,
                    lParams {
                        below(t9)
                        endOfParent()
                        bottomOfParent()
                    },
                )
                add(
                    t9.sidebar,
                    lParams(0, 0) {
                        below(t9)
                        above(bottomPaddingSpace)
                        startToEndOf(leftPaddingSpace)
                    },
                )
                add(
                    windowManager.view,
                    lParams {
                        below(t9)
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
            }

        // One stable parent scrolls the complete panel only when it cannot fit.
        // Keep it in place so entering fallback never reparents a held key.
        keyboardViewport =
            object : ScrollView(themedContext) {
                override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> panelTouchActive = true
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> panelTouchActive = false
                    }
                    return super.dispatchTouchEvent(event)
                }
            }.apply {
                id = R.id.keyboard_viewport
                isFillViewport = false
                isFocusable = false
                // Retain the scroll container in the accessibility tree without
                // taking keyboard focus away from the editor.
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                layoutParams = LayoutParams(matchParent, wrapContent).apply {
                    centerHorizontally()
                    bottomOfParent()
                }
                addView(keyboardView, FrameLayout.LayoutParams(matchParent, wrapContent))
            }

        updateWindowViewHeightJob =
            service.lifecycleScope.launch {
                keyboardWindow.currentKeyboardHeight.collect {
                    windowManager.view.updateLayoutParams {
                        height = it
                    }
                    updateKeyboardSize()
                }
            }

        updateKeyboardSize()
        broadcaster.addReceiver(object : InputBroadcastReceiver {
            override fun onWindowAttached(window: BoardWindow) {
                updateKeyboardSize()
            }

            override fun onKeyboardLayoutChanged() {
                // A symbol-page scroll must not clip candidates on return to T9.
                // Same-page size refreshes do not send this event.
                keyboardViewport.scrollTo(0, 0)
                // Replace or abort any old-page fling/accessibility scroll too.
                keyboardViewport.smoothScrollTo(0, 0)
            }
        })
        keyboardView.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) {
                keyboardView.post {
                    if (keyboardView.isAttachedToWindow) updateKeyboardSize()
                }
            }
        }
        addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (left != oldLeft || right != oldRight) {
                post { if (isAttachedToWindow) updateKeyboardSize() }
            }
        }
        t9.update(rime.run { t9Cached })
        preedit.ui.root.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) scheduleKeyboardSizeUpdate()
        }

        add(
            preedit.ui.root,
            lParams(wrapContent, wrapContent) {
                above(keyboardViewport)
                startOfParent()
            },
        )

        add(
            keyboardViewport,
            checkNotNull(keyboardViewport.layoutParams),
        )

        add(
            popup.root,
            lParams(matchParent, matchParent) {
                centerInParent()
            },
        )
    }

    private fun scheduleKeyboardSizeUpdate() {
        if (sizeUpdatePending) return
        sizeUpdatePending = true
        postOnAnimation {
            sizeUpdatePending = false
            if (isAttachedToWindow) updateKeyboardSize()
        }
    }

    private fun updateKeyboardSize() {
        if (panelTouchActive || keyboardWindow.hasPendingInputGesture) {
            scheduleKeyboardSizeUpdate()
            return
        }
        bottomPaddingSpace.updateLayoutParams {
            height = keyboardBottomPaddingPx
        }
        val keyboardAttached = windowManager.isAttached(keyboardWindow)
        val active = keyboardAttached && keyboardWindow.currentKeyboard.isT9Layout
        val compositionPanel = keyboardAttached && keyboardWindow.usesT9CompositionPanel
        val sidePadding = keyboardSidePaddingPx
        val safe = horizontalSafeInsets()
        val density = resources.displayMetrics.density
        val availableHeight = if (Build.VERSION.SDK_INT >= 30) {
            val metrics = context.systemWindowManager.currentWindowMetrics
            val bars = metrics.windowInsets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            metrics.bounds.height() - bars.top - maxOf(bars.bottom, windowNavBottom)
        } else {
            // screenHeightDp already excludes the legacy system-bar area.
            dp(resources.configuration.screenHeightDp) - (windowNavBottom - windowSystemBottom).coerceAtLeast(0)
        }
        val width = ((keyboardView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels) - safe.left - safe.right).coerceAtLeast(1)
        val hand = AppPrefs.defaultInstance().keyboard.t9OneHand.getValue()
        val sizes = if (compositionPanel) {
            T9LayoutPolicy.widths(
                (width / density).toInt(),
                (sidePadding / density).toInt(),
                resources.configuration.fontScale,
                hand.ordinal,
                (availableHeight / density).toInt(),
            )
        } else {
            T9LayoutPolicy.Widths((sidePadding / density).toInt(), (sidePadding / density).toInt(), 0)
        }
        val left = safe.left + if (compositionPanel) dp(sizes.left) else sidePadding
        val right = safe.right + if (compositionPanel) dp(sizes.right) else sidePadding
        val choiceWidth = dp(sizes.choices)
        val compactSidebar = compositionPanel && resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
            availableHeight / density <= 480 && choiceWidth > 0
        t9.setKeyboardLayout(active, choiceWidth > 0, compactSidebar, compositionPanel)
        var panelMaximum = 0
        if (compositionPanel) {
            val editorFont = TextPaint().apply {
                textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
            }.fontMetricsInt
            // Preserve space for the app, whose actual editor may be taller than
            // our font estimate. Short screens scroll the panel instead of
            // allowing it to occupy almost the entire available window.
            val editorReserve = T9LayoutPolicy.editorReserve(
                availableHeight,
                maxOf(dp(48), editorFont.bottom - editorFont.top + dp(16)),
            )
            val budget = T9LayoutPolicy.heightBudget(
                ((availableHeight - if (preedit.ui.visible) preedit.ui.root.height else 0) / density).toInt(),
                ceil(maxOf(inputBar.view.height, dp(inputBar.themedHeight)) / density).toInt(),
                ceil(t9.maximumTopControlsHeight / density).toInt(),
                ceil(keyboardBottomPaddingPx / density).toInt(),
                ceil(editorReserve / density).toInt(),
            )
            keyboardWindow.setT9HeightBudget(dp(budget.maximum))
            panelMaximum = dp(budget.panelMaximum)
        } else {
            keyboardWindow.setT9HeightBudget(0)
        }
        val scrollPanel = compositionPanel && keyboardWindow.usesScrollableT9Fallback
        keyboardViewport.updateLayoutParams<LayoutParams> {
            height = if (scrollPanel) panelMaximum else wrapContent
            // Navigation stays outside the scrolling content in fallback.
            bottomMargin = if (scrollPanel) windowNavBottom else 0
        }
        bottomPaddingSpace.updateLayoutParams<LayoutParams> {
            bottomMargin = if (scrollPanel) 0 else windowNavBottom
        }
        val unset = LayoutParams.UNSET
        leftPaddingSpace.updateLayoutParams { this.width = left }
        rightPaddingSpace.updateLayoutParams { this.width = right }
        val choicesOnRight = compositionPanel && hand == AppPrefs.Keyboard.OneHandMode.LEFT && choiceWidth > 0
        t9.sidebar.updateLayoutParams<LayoutParams> {
            this.width = choiceWidth
            startToEndOf(if (choicesOnRight) windowManager.view else leftPaddingSpace)
            if (choicesOnRight) endToStartOf(rightPaddingSpace) else endToStart = unset
        }
        windowManager.view.updateLayoutParams<LayoutParams> {
            startToStart = unset
            endToEnd = unset
            startToEndOf(if (choicesOnRight) leftPaddingSpace else t9.sidebar)
            endToStartOf(if (choicesOnRight) t9.sidebar else rightPaddingSpace)
        }
        preedit.ui.root.setPadding(left, 0, right, 0)
        inputBar.view.setPadding(left, 0, right, 0)
        t9.setPadding(left, 0, right, 0)
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val compat = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
        val safe = compat.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout())
        windowSafeLeft = safe.left
        windowSafeRight = safe.right
        windowSystemBottom = compat.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        windowNavBottom = getNavBarBottomInset(insets)
        updateKeyboardSize()
        return insets
    }

    fun startInput(
        info: EditorInfo,
        restarting: Boolean = false,
    ) {
        updateEnterKeyLabel(info)
        broadcaster.onStartInput(info)
        acceptsPreeditTouches = true
        preedit.ui.root.invalidate()
        if (!restarting) {
            windowManager.attachWindow(KeyboardWindow)
        }
    }

    fun updateEnterKeyLabel(info: EditorInfo) {
        enterKeyDisplay.updateLabelOnEditorInfo(info)
    }

    fun reapplyCurrentEditorMode() {
        keyboardWindow.reapplyCurrentEditorMode()
    }

    fun restoreModeForHardwareEditor(info: EditorInfo) {
        keyboardWindow.restoreModeForHardwareEditor(info)
    }

    fun resumeInputAfterHardware(info: EditorInfo) {
        keyboardWindow.syncModeFromNative()
        startInput(info, restarting = true)
    }

    fun finishInput() {
        dismissCandidateActionMenu()
        keyboardWindow.finishInput()
        popup.dismissAll()
        acceptsPreeditTouches = false
        preedit.ui.root.invalidate()
    }

    /** Drop content-bearing surfaces at an editor/privacy boundary. */
    fun clearEditorBoundaryState(privateInput: Boolean) {
        inputBar.clearEditorBoundaryState()
        keyboardWindow.clearEditorBoundaryState()
        preedit.clearEditorBoundaryState()
        if (privateInput && !windowManager.isAttached(keyboardWindow)) {
            windowManager.attachWindow(KeyboardWindow)
        }
    }

    override fun handleRimeMessage(it: RimeMessage<*>) {
        when (it) {
            is RimeMessage.SchemaMessage -> {
                t9.update(T9StateProto())
                broadcaster.onRimeSchemaUpdated(it.data)

                windowManager.attachWindow(KeyboardWindow)
            }

            is RimeMessage.OptionMessage -> {
                if (it.data.option == "ascii_mode" && it.data.value) t9.update(T9StateProto())
                broadcaster.onRimeOptionUpdated(it.data)

                if (it.data.option == "_liquid_keyboard") {
                    ContextCompat.getMainExecutor(service).execute {
                        windowManager.attachWindow(LiquidWindow)
                        liquidWindow.setDataByIndex(0)
                    }
                }
            }

            is RimeMessage.CompositionMessage -> {
                val data = if (candidatesMode == PopupCandidatesMode.ALWAYS_SHOW || t9.managesComposition) {
                    CompositionProto()
                } else {
                    it.data
                }
                broadcaster.onCompositionUpdate(data)
            }

            is RimeMessage.BulkCandidatesMessage -> {
                if (it.data.snapshot.isValid && it.data.snapshot != rime.run { candidateSnapshotCached }) {
                    InputLatencyLog.stale(it.data.snapshot)
                    return
                }
                broadcaster.onCandidateListUpdate(it.data)
            }

            is RimeMessage.T9Message -> {
                t9.update(it.data)
                if (it.data.enabled) preedit.onCompositionUpdate(CompositionProto())
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
    fun handleInlineSuggestions(
        response: InlineSuggestionsResponse,
        editorToken: Long,
    ): Boolean = inputBar.handleInlineSuggestions(response, editorToken)

    override fun onDetachedFromWindow() {
        ViewCompat.setOnApplyWindowInsetsListener(this, null)
        // cancel the notification job and clear all broadcast receivers,
        // implies that InputView should not be attached again after detached.
        updateWindowViewHeightJob.cancel()
        popup.root.removeAllViews()
        broadcaster.clear()
        super.onDetachedFromWindow()
    }
}
