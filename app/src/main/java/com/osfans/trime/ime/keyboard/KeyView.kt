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
import android.view.KeyEvent
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.popup.PopupAction
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.util.sp
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
    private var boundsValid = false

    private val minTextSize: Float = sp(8f)

    private val contentLeft: Float
        get() = paddingLeft.toFloat()
    private val contentTop: Float
        get() = paddingTop.toFloat()
    private val contentRight: Float
        get() = (width - paddingRight).toFloat()
    private val contentBottom: Float
        get() = (height - paddingBottom).toFloat()
    private val contentWidth: Float
        get() = (contentRight - contentLeft).coerceAtLeast(0f)
    private val contentHeight: Float
        get() = (contentBottom - contentTop).coerceAtLeast(0f)

    val bounds: Rect
        get() = cachedBounds.also {
            if (!boundsValid) updateBounds()
        }

    fun updateBounds() {
        val (x, y) = cachedLocation.also { getLocationInWindow(it) }
        cachedBounds.set(x + key.extraWidthLeft, y, x + width - key.extraWidthRight, y + height)
        boundsValid = true
    }

    init {
        setWillNotDraw(false)
        isRepeatable = key.click?.isRepeatable ?: false
        isSlideCursor = key.click?.isSlideCursor ?: false
        isSlideDelete = key.click?.isSlideDelete ?: false
        hasLongPress = key.hasAction(KeyBehavior.LONG_CLICK)
        hasDouble = key.hasAction(KeyBehavior.DOUBLE_CLICK)
        hasLazyDouble = key.hasAction(KeyBehavior.LAZY_DOUBLE_CLICK)
        hasPopup = key.popup.isNotEmpty()

        onPress = {
            if (keyboard.firstPressedKeyIndex == -1) keyboard.firstPressedKeyIndex = id
            setPressedState(true)
            key.getCode(KeyBehavior.CLICK).let { keyboardActionListener.onPress(it) }
            showPopupPreview()
        }

        onRelease = { behavior, isFromLongPress ->
            Timber.d("KeyView release: label=${key.getLabel()}, behavior=$behavior, fromLongPress=$isFromLongPress")
            if (isFromLongPress) {
                if (hasPopup) {
                    val triggerAction = PopupAction.TriggerAction(id)
                    popup.listener.onPopupAction(triggerAction)
                    triggerAction.outAction?.let { action ->
                        keyboardActionListener.onAction(theme.resolveAction(action))
                        dismissPopupPreview()
                    }
                    setPressedState(false)
                } else if (isRepeatable) {
                    key.getAction(KeyBehavior.CLICK)?.let { processKeyAction(it, KeyBehavior.CLICK) }
                }
            } else {
                when (behavior) {
                    KeyBehavior.CLICK -> {
                        val pressedIdx = keyboard.firstPressedKeyIndex
                        val actionBehavior = if (pressedIdx != -1 && pressedIdx != id) KeyBehavior.COMBO else behavior
                        key.getAction(actionBehavior)?.let { processKeyAction(it, actionBehavior) }
                    }

                    KeyBehavior.DOUBLE_CLICK, KeyBehavior.LAZY_DOUBLE_CLICK,
                    KeyBehavior.SWIPE_UP, KeyBehavior.SWIPE_DOWN, KeyBehavior.SWIPE_LEFT, KeyBehavior.SWIPE_RIGHT,
                    ->
                        key.getAction(behavior)?.let { processKeyAction(it, behavior) }

                    else -> {}
                }

                setPressedState(false)
                dismissPopupPreview()
            }
            if (keyboard.firstPressedKeyIndex == id) keyboard.firstPressedKeyIndex = -1
        }

        onSwipe = { direction ->
            setPressedState(true)
            showPopupPreview(direction)
        }

        onSlide = { delta, _, _ ->
            if (isSlideCursor) {
                when {
                    delta > 0 -> keyboardActionListener.onAction(theme.resolveAction("Right"))
                    delta < 0 -> keyboardActionListener.onAction(theme.resolveAction("Left"))
                }
            } else if (isSlideDelete) {
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
            deletedTextBuffer.clear()
            setPressedState(false)
            dismissPopupPreview()
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

    private fun processKeyAction(action: KeyAction, behavior: KeyBehavior) {
        Timber.d("processKeyAction: label=${key.getLabel()}, code=${action.code}, type=$behavior")

        if (action.isModifierKey) {
            keyboard.clickModifierKey(
                action.isShiftLock xor (behavior == KeyBehavior.LONG_CLICK),
                action.modifierKeyOnMask,
            )
            keyboardView.invalidateAllKeys()
            return
        }

        keyboardActionListener.onAction(action)

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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val totalWidth = key.width + key.extraWidthLeft + key.extraWidthRight
        val desiredWidth = totalWidth + paddingLeft + paddingRight
        val desiredHeight = key.height + paddingTop + paddingBottom

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        boundsValid = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.save()
        // 所有内容严格限制在可见按键区域内，避免极端字号或 offset 画出按键
        canvas.clipRect(contentLeft, contentTop, contentRight, contentBottom)

        drawBackground(canvas, key)

        val label = key.getLabel().let {
            if (it == "enter_labels") keyboardView.labelEnter else it
        }

        if (label.isNotEmpty()) {
            drawLabel(canvas, label)
        }

        val symbol = key.symbolLabel
        if (symbol.isNotEmpty()) {
            drawSymbol(canvas, symbol)
        }

        val hint = key.hint
        if (hint.isNotEmpty()) {
            drawSymbol(canvas, hint, isTop = false)
        }

        canvas.restore()
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

    private fun drawLabel(canvas: Canvas, label: String) {
        val textColor = key.getTextColor()
        val desiredSize = sp(key.keyTextSize.takeIf { it > 0 } ?: if (label.length > 1 && !label.isIconFont) keyboardView.keyLongTextSize else keyboardView.keyTextSize)

        if (label.isIconFont) {
            drawIcon(canvas, label, desiredSize.toInt(), textColor, key.keyTextOffsetX, key.keyTextOffsetY)
            return
        }

        textPaint.apply {
            color = textColor
            typeface = theme.fonts.key
            clearShadowLayer()
            textSize = desiredSize
            textScaleX = 1f
        }

        val rawWidth = textPaint.measureText(label)
        val rawMetrics = textPaint.fontMetrics
        val rawHeight = rawMetrics.descent - rawMetrics.ascent

        // 上下有符号/hint 且总高度放不下时，给它们预留空间，主标签居中在剩余区域
        val reserveTop = topSymbolReserve()
        val reserveBottom = bottomHintReserve()
        val needsBand = rawHeight + reserveTop + reserveBottom > contentHeight
        val maxHeight = if (needsBand) {
            (contentHeight - reserveTop - reserveBottom).coerceAtLeast(minTextSize)
        } else {
            contentHeight
        }

        val fit = KeyTextFitter.fit(
            desiredSize = desiredSize,
            textWidth = rawWidth,
            textHeight = rawHeight,
            maxWidth = contentWidth,
            maxHeight = maxHeight,
            minSize = minTextSize,
        )
        textPaint.textSize = fit.textSize
        textPaint.textScaleX = fit.textScaleX

        val metrics = textPaint.fontMetrics
        val fittedWidth = textPaint.measureText(label)
        val fittedHeight = metrics.descent - metrics.ascent
        val bandTop = if (needsBand) contentTop + reserveTop else contentTop
        val bandBottom = if (needsBand) contentBottom - reserveBottom else contentBottom

        val centerX = clampCenter(
            (contentLeft + contentRight) / 2f + sp(key.keyTextOffsetX),
            fittedWidth,
            contentLeft,
            contentRight,
        )
        val centerY = clampCenter(
            (contentTop + contentBottom) / 2f + sp(key.keyTextOffsetY),
            fittedHeight,
            bandTop,
            bandBottom,
        )

        canvas.drawText(label, centerX, centerY - (metrics.ascent + metrics.descent) / 2f, textPaint)
    }

    private fun topSymbolReserve(): Float {
        if (key.symbolLabel.isEmpty() || keyboardView.hideKeySymbol) return 0f
        return symbolDesiredSize() * 1.2f
    }

    private fun bottomHintReserve(): Float {
        if (key.hint.isEmpty() || keyboardView.hideKeyHint) return 0f
        return symbolDesiredSize() * 1.2f
    }

    private fun symbolDesiredSize(): Float = sp(key.symbolTextSize.takeIf { it > 0f } ?: keyboardView.symbolTextSize)

    private fun clampCenter(value: Float, size: Float, min: Float, max: Float): Float {
        val span = max - min
        if (span <= 0f || size >= span) return (min + max) / 2f
        return value.coerceIn(min + size / 2f, max - size / 2f)
    }

    private fun drawIcon(
        canvas: Canvas,
        iconName: String,
        size: Int,
        color: Int,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        isTop: Boolean? = null,
    ) {
        val maxSize = minOf(contentWidth, contentHeight).toInt()
        val iconSize = size.coerceAtMost(maxSize)
        if (iconSize <= 0) return
        val halfSize = iconSize / 2f

        val cmdName = iconName.toIconName()
        val icon = if (cachedIconName == cmdName) {
            cachedIcon!!
        } else {
            IconicsDrawable(context, cmdName).apply {
                sizeDp = iconSize
            }.also {
                cachedIcon = it
                cachedIconName = cmdName
            }
        }

        icon.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)

        val centerX = clampCenter(
            (contentLeft + contentRight) / 2f + sp(offsetX),
            iconSize.toFloat(),
            contentLeft,
            contentRight,
        )
        val centerY = clampCenter(
            when (isTop) {
                true -> contentTop + halfSize + sp(offsetY)
                false -> contentBottom - halfSize + sp(offsetY)
                null -> (contentTop + contentBottom) / 2f + sp(offsetY)
            },
            iconSize.toFloat(),
            contentTop,
            contentBottom,
        )

        icon.setBounds(
            (centerX - halfSize).toInt(),
            (centerY - halfSize).toInt(),
            (centerX + halfSize).toInt(),
            (centerY + halfSize).toInt(),
        )
        icon.draw(canvas)
    }

    private fun drawSymbol(canvas: Canvas, text: String, isTop: Boolean = true) {
        if (isTop && keyboardView.hideKeySymbol) return
        if (!isTop && keyboardView.hideKeyHint) return

        val textColor = key.getSymbolColor()
        val desiredSize = symbolDesiredSize()
        val offsetX = if (isTop) key.keySymbolOffsetX else key.keyHintOffsetX
        val offsetY = if (isTop) key.keySymbolOffsetY else key.keyHintOffsetY

        if (text.isIconFont) {
            drawIcon(canvas, text, desiredSize.toInt(), textColor, offsetX, offsetY, isTop)
            return
        }

        symbolPaint.apply {
            color = textColor
            typeface = theme.fonts.symbol
            textSize = desiredSize
            textScaleX = 1f
        }

        val lines = text.split("\n")
        val rawWidth = lines.maxOf { symbolPaint.measureText(it) }
        val rawMetrics = symbolPaint.fontMetrics
        val rawLineHeight = rawMetrics.descent - rawMetrics.ascent
        val rawHeight = rawLineHeight * lines.size

        val fit = KeyTextFitter.fit(
            desiredSize = desiredSize,
            textWidth = rawWidth,
            textHeight = rawHeight,
            maxWidth = contentWidth,
            maxHeight = contentHeight,
            minSize = minTextSize,
        )
        symbolPaint.textSize = fit.textSize
        symbolPaint.textScaleX = fit.textScaleX

        val metrics = symbolPaint.fontMetrics
        val lineHeight = metrics.descent - metrics.ascent
        val totalHeight = lineHeight * lines.size
        val maxWidth = lines.maxOf { symbolPaint.measureText(it) }

        val centerX = clampCenter(
            (contentLeft + contentRight) / 2f + sp(offsetX),
            maxWidth,
            contentLeft,
            contentRight,
        )

        var startY = if (isTop) {
            contentTop - metrics.top + sp(offsetY) - (totalHeight - lineHeight) / 2f
        } else {
            contentBottom - metrics.bottom + sp(offsetY) - (totalHeight - lineHeight) / 2f
        }

        if (totalHeight <= contentHeight) {
            val blockTop = startY + metrics.ascent
            if (blockTop < contentTop) {
                startY += contentTop - blockTop
            }
            val adjustedTop = startY + metrics.ascent
            if (adjustedTop + totalHeight > contentBottom) {
                startY -= adjustedTop + totalHeight - contentBottom
            }
        } else {
            startY = contentTop - metrics.ascent
        }

        for (i in lines.indices) {
            canvas.drawText(lines[i], centerX, startY + lineHeight * i, symbolPaint)
        }
    }
}
