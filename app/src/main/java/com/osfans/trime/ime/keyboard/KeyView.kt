/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.core.view.children
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.osfans.trime.R
import com.osfans.trime.core.T9Action
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.performance.InputLatencyLog
import com.osfans.trime.ime.popup.PopupAction
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.util.setCheckedState
import com.osfans.trime.util.sp
import kotlinx.coroutines.Job
import splitties.dimensions.dp
import timber.log.Timber

@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
class KeyView(
    context: Context,
    private val theme: Theme,
    private val key: Key,
    private val keyboard: Keyboard,
    private val keyboardView: KeyboardView,
    private val keyboardActionListener: KeyboardActionListener,
) : GestureFrame(context) {

    private val service: TrimeInputMethodService
        get() = keyboardView.service

    private val popup: PopupDelegate
        get() = keyboardView.popup

    private val deletedTextBuffer = ArrayDeque<String>()
    private var pendingRepeat: Job? = null
    private var feedbackNanos = 0L

    private fun <T> dispatchInput(action: () -> T): T = service.withInputFeedback(feedbackNanos, action)

    private var keyPressed = false
    override fun isPressed(): Boolean = keyPressed

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private var cachedIcon: IconicsDrawable? = null
    private var cachedIconName: String? = null

    private val cachedLocation = intArrayOf(0, 0)
    private val cachedBounds = Rect()
    val bounds: Rect
        get() = cachedBounds.also {
            // Ancestor scrolling moves the key without laying it out again.
            updateBounds()
        }

    fun updateBounds() {
        val (x, y) = cachedLocation.also { getLocationInWindow(it) }
        cachedBounds.set(x + key.extraWidthLeft, y, x + width - key.extraWidthRight, y + height)
    }

    init {
        setWillNotDraw(false)
        isRepeatable = key.click?.isRepeatable ?: false
        isSlideCursor = key.click?.isSlideCursor ?: false
        isSlideDelete = key.click?.isSlideDelete ?: false
        hasLongPress = key.hasAction(KeyBehavior.LONG_CLICK)
        // Explicit theme double/lazy actions keep priority over built-in Shift locking.
        hasDouble = key.hasAction(KeyBehavior.DOUBLE_CLICK) || (key.isShift && !key.hasAction(KeyBehavior.LAZY_DOUBLE_CLICK))
        hasLazyDouble = key.hasAction(KeyBehavior.LAZY_DOUBLE_CLICK)
        hasPopup = key.popup.isNotEmpty()
        isClickable = key.click != null
        importantForAccessibility = if (isClickable) IMPORTANT_FOR_ACCESSIBILITY_YES else IMPORTANT_FOR_ACCESSIBILITY_NO
        updateAccessibilityDescription()

        onPress = {
            if (keyboard.firstPressedKeyIndex == -1) keyboard.firstPressedKeyIndex = id
            setPressedState(true)
            key.getCode(KeyBehavior.CLICK).let { keyboardActionListener.onPress(it) }
            feedbackNanos = if (InputLatencyLog.enabled) InputLatencyLog.now() else 0L
            showPopupPreview()
        }

        onRelease = { behavior, isFromLongPress ->
            Timber.d("KeyView release: behavior=$behavior, fromLongPress=$isFromLongPress")
            if (isFromLongPress) {
                if (hasPopup) {
                    val triggerAction = PopupAction.TriggerAction(id)
                    popup.listener.onPopupAction(triggerAction)
                    triggerAction.outAction?.let { action ->
                        dispatchInput { keyboardActionListener.onAction(theme.resolveAction(action)) }
                        dismissPopupPreview()
                    }
                    setPressedState(false)
                } else if (isRepeatable) {
                    if (pendingRepeat?.isCompleted != false) {
                        key.getAction(KeyBehavior.CLICK)?.let { processKeyAction(it, KeyBehavior.CLICK, repeated = true) }
                    }
                }
            } else {
                when (behavior) {
                    KeyBehavior.CLICK -> {
                        val pressedIdx = keyboard.firstPressedKeyIndex
                        val actionBehavior = if (pressedIdx != -1 && pressedIdx != id) KeyBehavior.COMBO else behavior
                        key.getAction(actionBehavior)?.let { processKeyAction(it, actionBehavior) }
                    }

                    KeyBehavior.DOUBLE_CLICK -> {
                        if (key.hasAction(behavior)) {
                            key.getAction(behavior)?.let { processKeyAction(it, behavior) }
                        } else if (key.isShift) {
                            val actionBehavior = if (keyboardView.hookShiftLock) behavior else KeyBehavior.CLICK
                            key.getAction(KeyBehavior.CLICK)?.let { processKeyAction(it, actionBehavior) }
                        }
                    }

                    KeyBehavior.LAZY_DOUBLE_CLICK,
                    KeyBehavior.SWIPE_UP, KeyBehavior.SWIPE_DOWN, KeyBehavior.SWIPE_LEFT, KeyBehavior.SWIPE_RIGHT,
                    -> if (key.hasAction(behavior)) {
                        key.getAction(behavior)?.let { processKeyAction(it, behavior) }
                    }

                    else -> {}
                }

                setPressedState(false)
                dismissPopupPreview()
            }
            if (keyboard.firstPressedKeyIndex == id) keyboard.firstPressedKeyIndex = -1
        }

        onSwipe = { direction ->
            setPressedState(true)
            if (key.hasAction(direction)) showPopupPreview(direction) else dismissPopupPreview()
        }

        onSlide = slide@{ delta, _, _ ->
            if (isSlideCursor) {
                repeat(kotlin.math.abs(delta).coerceAtMost(64)) {
                    dispatchInput { keyboardActionListener.onAction(theme.resolveAction(if (delta > 0) "Right" else "Left")) }
                }
            } else if (isSlideDelete) {
                if (RimeDaemon.readCached { statusCached.isComposing } == true) {
                    when {
                        delta < 0 -> dispatchInput { keyboardActionListener.onKey(KeyEvent.KEYCODE_DEL, 0) }

                        delta > 0 -> dispatchInput {
                            service.postRimeJob {
                                if (t9Cached.canUndo) t9Action(t9Cached.revision, T9Action.Undo)
                            }
                        }
                    }
                    return@slide
                }
                val ic = service.currentInputConnection
                when {
                    delta < 0 -> {
                        val beforeText = ic.getTextBeforeCursor(1, 0) ?: ""
                        if (beforeText.isNotEmpty()) {
                            deletedTextBuffer.addFirst(beforeText.toString())
                            ic.deleteSurroundingText(1, 0)
                        }
                    }

                    delta > 0 -> {
                        if (deletedTextBuffer.isNotEmpty()) {
                            ic.commitText(deletedTextBuffer.removeFirst(), 1)
                        }
                    }
                }
            }
        }

        onLongClick = {
            if (key.popup.isNotEmpty()) {
                dismissPopupPreview()
                showPopupKeyboard()
            } else if (hasLongPress) {
                key.getAction(KeyBehavior.LONG_CLICK)?.let {
                    processKeyAction(it, KeyBehavior.LONG_CLICK)
                    setPressedState(false)
                    dismissPopupPreview()
                }
            }
        }

        onMove = { x, y, isLongPress ->
            if (isLongPress && hasPopup) {
                popup.listener.onPopupAction(PopupAction.ChangeFocusAction(id, x, y))
            }
        }

        onCancel = {
            if (keyboard.firstPressedKeyIndex == id) keyboard.firstPressedKeyIndex = -1
            deletedTextBuffer.clear()
            setPressedState(false)
            dismissPopupPreview()
        }
        onRepeatEnd = {
            pendingRepeat?.cancel()
            pendingRepeat = null
        }
    }

    fun setPressedState(pressed: Boolean) {
        if (keyPressed != pressed) {
            keyPressed = pressed
            if (pressed) {
                key.onPressed()
            } else {
                key.onReleased()
            }
            invalidate()
        }
    }

    private fun processKeyAction(action: KeyAction, behavior: KeyBehavior, repeated: Boolean = false) {
        Timber.d("processKeyAction: type=$behavior")

        if (action.isModifierKey) {
            if (keyboardView.hookShiftLock && key.isShift && behavior == KeyBehavior.DOUBLE_CLICK &&
                !key.hasAction(KeyBehavior.DOUBLE_CLICK) && !key.hasAction(KeyBehavior.LAZY_DOUBLE_CLICK) &&
                action.modifierKeyOnMask == KeyEvent.META_SHIFT_ON
            ) {
                keyboard.lockShift()
            } else {
                keyboard.clickModifierKey(
                    action.isShiftLock xor (behavior == KeyBehavior.LONG_CLICK),
                    action.modifierKeyOnMask,
                )
            }
            keyboardView.invalidateAllKeys()
            return
        }

        if (repeated) {
            pendingRepeat = dispatchInput { keyboardActionListener.onRepeat(action) }
        } else {
            dispatchInput { keyboardActionListener.onAction(action) }
        }

        val hookArrow = if (keyboardView.hookShiftArrow) {
            when (action.code) {
                in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT -> true
                KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END -> true
                else -> false
            }
        } else {
            false
        }

        if (!hookArrow) {
            if (keyboard.refreshModifier()) {
                keyboardView.invalidateAllKeys()
            }
        }
    }

    private fun showPopupKeyboard() {
        val popupKeys = key.popup
        if (popupKeys.isEmpty()) return

        popup.listener.onPopupAction(
            PopupAction.ShowKeyboardAction(id, popupKeys, bounds),
        )
    }

    private fun showPopupPreview(behavior: KeyBehavior = KeyBehavior.CLICK) {
        if (!keyboardView.popupOnKeyPress) return
        key.getPreviewText(behavior).takeIf { it.isNotEmpty() }?.let { previewText ->
            val context = if (previewText.isIconFont) {
                previewText
            } else {
                String(Character.toChars(previewText.codePointAt(0)))
            }
            popup.listener.onPopupAction(PopupAction.PreviewAction(id, context, bounds))
        }
    }

    private fun dismissPopupPreview() {
        popup.listener.onPopupAction(
            PopupAction.DismissAction(id),
        )
    }

    private fun accessibilityAction(): KeyAction? = key.getAction(KeyBehavior.CLICK)

    private fun accessibilityChecked(action: KeyAction?): Boolean = when {
        key.isShift -> keyboard.isShifted
        action?.isModifierKey == true -> keyboard.modifier and action.modifierKeyOnMask != 0 || key.isOn
        !action?.toggle.isNullOrEmpty() -> RimeDaemon.readCached { getRuntimeOptionCached(checkNotNull(action).toggle) } ?: key.isOn
        else -> key.isOn
    }

    private fun canAccessibilityLockShift(): Boolean = key.isShift && keyboardView.hookShiftLock &&
        !key.hasAction(KeyBehavior.DOUBLE_CLICK) && !key.hasAction(KeyBehavior.LAZY_DOUBLE_CLICK)

    private fun canAccessibilityLongPress(): Boolean = hasLongPress && !hasPopup && !isRepeatable

    private fun accessibilityActivationAllowed(): Boolean = isShown && isAttachedToWindow &&
        RimeDaemon.readCached { isReady } == true && keyboard.firstPressedKeyIndex == -1 &&
        keyboard.keys.none { it.isPressed } &&
        keyboardView.children.none { (it as? GestureFrame)?.hasPendingTouchGesture == true }

    /** Cached key views describe current visible captions without reading editor content. */
    private fun updateAccessibilityDescription() {
        if (!isClickable) {
            contentDescription = null
            ViewCompat.setStateDescription(this, null)
            return
        }
        val action = accessibilityAction()
        val functional = when (action?.code) {
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> context.getString(R.string.delete)
            KeyEvent.KEYCODE_ENTER -> keyboardView.labelEnter
            KeyEvent.KEYCODE_SPACE -> context.getString(R.string.t9_space)
            KeyEvent.KEYCODE_APOSTROPHE -> if (keyboard.isT9Layout) context.getString(R.string.t9_separator) else null
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> context.getString(R.string.t13_key_shift)
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> context.getString(R.string.t13_key_control)
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> context.getString(R.string.t13_key_alt)
            KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> context.getString(R.string.t13_key_meta)
            KeyEvent.KEYCODE_SYM -> context.getString(R.string.t13_key_symbols)
            KeyEvent.KEYCODE_SWITCH_CHARSET, KeyEvent.KEYCODE_LANGUAGE_SWITCH -> context.getString(R.string.t13_key_switch_language)
            KeyEvent.KEYCODE_EISU -> context.getString(R.string.t13_key_switch_keyboard)
            KeyEvent.KEYCODE_ESCAPE -> context.getString(R.string.t13_key_escape)
            KeyEvent.KEYCODE_TAB -> context.getString(R.string.t13_key_tab)
            KeyEvent.KEYCODE_DPAD_LEFT -> context.getString(R.string.t13_key_left)
            KeyEvent.KEYCODE_DPAD_RIGHT -> context.getString(R.string.t13_key_right)
            KeyEvent.KEYCODE_DPAD_UP -> context.getString(R.string.t13_key_up)
            KeyEvent.KEYCODE_DPAD_DOWN -> context.getString(R.string.t13_key_down)
            KeyEvent.KEYCODE_MOVE_HOME -> context.getString(R.string.t13_key_home)
            KeyEvent.KEYCODE_MOVE_END -> context.getString(R.string.t13_key_end)
            KeyEvent.KEYCODE_PAGE_UP -> context.getString(R.string.t13_key_page_up)
            KeyEvent.KEYCODE_PAGE_DOWN -> context.getString(R.string.t13_key_page_down)
            else -> null
        }
        val description = KeyAccessibilityLabelPolicy.label(
            key.getLabel(),
            key.hint.takeUnless { keyboardView.hideKeyHint }.orEmpty(),
            if (!keyboardView.hideKeySymbol) key.symbolLabel else "",
            functional,
            context.getString(R.string.t13_key_action),
            preferDisplay = action?.code == KeyEvent.KEYCODE_EISU ||
                action?.code == KeyEvent.KEYCODE_SWITCH_CHARSET || action?.code == KeyEvent.KEYCODE_LANGUAGE_SWITCH,
        )
        if (contentDescription != description) contentDescription = description
        ViewCompat.setStateDescription(
            this,
            if (key.isShift) {
                context.getString(
                    when {
                        keyboardView.isCapsOn -> R.string.t13_key_caps_locked
                        keyboard.isShifted -> R.string.t13_key_shift_on
                        else -> R.string.t13_key_shift_off
                    },
                )
            } else {
                null
            },
        )
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        updateAccessibilityDescription()
        super.onInitializeAccessibilityNodeInfo(info)
        val action = accessibilityAction()
        info.className = Button::class.java.name
        info.contentDescription = contentDescription
        info.isCheckable = action?.isModifierKey == true || !action?.toggle.isNullOrEmpty() || action?.isSticky == true
        info.setCheckedState(info.isCheckable && accessibilityChecked(action))
        info.isClickable = isEnabled && isClickable
        info.isLongClickable = isEnabled && canAccessibilityLongPress()
        if (!info.isClickable) info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK)
        if (info.isLongClickable) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, context.getString(R.string.t13_key_long_press)))
        }
        if (!isEnabled) return
        for ((behavior, id, label) in accessibilitySwipeActions) {
            if (key.hasAction(behavior)) info.addAction(AccessibilityNodeInfo.AccessibilityAction(id, context.getString(label)))
        }
        if (canAccessibilityLockShift()) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.t13_key_action_lock_shift, context.getString(R.string.t13_key_lock_shift)))
        }
    }

    override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
        updateAccessibilityDescription()
        super.onInitializeAccessibilityEvent(event)
        event.className = Button::class.java.name
        event.isChecked = accessibilityChecked(accessibilityAction())
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        updateAccessibilityDescription()
        // View's hover events drive touch exploration; they must never press or commit a key.
        return super.onHoverEvent(event)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean = when (action) {
        AccessibilityNodeInfo.ACTION_CLICK -> accessibilityActivationAllowed() && isClickable && performAccessibilityBehavior(KeyBehavior.CLICK)

        AccessibilityNodeInfo.ACTION_LONG_CLICK -> accessibilityActivationAllowed() && canAccessibilityLongPress() && performAccessibilityBehavior(KeyBehavior.LONG_CLICK)

        R.id.t13_key_action_lock_shift -> accessibilityActivationAllowed() && canAccessibilityLockShift() && performAccessibilityBehavior(KeyBehavior.DOUBLE_CLICK)

        else -> accessibilitySwipeActions.firstOrNull { it.second == action }?.let { (behavior, _, _) ->
            accessibilityActivationAllowed() && key.hasAction(behavior) && performAccessibilityBehavior(behavior)
        } ?: super.performAccessibilityAction(action, arguments)
    }

    private companion object {
        val accessibilitySwipeActions = listOf(
            Triple(KeyBehavior.SWIPE_UP, R.id.t13_key_action_swipe_up, R.string.t13_key_swipe_up),
            Triple(KeyBehavior.SWIPE_DOWN, R.id.t13_key_action_swipe_down, R.string.t13_key_swipe_down),
            Triple(KeyBehavior.SWIPE_LEFT, R.id.t13_key_action_swipe_left, R.string.t13_key_swipe_left),
            Triple(KeyBehavior.SWIPE_RIGHT, R.id.t13_key_action_swipe_right, R.string.t13_key_swipe_right),
        )
    }

    fun refreshAppearance() {
        updateAccessibilityDescription()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAccessibilityDescription()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalWidth = key.width + key.extraWidthLeft + key.extraWidthRight
        val desiredWidth = totalWidth + paddingLeft + paddingRight
        val desiredHeight = key.height + paddingTop + paddingBottom

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawBackground(canvas, key)

        visualContentLayout().forEach { content ->
            val color = if (content.isMain) key.getTextColor() else key.getSymbolColor()
            if (content.icon) {
                drawIcon(canvas, content.original, content.bounds, color)
            } else {
                val paint = if (content.isMain) textPaint else symbolPaint
                paint.color = color
                paint.textSize = content.text.size
                paint.typeface = if (content.isMain) theme.fonts.key else theme.fonts.symbol
                paint.textAlign = Paint.Align.CENTER
                paint.clearShadowLayer()
                val centerX = (content.bounds.left + content.bounds.right) / 2f
                content.text.lines.forEachIndexed { index, line ->
                    canvas.drawText(line, centerX, content.bounds.top - content.text.top + content.text.lineHeight * index, paint)
                }
            }
        }
    }

    private fun drawBackground(canvas: Canvas, k: Key) {
        val bg = k.getBackgroundDrawable() ?: return

        if (bg is GradientDrawable) {
            k.roundCorner.takeIf { it > 0f }?.let { bg.cornerRadius = dp(it) }
            k.keyBorder.takeIf { it > 0 }?.let { bg.setStroke(dp(it), k.getBorderColor()) }
        }

        bg.setBounds(
            paddingLeft,
            paddingTop,
            width - paddingRight,
            height - paddingBottom,
        )
        bg.draw(canvas)
    }

    internal data class VisualContent(
        val original: String,
        val isMain: Boolean,
        val icon: Boolean,
        val area: KeyContentLayout.Box,
        val bounds: KeyContentLayout.Box,
        val text: KeyContentLayout.Text,
    )

    /** The same finite plan drives Canvas drawing and the attached-view layout regression. */
    internal fun visualContentLayout(): List<VisualContent> {
        val label = key.getLabel().let { if (it == "enter_labels") keyboardView.labelEnter else it }
        val symbol = key.symbolLabel.takeUnless {
            keyboardView.hideKeySymbol || (keyboard.isT9Layout && it == key.hint)
        }.orEmpty()
        val hint = key.hint.takeUnless { keyboardView.hideKeyHint }.orEmpty()
        val horizontalInset = minOf(dp(4).toFloat(), (width - paddingLeft - paddingRight).coerceAtLeast(0) / 4f)
        val verticalInset = minOf(dp(2).toFloat(), (height - paddingTop - paddingBottom).coerceAtLeast(0) / 4f)
        val content = KeyContentLayout.Box(
            paddingLeft + horizontalInset,
            paddingTop + verticalInset,
            (width - paddingRight - horizontalInset).coerceAtLeast(paddingLeft + horizontalInset),
            (height - paddingBottom - verticalInset).coerceAtLeast(paddingTop + verticalInset),
        )
        val bands = KeyContentLayout.bands(content, symbol.isNotEmpty(), hint.isNotEmpty(), dp(14).toFloat(), dp(1).toFloat())
        val result = ArrayList<VisualContent>(3)
        fun add(text: String, main: Boolean, area: KeyContentLayout.Box?, offsetX: Float, offsetY: Float) {
            if (text.isEmpty() || area == null || area.width <= 0f || area.height <= 0f) return
            val preferred = if (main) {
                sp(key.keyTextSize.takeIf { it > 0 } ?: if (text.length > 1 && !text.isIconFont) keyboardView.keyLongTextSize else keyboardView.keyTextSize)
            } else {
                minOf(sp(key.symbolTextSize.takeIf { it > 0f } ?: keyboardView.symbolTextSize), dp(12).toFloat())
            }
            val icon = text.isIconFont
            val layout = if (icon) {
                val size = minOf(preferred, area.width, area.height, if (main && keyboard.isT9Layout) dp(26).toFloat() else preferred)
                KeyContentLayout.Text(emptyList(), size, size, 0f, size)
            } else {
                val paint = if (main) textPaint else symbolPaint
                paint.typeface = if (main) theme.fonts.key else theme.fonts.symbol
                paint.textAlign = Paint.Align.LEFT
                val ink = Rect()
                KeyContentLayout.text(text, preferred, dp(if (main) 12 else 8).toFloat(), area, 2) { line, size ->
                    paint.textSize = size
                    val advance = paint.measureText(line)
                    ink.setEmpty()
                    paint.getTextBounds(line, 0, line.length, ink)
                    val metrics = paint.fontMetrics
                    // Include italic/fallback-glyph overhang, not only the logical advance width.
                    val halfWidth = maxOf(advance / 2f, kotlin.math.abs(ink.left - advance / 2f), kotlin.math.abs(ink.right - advance / 2f))
                    KeyContentLayout.Metrics(halfWidth * 2f, minOf(metrics.top, ink.top.toFloat()), maxOf(metrics.bottom, ink.bottom.toFloat()))
                }
            }
            val drawingHeight = if (icon) layout.size else layout.height
            result.add(
                VisualContent(
                    text,
                    main,
                    icon,
                    area,
                    KeyContentLayout.place(area, layout.width, drawingHeight, sp(offsetX), sp(offsetY)),
                    layout,
                ),
            )
        }
        add(label, true, bands.main, key.keyTextOffsetX, key.keyTextOffsetY)
        add(symbol, false, bands.top, key.keySymbolOffsetX, key.keySymbolOffsetY)
        add(hint, false, bands.bottom, key.keyHintOffsetX, key.keyHintOffsetY)
        return result
    }

    private fun drawIcon(canvas: Canvas, iconName: String, bounds: KeyContentLayout.Box, color: Int) {
        val cmdName = iconName.toIconName()
        val icon = if (cachedIconName == cmdName) {
            cachedIcon!!
        } else {
            IconicsDrawable(context, cmdName).apply {
                sizeDp = bounds.width.toInt().coerceAtLeast(1)
            }.also {
                cachedIcon = it
                cachedIconName = cmdName
            }
        }
        icon.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        icon.setBounds(kotlin.math.ceil(bounds.left).toInt(), kotlin.math.ceil(bounds.top).toInt(), bounds.right.toInt(), bounds.bottom.toInt())
        icon.draw(canvas)
    }
}
