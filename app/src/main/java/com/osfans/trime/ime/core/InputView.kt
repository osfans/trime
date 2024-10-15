// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.View.OnClickListener
import android.view.WindowManager
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.core.CandidateItem
import com.osfans.trime.core.RimeCallback
import com.osfans.trime.core.RimeEvent
import com.osfans.trime.core.RimeNotification
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.candidates.CompactCandidateModule
import com.osfans.trime.ime.composition.CompositionPopupWindow
import com.osfans.trime.ime.dependency.InputComponent
import com.osfans.trime.ime.dependency.create
import com.osfans.trime.ime.keyboard.KeyboardPrefs.isLandscapeMode
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.symbol.LiquidKeyboard
import com.osfans.trime.util.ColorUtils
import com.osfans.trime.util.styledFloat
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.constraintlayout.startToEndOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.view
import splitties.views.dsl.core.withTheme
import splitties.views.dsl.core.wrapContent
import splitties.views.imageDrawable

/**
 * Successor of the old InputRoot
 */
@SuppressLint("ViewConstructor")
class InputView(
    private val service: TrimeInputMethodService,
    private val rime: RimeSession,
    private val theme: Theme,
) : ConstraintLayout(service) {
    private var shouldUpdateNavbarForeground = false
    private var shouldUpdateNavbarBackground = false
    private val navbarBackground get() = AppPrefs.defaultInstance().theme.navbarBackground

    private val keyboardBackground =
        imageView {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
    private val placeholderListener = OnClickListener { }

    private val leftPaddingSpace =
        view(::View) {
            setOnClickListener(placeholderListener)
        }

    private val rightPaddingSpace =
        view(::View) {
            setOnClickListener(placeholderListener)
        }

    private val bottomPaddingSpace =
        view(::View) {
            setOnClickListener(placeholderListener)
        }

    private val callbackHandlerJob: Job

    private val themedContext = context.withTheme(android.R.style.Theme_DeviceDefault_Settings)
    private val inputComponent = InputComponent::class.create(this, themedContext, theme, service, rime)
    private val broadcaster = inputComponent.broadcaster
    val commonKeyboardActionListener = inputComponent.commonKeyboardActionListener
    private val enterKeyLabel = inputComponent.enterKeyLabel
    private val windowManager = inputComponent.windowManager
    private val quickBar: QuickBar = inputComponent.quickBar
    private val composition: CompositionPopupWindow = inputComponent.composition
    private val keyboardWindow: KeyboardWindow = inputComponent.keyboardWindow
    private val liquidKeyboard: LiquidKeyboard = inputComponent.liquidKeyboard
    private val compactCandidate: CompactCandidateModule = inputComponent.compactCandidate

    private fun addBroadcastReceivers() {
        broadcaster.addReceiver(quickBar)
        broadcaster.addReceiver(keyboardWindow)
        broadcaster.addReceiver(liquidKeyboard)
        broadcaster.addReceiver(composition)
        broadcaster.addReceiver(compactCandidate)
    }

    private val keyboardSidePadding = theme.generalStyle.keyboardPadding
    private val keyboardSidePaddingLandscape = theme.generalStyle.keyboardPaddingLand
    private val keyboardBottomPadding = theme.generalStyle.keyboardPaddingBottom
    private val keyboardBottomPaddingLandscape = theme.generalStyle.keyboardPaddingLandBottom

    private val keyboardSidePaddingPx: Int
        get() {
            val value =
                if (context.isLandscapeMode()) keyboardSidePaddingLandscape else keyboardSidePadding
            return dp(value)
        }

    private val keyboardBottomPaddingPx: Int
        get() {
            val value =
                if (context.isLandscapeMode()) keyboardBottomPaddingLandscape else keyboardBottomPadding
            return dp(value)
        }

    val keyboardView: View

    init {
        addBroadcastReceivers()

        callbackHandlerJob =
            service.lifecycleScope.launch {
                rime.run { callbackFlow }.collect {
                    handleRimeCallback(it)
                }
            }

        windowManager.cacheResidentWindow(keyboardWindow, createView = true)
        windowManager.cacheResidentWindow(liquidKeyboard)

        service.window.window!!.also {
            when (navbarBackground) {
                AppPrefs.Theme.NavbarBackground.NONE -> {
                    WindowCompat.setDecorFitsSystemWindows(it, true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        it.isNavigationBarContrastEnforced = true
                    }
                }
                AppPrefs.Theme.NavbarBackground.COLOR_ONLY -> {
                    shouldUpdateNavbarForeground = true
                    shouldUpdateNavbarBackground = true
                    // don't draw behind navigation bar
                    WindowCompat.setDecorFitsSystemWindows(it, true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // don't apply scrim to transparent navigation bar
                        it.isNavigationBarContrastEnforced = false
                    }
                }
                AppPrefs.Theme.NavbarBackground.FULL -> {
                    shouldUpdateNavbarForeground = true
                    // allow draw behind navigation bar
                    WindowCompat.setDecorFitsSystemWindows(it, false)
                    it.navigationBarColor = Color.TRANSPARENT
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // don't apply scrim to transparent navigation bar
                        it.isNavigationBarContrastEnforced = false
                    }
                    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                        insets.getInsets(WindowInsetsCompat.Type.navigationBars()).let {
                            bottomPaddingSpace.updateLayoutParams<LayoutParams> {
                                bottomMargin = it.bottom
                            }
                        }
                        WindowInsetsCompat.CONSUMED
                    }
                }
            }
        }

        keyboardBackground.imageDrawable = ColorManager.getDrawable("keyboard_background")
            ?: ColorManager.getDrawable("keyboard_back_color")

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
                    quickBar.view,
                    lParams(matchParent, dp(quickBar.themedHeight)) {
                        topOfParent()
                        centerHorizontally()
                    },
                )
                add(
                    leftPaddingSpace,
                    lParams {
                        below(quickBar.view)
                        startOfParent()
                        bottomOfParent()
                    },
                )
                add(
                    rightPaddingSpace,
                    lParams {
                        below(quickBar.view)
                        endOfParent()
                        bottomOfParent()
                    },
                )
                add(
                    windowManager.view,
                    lParams(matchConstraints, wrapContent) {
                        below(quickBar.view)
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

        updateKeyboardSize()

        add(
            keyboardView,
            lParams(matchParent, wrapContent) {
                centerHorizontally()
                bottomOfParent()
            },
        )
    }

    private fun updateKeyboardSize() {
        bottomPaddingSpace.updateLayoutParams {
            height = keyboardBottomPaddingPx
        }
        val sidePadding = keyboardSidePaddingPx
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
        quickBar.view.setPadding(sidePadding, 0, sidePadding, 0)
    }

    fun startInput(
        info: EditorInfo,
        restarting: Boolean = false,
    ) {
        if (!restarting) {
            if (shouldUpdateNavbarForeground || shouldUpdateNavbarBackground) {
                service.window.window!!.also {
                    val backColor = ColorManager.getColor("back_color") ?: Color.BLACK
                    if (shouldUpdateNavbarForeground) {
                        WindowCompat
                            .getInsetsController(it, it.decorView)
                            .isAppearanceLightNavigationBars = ColorUtils.isContrastedDark(backColor)
                    }
                    if (shouldUpdateNavbarBackground) {
                        it.navigationBarColor = backColor
                    }
                }
            }
        }
        broadcaster.onStartInput(info)
        enterKeyLabel.updateLabelOnEditorInfo(info)
        if (!restarting) {
            windowManager.attachWindow(KeyboardWindow)
        }
    }

    private fun handleRimeCallback(it: RimeCallback) {
        when (it) {
            is RimeNotification.SchemaNotification -> {
                broadcaster.onRimeSchemaUpdated(it.value)

                windowManager.attachWindow(KeyboardWindow)
            }
            is RimeNotification.OptionNotification -> {
                broadcaster.onRimeOptionUpdated(it.value)

                if (it.value.option == "_liquid_keyboard") {
                    ContextCompat.getMainExecutor(service).execute {
                        windowManager.attachWindow(LiquidKeyboard)
                        liquidKeyboard.select(0)
                    }
                }
            }
            is RimeEvent.IpcResponseEvent ->
                it.data.let event@{
                    val ctx = it.context
                    if (ctx != null) {
                        broadcaster.onInputContextUpdate(ctx)
                        val candidates = ctx.menu.candidates.map { CandidateItem(it.comment ?: "", it.text) }
                        val isLastPage = ctx.menu.isLastPage
                        val previous = ctx.menu.run { pageSize * pageNumber }
                        val highlightedIdx = ctx.menu.highlightedCandidateIndex
                        if (composition.isPopupWindowEnabled) {
                            val sticky = composition.composition.update(ctx)
                            compactCandidate.adapter.updateCandidates(candidates, isLastPage, previous, highlightedIdx, sticky)
                        } else {
                            compactCandidate.adapter.updateCandidates(candidates, isLastPage, previous, highlightedIdx)
                        }
                        if (candidates.isEmpty()) {
                            compactCandidate.refreshUnrolled()
                        }
                    }
                }
            else -> {}
        }
    }

    fun updateCursorAnchorInfo(info: CursorAnchorInfo) {
        composition.updateCursorAnchorInfo(info)
    }

    fun updateComposing(ic: InputConnection?) {
        if (composition.isPopupWindowEnabled) {
            if (ic != null && !composition.isWinFixed()) {
                ic.requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE)
            } else {
                false
            }.also { composition.isCursorUpdated = it }
        }
    }

    fun updateSelection(
        start: Int,
        end: Int,
    ) {
        broadcaster.onSelectionUpdate(start, end)
    }

    private var showingDialog: Dialog? = null

    fun showDialog(dialog: Dialog) {
        showingDialog?.dismiss()
        val windowToken = windowToken
        check(windowToken != null) { "InputView Token is null." }
        val window = dialog.window!!
        window.attributes.apply {
            token = windowToken
            type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
        )
        window.setDimAmount(themedContext.styledFloat(android.R.attr.backgroundDimAmount))
        showingDialog =
            dialog.apply {
                setOnDismissListener { this@InputView.showingDialog = null }
                show()
            }
    }

    fun finishInput() {
        showingDialog?.dismiss()
        keyboardWindow.mainKeyboardView.finishInput()
    }

    override fun onDetachedFromWindow() {
        ViewCompat.setOnApplyWindowInsetsListener(this, null)
        showingDialog?.dismiss()
        composition.hideCompositionView()
        // cancel the notification job and clear all broadcast receivers,
        // implies that InputView should not be attached again after detached.
        callbackHandlerJob.cancel()
        broadcaster.clear()
        super.onDetachedFromWindow()
    }
}
