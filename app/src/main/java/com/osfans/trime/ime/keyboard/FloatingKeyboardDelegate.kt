/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Outline
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.composition.PreeditDelegate
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.BoardWindowManager
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.instance
import splitties.dimensions.dp
import splitties.systemservices.windowManager
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.startOfParent
import kotlin.math.roundToInt

/**
 * Runtime state and behaviour of the floating keyboard card.
 *
 * The input view owns the card's view tree and the docked layout; this delegate
 * owns the floating-only views, geometry, gestures and persisted state.
 */
@SuppressLint("ClickableViewAccessibility")
class FloatingKeyboardDelegate(override val di: DI) :
    DIAware,
    InputBroadcastReceiver {
    private val service: TrimeInputMethodService by instance()
    private val context: ContextThemeWrapper by instance()
    private val scope: ThemeScope by instance()
    private val preedit: PreeditDelegate by instance()
    private val popup: PopupDelegate by instance()
    private val keyboardWindow: KeyboardWindow by instance()
    private val windowManager: BoardWindowManager by instance()

    private val prefs = AppPrefs.defaultInstance()
    private val hideInputBar by prefs.keyboard.hideInputBar

    /** Set by [attach] once the input view's tree exists. */
    private var host: ViewGroup? = null
    private var keyboardView: View? = null
    private var onKeyboardSizeChanged: (() -> Unit)? = null

    private val floatingDragPill =
        View(context).apply {
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
                context.dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
                context.dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
                context.dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
                context.dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
            )
            contentDescription = context.getString(R.string.floating_keyboard_collapse)
            imageTintList = ColorStateList.valueOf(scope.colors.keyTextColor)
            isClickable = true
            isFocusable = true
            setOnClickListener { toggleFloatingCollapsed() }
        }

    /**
     * Docking control for users who hide the input bar. The bar toggle is not
     * visible then, so the floating card keeps its own control.
     */
    private val floatingDockButton =
        ImageView(context).apply {
            setImageResource(R.drawable.ic_baseline_dock_to_bottom_24)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(
                context.dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
                context.dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
                context.dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
                context.dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
            )
            contentDescription = context.getString(R.string.floating_keyboard_dock)
            imageTintList = ColorStateList.valueOf(scope.colors.keyTextColor)
            isClickable = true
            isFocusable = true
            visibility = if (hideInputBar) View.VISIBLE else View.GONE
            setOnClickListener { service.requestFloatingMode(false) }
        }

    private val floatingDragHandle =
        FrameLayout(context).apply {
            id = R.id.floating_keyboard_handle
            visibility = View.GONE
            contentDescription = context.getString(R.string.floating_keyboard_drag_handle)
            setOnTouchListener { _, event -> handleFloatingDrag(event) }
            // The button must stay clear of the rounded corners of the card.
            setPadding(
                context.dp(FLOATING_CARD_CONTENT_PADDING_DP),
                0,
                context.dp(FLOATING_CARD_CONTENT_PADDING_DP),
                0,
            )
            addView(
                floatingDragPill,
                FrameLayout.LayoutParams(
                    context.dp(FLOATING_DRAG_PILL_WIDTH_DP),
                    context.dp(FLOATING_DRAG_PILL_HEIGHT_DP),
                    Gravity.CENTER,
                ),
            )
            addView(
                floatingCollapseButton,
                FrameLayout.LayoutParams(
                    context.dp(FLOATING_HANDLE_BUTTON_SIZE_DP),
                    context.dp(FLOATING_HANDLE_BUTTON_SIZE_DP),
                    Gravity.START or Gravity.CENTER_VERTICAL,
                ),
            )
            addView(
                floatingDockButton,
                FrameLayout.LayoutParams(
                    context.dp(FLOATING_HANDLE_BUTTON_SIZE_DP),
                    context.dp(FLOATING_HANDLE_BUTTON_SIZE_DP),
                    Gravity.START or Gravity.CENTER_VERTICAL,
                ).apply {
                    marginStart = context.dp(FLOATING_HANDLE_BUTTON_SIZE_DP)
                },
            )
        }

    /** Hosts the composer text inside the floating card so it never splits the input bar. */
    val preeditHost =
        FrameLayout(context).apply {
            id = R.id.floating_preedit_host
        }

    private val floatingResizeHandle =
        ImageView(context).apply {
            setImageResource(R.drawable.ic_baseline_open_in_full_24)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(
                context.dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
                context.dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
                context.dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
                context.dp(FLOATING_HANDLE_BUTTON_PADDING_DP),
            )
            contentDescription = context.getString(R.string.floating_keyboard_resize)
            imageTintList = ColorStateList.valueOf(scope.colors.keyTextColor)
            alpha = 0.6f
            visibility = View.GONE
            setOnTouchListener { _, event -> handleFloatingResize(event) }
        }

    /** Shown when the resize gesture is stopped by the card's height limit. */
    val heightLimitHint =
        TextView(context).apply {
            setText(R.string.floating_keyboard_height_limit)
            setTextColor(scope.colors.keyTextColor)
            textSize = FLOATING_HINT_TEXT_SIZE_SP
            gravity = Gravity.CENTER
            maxLines = 2
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(
                context.dp(FLOATING_HINT_PADDING_HORIZONTAL_DP),
                context.dp(FLOATING_HINT_PADDING_VERTICAL_DP),
                context.dp(FLOATING_HINT_PADDING_HORIZONTAL_DP),
                context.dp(FLOATING_HINT_PADDING_VERTICAL_DP),
            )
            background =
                GradientDrawable().apply {
                    setColor(scope.colors.backColor)
                    cornerRadius = context.dp(FLOATING_HINT_CORNER_RADIUS_DP).toFloat()
                }
            visibility = View.GONE
            isClickable = false
            isFocusable = false
        }

    private var floatingCornerRadiusPx = context.dp(FLOATING_CARD_CORNER_RADIUS_DP)

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
                    floatingCornerRadiusPx.toFloat(),
                )
            }
        }

    val dragHandle: View get() = floatingDragHandle
    val resizeHandle: View get() = floatingResizeHandle

    /** The padding that keeps the card's controls clear of the rounded corners. */
    val cardContentPadding: Int get() = scaledDp(FLOATING_CARD_CONTENT_PADDING_DP)

    /** The size of the collapse, dock and resize controls. */
    val resizeHandleSize: Int get() = scaledDp(FLOATING_HANDLE_BUTTON_SIZE_DP)

    private val currentFloatingScale: Int
        get() = if (floatingActive) keyboardWindow.floatingScalePercent else 100

    private val floatingKeyboardBoundsLocation = IntArray(2)
    private val floatingPopupBounds = Rect()

    private var floatingSystemInsets = Insets.NONE
    private var dockedBottomInset = 0
    private var baseFixedCardHeightPx = 0

    private var onFloatingScaleChanged: ((Int) -> Unit)? = null
    private var lastNotifiedScale = -1

    private var floatingActive = false

    /** A manual dock in landscape lasts until the view is hidden or rotated. */
    private var landscapeDockOverride = false
    private var pendingModeReevaluation = false

    private var lastWindowLandscape: Boolean? = null

    private var floatingCollapsed = false

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
    private var resizeLastWidth = 0
    private var resizeChanged = false

    val isActive: Boolean get() = floatingActive

    val systemInsets: Insets get() = floatingSystemInsets

    /**
     * Resolves the initial mode before the keyboard window creates its view.
     * Must be called by the input view before caching the keyboard window.
     */
    fun initialize() {
        // Landscape starts in the floating card. The measured window shape
        // takes over in onSizeChanged(), but this keeps the first layout from
        // building the docked keyboard in the common case.
        floatingActive =
            prefs.floatingKeyboard.portraitFloat.getValue() || configurationIsLandscape()
        floatingCollapsed = prefs.floatingKeyboard.collapsed.getValue()
        keyboardWindow.isFloating = floatingActive
        if (floatingActive) {
            keyboardWindow.floatingScalePercent = floatingScalePreference()
        }
    }

    /**
     * Binds the delegate to the input view's tree.
     *
     * [onKeyboardSizeChanged] lets the delegate ask the input view to re-apply
     * its docked layout after the floating mode changed.
     */
    fun attach(
        host: ViewGroup,
        keyboardView: View,
        onKeyboardSizeChanged: () -> Unit,
    ) {
        this.host = host
        this.keyboardView = keyboardView
        this.onKeyboardSizeChanged = onKeyboardSizeChanged
        keyboardView.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (!floatingActive) return@addOnLayoutChangeListener
            // The card is anchored to the bottom, so collapsing it moves the top
            // and not the bottom. Compare every edge, otherwise the strip would
            // stay at the expanded position.
            if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                positionFloatingCard()
                service.requestInputViewInsetsUpdate()
                if (!floatingCollapsed && !floatingDragging && !resizeChanged) {
                    keyboardView.post { if (host != null) clampFloatingScaleToScreen() }
                }
            }
        }
    }

    /** Restyles colors after a scheme switch without rebuilding the view tree. */
    fun refreshColors() {
        val color = ColorStateList.valueOf(scope.colors.keyTextColor)
        floatingDragPill.setBackgroundColor(scope.colors.keyTextColor)
        floatingCollapseButton.imageTintList = color
        floatingDockButton.imageTintList = color
        floatingResizeHandle.imageTintList = color
        heightLimitHint.setTextColor(scope.colors.keyTextColor)
        (heightLimitHint.background as? GradientDrawable)?.setColor(scope.colors.backColor)
    }

    fun getBounds(out: Rect): Boolean {
        val keyboardView = keyboardView ?: return false
        if (!floatingActive || keyboardView.width <= 0 || keyboardView.height <= 0) return false
        keyboardView.getLocationInWindow(floatingKeyboardBoundsLocation)
        out.set(
            floatingKeyboardBoundsLocation[0],
            floatingKeyboardBoundsLocation[1],
            floatingKeyboardBoundsLocation[0] + keyboardView.width,
            floatingKeyboardBoundsLocation[1] + keyboardView.height,
        )
        // The popup layer is full-screen and can extend past the card. Those
        // parts still belong to the IME window and must stay touchable.
        if (popup.getVisibleBounds(floatingPopupBounds)) {
            out.union(floatingPopupBounds)
        }
        return true
    }

    /** The card's height at 100%, excluding the key area. */
    fun setBaseFixedCardHeight(height: Int) {
        baseFixedCardHeightPx = height.coerceAtLeast(0)
    }

    /** Lets the input view resize the card's chrome with the same scale. */
    fun setOnFloatingScaleChanged(listener: ((Int) -> Unit)?) {
        onFloatingScaleChanged = listener
    }

    /** The drag strip's height at the current scale. */
    val dragHandleHeight: Int get() = scaledDp(FloatingKeyboardGeometry.DRAG_HANDLE_HEIGHT_DP)

    /** Scales a base pixel dimension by the current floating scale. */
    fun scaledPx(base: Int): Int = FloatingKeyboardGeometry.scaledDimension(base, currentFloatingScale)

    /** Scales the drag strip, its controls, the resize grip and the hint. */
    fun applyChromeScale() {
        val scale = currentFloatingScale
        val contentPadding = cardContentPadding
        floatingCornerRadiusPx = scaledPx(context.dp(FLOATING_CARD_CORNER_RADIUS_DP))
        floatingDragHandle.updateLayoutParams { height = dragHandleHeight }
        floatingDragHandle.setPadding(contentPadding, 0, contentPadding, 0)
        floatingDragPill.updateLayoutParams<FrameLayout.LayoutParams> {
            width = scaledPx(context.dp(FLOATING_DRAG_PILL_WIDTH_DP))
            height = scaledPx(context.dp(FLOATING_DRAG_PILL_HEIGHT_DP))
        }
        applyHandleButtonScale(floatingCollapseButton, scale)
        applyHandleButtonScale(floatingDockButton, scale)
        floatingDockButton.updateLayoutParams<FrameLayout.LayoutParams> {
            marginStart = scaledPx(context.dp(FLOATING_HANDLE_BUTTON_SIZE_DP))
        }
        val handleSize = resizeHandleSize
        floatingResizeHandle.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = handleSize
            height = handleSize
            marginEnd = contentPadding
        }
        val handlePadding = scaledPx(context.dp(FLOATING_HANDLE_BUTTON_PADDING_DP))
        floatingResizeHandle.setPadding(handlePadding, handlePadding, handlePadding, handlePadding)
        heightLimitHint.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topMargin = scaledPx(context.dp(FLOATING_HINT_MARGIN_DP))
        }
        heightLimitHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, FLOATING_HINT_TEXT_SIZE_SP * scale / 100f)
        val hintPaddingH = scaledPx(context.dp(FLOATING_HINT_PADDING_HORIZONTAL_DP))
        val hintPaddingV = scaledPx(context.dp(FLOATING_HINT_PADDING_VERTICAL_DP))
        heightLimitHint.setPadding(hintPaddingH, hintPaddingV, hintPaddingH, hintPaddingV)
        (heightLimitHint.background as? GradientDrawable)?.cornerRadius =
            scaledPx(context.dp(FLOATING_HINT_CORNER_RADIUS_DP)).toFloat()
        keyboardView?.invalidateOutline()
    }

    private fun applyHandleButtonScale(
        button: ImageView,
        scale: Int,
    ) {
        val size = FloatingKeyboardGeometry.scaledDimension(context.dp(FLOATING_HANDLE_BUTTON_SIZE_DP), scale)
        button.updateLayoutParams<FrameLayout.LayoutParams> {
            width = size
            height = size
        }
        val padding = FloatingKeyboardGeometry.scaledDimension(context.dp(FLOATING_HANDLE_BUTTON_PADDING_DP), scale)
        button.setPadding(padding, padding, padding, padding)
    }

    private fun notifyFloatingScaleChanged(scale: Int) {
        if (lastNotifiedScale == scale) return
        lastNotifiedScale = scale
        onFloatingScaleChanged?.invoke(scale)
    }

    private fun showHeightLimitHint() {
        val cardWidth = keyboardView?.width ?: 0
        if (cardWidth > 0) {
            heightLimitHint.maxWidth = (cardWidth - 2 * cardContentPadding).coerceAtLeast(1)
        }
        if (heightLimitHint.visibility != View.VISIBLE) {
            heightLimitHint.visibility = View.VISIBLE
        }
    }

    private fun hideHeightLimitHint() {
        if (heightLimitHint.visibility != View.GONE) {
            heightLimitHint.visibility = View.GONE
        }
    }

    /**
     * Landscape floats again the next time the input view is shown, while a
     * manual dock still lasts for the current visible session.
     */
    fun onInputViewHidden() {
        landscapeDockOverride = false
        pendingModeReevaluation = true
        hideHeightLimitHint()
    }

    /** Re-applies the landscape default after a hidden input view is shown. */
    fun onInputViewShown() {
        if (!pendingModeReevaluation) return
        pendingModeReevaluation = false
        val host = host ?: return
        if (keyboardView == null || host.width <= 0 || host.height <= 0) return
        applyEffectiveFloatingMode()
    }

    /**
     * Landscape detection from the measured window. It is more accurate than
     * the configuration in split screen, where the input method window follows
     * the app window and not the device.
     */
    private fun isLandscapeWindow(): Boolean {
        val host = host ?: return false
        return host.width > 0 && host.height > 0 && host.width > host.height
    }

    private fun configurationIsLandscape(): Boolean = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun scaledDp(valueDp: Int): Int = FloatingKeyboardGeometry.scaledDimension(context.dp(valueDp), currentFloatingScale)

    /** The physical display size, including the space used by system bars. */
    private fun realDisplaySize(): Point {
        val point = Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = context.windowManager.maximumWindowMetrics.bounds
            point.set(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            context.windowManager.defaultDisplay.getRealSize(point)
        }
        return point
    }

    private fun floatingSafeWidth(): Int {
        val host = host ?: return 1
        return (host.width - floatingSystemInsets.left - floatingSystemInsets.right).coerceAtLeast(1)
    }

    /** The bottom edge the card rests on, used by layout, gestures and limits. */
    private fun floatingSafeBottom(): Int = maxOf(dockedBottomInset, floatingSystemInsets.bottom)

    /**
     * The real display short side, so 100% matches the docked portrait width.
     * Resources metrics can exclude system bars in landscape and shrink it.
     */
    private fun floatingBaseWidth(): Int {
        val display = realDisplaySize()
        return minOf(display.x, display.y)
            .coerceAtMost(floatingSafeWidth())
            .coerceAtLeast(1)
    }

    private fun floatingWidthMaxScalePercent(): Int {
        val baseWidth = floatingBaseWidth()
        return (floatingSafeWidth() * 100f / baseWidth)
            .toInt()
            .coerceIn(
                FloatingKeyboardGeometry.MIN_WIDTH_PERCENT,
                FloatingKeyboardGeometry.ABSOLUTE_MAX_WIDTH_PERCENT,
            )
    }

    private fun floatingMaxWidth(
        baseWidth: Int,
        maxScale: Int,
    ): Int = (baseWidth * maxScale / 100f)
        .roundToInt()
        .coerceAtMost(floatingSafeWidth())

    /**
     * The largest scale that fits the safe width and height. The drag strip
     * may overlap the top edge, so 100% still fits in landscape.
     */
    private fun floatingMaxScalePercent(): Int {
        val widthPercent = floatingWidthMaxScalePercent()
        if (floatingCollapsed) return widthPercent
        val baseKeyHeight = keyboardWindow.baseKeyboardHeightPx
        val host = host
        if (baseKeyHeight <= 0 || baseFixedCardHeightPx <= 0 || host == null) {
            return widthPercent
        }
        val baseCardHeight = baseFixedCardHeightPx + baseKeyHeight
        val safeTop = floatingSystemInsets.top
        val availableHeight = (host.height - safeTop - floatingSafeBottom()).coerceAtLeast(1)
        val preeditHeight = preedit.ui.root.height.coerceAtLeast(0)
        return FloatingKeyboardGeometry.maxScalePercent(
            widthPercent = widthPercent,
            availableHeight = availableHeight,
            baseCardHeight = baseCardHeight,
            unscaledFixedHeight = preeditHeight,
            overlayHeight = context.dp(FloatingKeyboardGeometry.DRAG_HANDLE_HEIGHT_DP),
        )
    }

    private fun floatingScalePreference(): Int {
        val landscape = isLandscapeWindow() || ((host?.width ?: 0) <= 0 && configurationIsLandscape())
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
        val keyboardView = keyboardView ?: return
        keyboardRefreshReevaluatesKeyboard = keyboardRefreshReevaluatesKeyboard || reevaluateKeyboard
        if (keyboardRefreshPosted) return
        keyboardRefreshPosted = true
        keyboardView.post {
            keyboardRefreshPosted = false
            if (host == null) return@post
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
    ): Float {
        val host = host ?: return translation
        return FloatingKeyboardGeometry.clampTranslation(
            translation = translation,
            containerStart = floatingSystemInsets.left,
            containerEnd = (host.width - floatingSystemInsets.right).coerceAtLeast(floatingSystemInsets.left),
            viewSize = cardWidth,
            basePosition = baseX,
        )
    }

    private fun clampFloatingY(
        translation: Float,
        cardHeight: Int,
        baseY: Int,
    ): Float {
        val host = host ?: return translation
        return FloatingKeyboardGeometry.clampTranslation(
            translation = translation,
            containerStart = floatingSystemInsets.top,
            containerEnd = (host.height - floatingSafeBottom()).coerceAtLeast(floatingSystemInsets.top),
            viewSize = cardHeight,
            basePosition = baseY,
            allowStartOverflow = true,
        )
    }

    private fun floatingDefaultTranslationX(cardWidth: Int): Float {
        val host = host ?: return 0f
        val keyboardView = keyboardView ?: return 0f
        val safeStart = floatingSystemInsets.left
        val safeEnd = (host.width - floatingSystemInsets.right).coerceAtLeast(safeStart)
        val targetX =
            FloatingKeyboardGeometry.defaultPosition(
                containerStart = safeStart,
                containerEnd = safeEnd,
                viewSize = cardWidth,
                margin = context.dp(FLOATING_CARD_EDGE_MARGIN_DP),
                center = isLandscapeWindow(),
            )
        return (targetX - keyboardView.left).toFloat()
    }

    private fun floatingDefaultTranslationY(cardHeight: Int): Float {
        val host = host ?: return 0f
        val keyboardView = keyboardView ?: return 0f
        val safeStart = floatingSystemInsets.top
        val safeEnd = (host.height - floatingSafeBottom()).coerceAtLeast(safeStart)
        val targetY =
            FloatingKeyboardGeometry.defaultPosition(
                containerStart = safeStart,
                containerEnd = safeEnd,
                viewSize = cardHeight,
                margin = 0,
                // Both orientations rest the card on the same safe bottom edge.
                center = false,
                // A card that is too tall overflows at the top, so its keys
                // and input bar stay reachable.
                allowStartOverflow = true,
            )
        return (targetY - keyboardView.top).toFloat()
    }

    /** Shrinks a card that no longer fits after a layout change or rotation. */
    private fun clampFloatingScaleToScreen() {
        if (!floatingActive || floatingCollapsed || floatingDragging || resizeChanged) return
        if (keyboardWindow.floatingScalePercent <= floatingMaxScalePercent()) return
        applyFloatingGeometry()
    }

    fun applyFloatingGeometry(rebuildKeyboard: Boolean = true) {
        val host = host ?: return
        val keyboardView = keyboardView ?: return
        if (!floatingActive || host.width <= 0) return
        val baseWidth = floatingBaseWidth()
        val maxScale = floatingMaxScalePercent()
        val requestedScale =
            floatingScalePreference().coerceIn(
                FloatingKeyboardGeometry.MIN_WIDTH_PERCENT,
                maxScale,
            )
        val targetWidth =
            FloatingKeyboardGeometry.widthFor(
                containerWidth = baseWidth,
                percent = requestedScale,
                // The keys stay readable even when the card is narrow.
                minWidth = context.dp(FLOATING_CARD_MIN_WIDTH_DP),
                maxWidth = floatingMaxWidth(baseWidth, maxScale),
            )
        val effectiveScale =
            FloatingKeyboardGeometry.percentFor(
                containerWidth = baseWidth,
                width = targetWidth,
                maxPercent = maxScale,
            )
        if (keyboardWindow.floatingScalePercent != effectiveScale) {
            keyboardWindow.floatingScalePercent = effectiveScale
            if (rebuildKeyboard) scheduleKeyboardRefresh()
        }
        notifyFloatingScaleChanged(effectiveScale)
        keyboardView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = targetWidth
            bottomMargin = floatingSafeBottom()
        }
        floatingDragHandle.visibility = View.VISIBLE
        keyboardView.clipToOutline = true
        keyboardView.outlineProvider = floatingOutlineProvider
        keyboardView.elevation = context.dp(FLOATING_CARD_ELEVATION_DP).toFloat()
        positionFloatingCard()
    }

    fun resetFloatingGeometry() {
        val keyboardView = keyboardView ?: return
        floatingDragging = false
        keyboardView.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = ConstraintLayout.LayoutParams.MATCH_PARENT
            bottomMargin = 0
        }
        keyboardView.translationX = 0f
        keyboardView.translationY = 0f
        keyboardView.elevation = 0f
        keyboardView.clipToOutline = false
        keyboardView.outlineProvider = null
        floatingDragHandle.visibility = View.GONE
        floatingDragHandle.translationY = 0f
        floatingResizeHandle.translationY = 0f
        hideHeightLimitHint()
        heightLimitHint.translationY = 0f
        updatePreeditTranslation()
    }

    /** A collapsed card is a strip with the handle and the input bar only. */
    fun applyFloatingCollapsedState() {
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
        if (!floatingActive || collapsed) hideHeightLimitHint()
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
            floatingResizeHandle.translationY = 0f
            heightLimitHint.translationY = 0f
            return
        }
        val keyboardView = keyboardView ?: return
        val cardTop = keyboardView.top + keyboardView.translationY
        val translation = (floatingSystemInsets.top - cardTop).coerceAtLeast(0f)
        floatingDragHandle.translationY = translation
        floatingResizeHandle.translationY = translation
        heightLimitHint.translationY = translation
    }

    /**
     * The position of the card, remembered per orientation as a fraction of
     * the available interval.
     *
     * The window shape is used instead of the configuration, because in split
     * screen the input method window follows the app window and not the device.
     */
    private fun savedFloatingPosition(): Pair<Float, Float> {
        val raw =
            if (isLandscapeWindow()) {
                prefs.floatingKeyboard.xLandscape.getValue() to
                    prefs.floatingKeyboard.yLandscape.getValue()
            } else {
                prefs.floatingKeyboard.xPortrait.getValue() to
                    prefs.floatingKeyboard.yPortrait.getValue()
            }
        // Anything outside the normalized range is a value from an older build
        // that stored absolute pixels; treat it as unset.
        return normalizedFraction(raw.first) to normalizedFraction(raw.second)
    }

    private fun normalizedFraction(value: Float): Float = value.takeIf { it in 0f..1f } ?: FloatingKeyboardPrefs.UNSET_FRACTION

    private fun positionFloatingCard() {
        val host = host ?: return
        val keyboardView = keyboardView ?: return
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
            if (savedX == FloatingKeyboardPrefs.UNSET_FRACTION) {
                floatingDefaultTranslationX(cardWidth)
            } else {
                val safeStart = floatingSystemInsets.left
                val safeEnd = (host.width - floatingSystemInsets.right).coerceAtLeast(safeStart)
                val targetX =
                    FloatingKeyboardGeometry.positionFromFraction(
                        fraction = savedX,
                        containerStart = safeStart,
                        containerEnd = safeEnd,
                        viewSize = cardWidth,
                    )
                clampFloatingX(
                    translation = targetX - baseX,
                    cardWidth = cardWidth,
                    baseX = baseX,
                )
            }
        keyboardView.translationY =
            if (savedY == FloatingKeyboardPrefs.UNSET_FRACTION) {
                floatingDefaultTranslationY(cardHeight)
            } else {
                val safeStart = floatingSystemInsets.top
                val safeEnd = (host.height - floatingSafeBottom()).coerceAtLeast(safeStart)
                val targetY =
                    FloatingKeyboardGeometry.positionFromFraction(
                        fraction = savedY,
                        containerStart = safeStart,
                        containerEnd = safeEnd,
                        viewSize = cardHeight,
                    )
                clampFloatingY(
                    translation = targetY - baseY,
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
    fun updatePreeditParent() {
        val host = host ?: return
        val keyboardView = keyboardView ?: return
        val preeditView = preedit.ui.root
        val target: ViewGroup = if (floatingActive) preeditHost else host
        if (preeditView.parent === target) return
        (preeditView.parent as? ViewGroup)?.removeView(preeditView)
        if (target === preeditHost) {
            preeditHost.addView(
                preeditView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START,
                ),
            )
        } else {
            val params =
                ConstraintLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    above(keyboardView)
                    startOfParent()
                }
            // Keep the preedit below the popup layer, which is added last.
            val popupIndex = host.indexOfChild(popup.root)
            host.addView(preeditView, if (popupIndex >= 0) popupIndex else host.childCount, params)
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
        val host = host ?: return false
        val keyboardView = keyboardView ?: return false
        if (!floatingActive) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                floatingDragging = true
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                dragStartTranslationX = keyboardView.translationX
                dragStartTranslationY = keyboardView.translationY
                host.parent?.requestDisallowInterceptTouchEvent(true)
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
                updateDragHandlePosition()
                updatePreeditTranslation()
                return true
            }

            MotionEvent.ACTION_UP -> {
                floatingDragging = false
                host.parent?.requestDisallowInterceptTouchEvent(false)
                snapFloatingCard()
                saveFloatingPosition()
                service.requestInputViewInsetsUpdate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                // The gesture was interrupted, so the card goes back to the
                // position it was saved at instead of a half finished drag.
                floatingDragging = false
                host.parent?.requestDisallowInterceptTouchEvent(false)
                positionFloatingCard()
                service.requestInputViewInsetsUpdate()
                return true
            }
        }
        return false
    }

    /** Resizes the card by dragging the grip; keys are rebuilt on release. */
    private fun handleFloatingResize(event: MotionEvent): Boolean {
        val host = host ?: return false
        val keyboardView = keyboardView ?: return false
        if (!floatingActive || floatingCollapsed) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resizeStartRawX = event.rawX
                resizeStartWidth = keyboardView.width
                resizeLastWidth = 0
                resizeChanged = false
                hideHeightLimitHint()
                keyboardWindow.setLayoutRefreshSuspended(true)
                host.parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val baseWidth = floatingBaseWidth()
                val widthLimit = floatingWidthMaxScalePercent()
                val maxScale = floatingMaxScalePercent()
                val maxWidth = floatingMaxWidth(baseWidth, maxScale)
                val minWidth = context.dp(FLOATING_CARD_MIN_WIDTH_DP).coerceAtMost(maxWidth)
                val requestedWidth =
                    (resizeStartWidth + (event.rawX - resizeStartRawX))
                        .roundToInt()
                val width = requestedWidth.coerceIn(minWidth, maxWidth)
                resizeLastWidth = width
                if (width != keyboardView.layoutParams.width) {
                    resizeChanged = true
                    val newScale =
                        FloatingKeyboardGeometry.percentFor(
                            containerWidth = baseWidth,
                            width = width,
                            maxPercent = maxScale,
                        )
                    if (keyboardWindow.floatingScalePercent != newScale) {
                        keyboardWindow.floatingScalePercent = newScale
                        notifyFloatingScaleChanged(newScale)
                    }
                    keyboardView.updateLayoutParams { this.width = width }
                }
                // The width grip has hit the height limit, not the screen
                // width, so explain why it stopped and how to shrink again.
                if (maxScale < widthLimit && requestedWidth >= maxWidth && event.rawX > resizeStartRawX) {
                    showHeightLimitHint()
                } else {
                    hideHeightLimitHint()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                host.parent?.requestDisallowInterceptTouchEvent(false)
                hideHeightLimitHint()
                setFloatingScalePreference(
                    FloatingKeyboardGeometry.percentFor(
                        containerWidth = floatingBaseWidth(),
                        width = resizeLastWidth.takeIf { it > 0 } ?: keyboardView.width,
                        maxPercent = floatingMaxScalePercent(),
                    ),
                )
                applyFloatingGeometry(rebuildKeyboard = false)
                finishFloatingResize()
                // Wait for the new width to be laid out before remembering
                // where the card is.
                keyboardView.post {
                    if (host == null) return@post
                    saveFloatingPosition()
                    service.requestInputViewInsetsUpdate()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                host.parent?.requestDisallowInterceptTouchEvent(false)
                hideHeightLimitHint()
                resizeLastWidth = 0
                applyFloatingGeometry(rebuildKeyboard = false)
                finishFloatingResize()
                return true
            }
        }
        return false
    }

    /**
     * Rebuilds the keys once after a resize gesture.
     *
     * The card width changes on every touch move, but rebuilding all key
     * views for each intermediate width is expensive and the keys are only
     * valid for the final width anyway. The width listener stays suspended
     * until the final width is laid out so it cannot start another rebuild.
     */
    private fun finishFloatingResize() {
        val keyboardView = keyboardView ?: return
        if (!resizeChanged) {
            keyboardWindow.setLayoutRefreshSuspended(false)
            return
        }
        resizeChanged = false
        keyboardView.doOnLayout {
            if (host == null) return@doOnLayout
            keyboardWindow.setLayoutRefreshSuspended(false)
            keyboardView.post { if (host != null) keyboardWindow.refreshKeyboards() }
        }
    }

    private fun snapFloatingCard() {
        val host = host ?: return
        val keyboardView = keyboardView ?: return
        val cardWidth = keyboardView.width
        val cardHeight = keyboardView.height
        if (cardWidth <= 0 || cardHeight <= 0) return
        val threshold = context.dp(FLOATING_SNAP_THRESHOLD_DP)
        keyboardView.translationX =
            FloatingKeyboardGeometry.snapTranslation(
                translation = keyboardView.translationX,
                containerStart = floatingSystemInsets.left,
                containerEnd = (host.width - floatingSystemInsets.right).coerceAtLeast(floatingSystemInsets.left),
                viewSize = cardWidth,
                basePosition = keyboardView.left,
                threshold = threshold,
                includeCentre = true,
            )
        keyboardView.translationY =
            FloatingKeyboardGeometry.snapTranslation(
                translation = keyboardView.translationY,
                containerStart = floatingSystemInsets.top,
                containerEnd = (host.height - floatingSafeBottom()).coerceAtLeast(floatingSystemInsets.top),
                viewSize = cardHeight,
                basePosition = keyboardView.top,
                threshold = threshold,
            )
        updateDragHandlePosition()
        updatePreeditTranslation()
    }

    private fun saveFloatingPosition() {
        val host = host ?: return
        val keyboardView = keyboardView ?: return
        val cardWidth = keyboardView.width
        val cardHeight = keyboardView.height
        if (cardWidth <= 0 || cardHeight <= 0) return
        val x = (keyboardView.left + keyboardView.translationX).roundToInt()
        val y = (keyboardView.top + keyboardView.translationY).roundToInt()
        val safeStartX = floatingSystemInsets.left
        val safeEndX = (host.width - floatingSystemInsets.right).coerceAtLeast(safeStartX)
        val safeStartY = floatingSystemInsets.top
        val safeEndY = (host.height - floatingSafeBottom()).coerceAtLeast(safeStartY)
        val xFraction =
            FloatingKeyboardGeometry.positionFraction(
                position = x,
                containerStart = safeStartX,
                containerEnd = safeEndX,
                viewSize = cardWidth,
            )
        val yFraction =
            FloatingKeyboardGeometry.positionFraction(
                position = y,
                containerStart = safeStartY,
                containerEnd = safeEndY,
                viewSize = cardHeight,
            )
        if (isLandscapeWindow()) {
            prefs.floatingKeyboard.xLandscape.setValue(xFraction)
            prefs.floatingKeyboard.yLandscape.setValue(yFraction)
        } else {
            prefs.floatingKeyboard.xPortrait.setValue(xFraction)
            prefs.floatingKeyboard.yPortrait.setValue(yFraction)
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
        onKeyboardSizeChanged?.invoke()
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
        if (host == null || keyboardView == null) return
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
        if (host == null || keyboardView == null) return
        if (isLandscapeWindow()) {
            landscapeDockOverride = !floating
            applyEffectiveFloatingMode(reevaluateKeyboard = true)
        } else {
            prefs.floatingKeyboard.portraitFloat.setValue(floating)
            applyEffectiveFloatingMode(reevaluateKeyboard = true)
        }
    }

    fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        if (host == null || keyboardView == null) return
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

    fun onApplyWindowInsets(
        insets: WindowInsets,
        dockedBottomInset: Int,
    ) {
        this.dockedBottomInset = dockedBottomInset
        val compat = WindowInsetsCompat.toWindowInsetsCompat(insets)
        val types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        floatingSystemInsets = FloatingKeyboardGeometry.mergeSystemInsets(
            current = compat.getInsets(types),
            stable = compat.getInsetsIgnoringVisibility(types),
        )
        if (floatingActive) {
            applyFloatingGeometry()
        }
    }

    /** Releases gesture state when the input view is going away. */
    fun onDetachedFromWindow() {
        floatingDragging = false
        resizeChanged = false
        resizeLastWidth = 0
        hideHeightLimitHint()
        keyboardWindow.setLayoutRefreshSuspended(false)
        host = null
        keyboardView = null
        onKeyboardSizeChanged = null
    }

    override fun onWindowAttached(window: BoardWindow) {
        if (host == null || keyboardView == null) return
        // Another window has to be visible, so a collapsed card is expanded
        // again. The keyboard window itself does not need it, because the card
        // may be collapsed on purpose.
        if (window is KeyboardWindow) return
        expandFloatingKeyboard()
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

        /** The horizontal gap between the card and the safe screen edge when it first opens. */
        private const val FLOATING_CARD_EDGE_MARGIN_DP = 12

        /** The narrowest the card may get, so the keys stay readable. */
        private const val FLOATING_CARD_MIN_WIDTH_DP = 260

        /** How close the card must be to an edge to snap to it. */
        private const val FLOATING_SNAP_THRESHOLD_DP = 24

        private const val FLOATING_HINT_MARGIN_DP = 4
        private const val FLOATING_HINT_PADDING_HORIZONTAL_DP = 8
        private const val FLOATING_HINT_PADDING_VERTICAL_DP = 4
        private const val FLOATING_HINT_CORNER_RADIUS_DP = 8
        private const val FLOATING_HINT_TEXT_SIZE_SP = 12f
    }
}
