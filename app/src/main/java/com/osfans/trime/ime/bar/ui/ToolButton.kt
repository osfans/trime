/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.shapes.OvalShape
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.view.isVisible
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.osfans.trime.R
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.ime.core.AutoScaleTextView
import com.osfans.trime.ime.keyboard.GestureFrame
import com.osfans.trime.ime.keyboard.Keyboard
import com.osfans.trime.util.setCheckedState
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.view
import splitties.views.gravityCenter
import splitties.views.imageDrawable
import splitties.views.imageResource
import splitties.views.padding

// Created programmatically with an input-window theme, never inflated from XML.
@SuppressLint("ViewConstructor")
class ToolButton(context: Context, private val scope: ThemeScope) : GestureFrame(context) {

    private val image = imageView {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        adjustViewBounds = true
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    }

    private val label = view(::AutoScaleTextView) {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isSingleLine = true
        includeFontPadding = false
        gravity = gravityCenter
        scaleMode = AutoScaleTextView.Mode.Horizontal
    }

    var option: String? = null
        private set
    private var optionEnabled = false
        set(value) {
            if (field == value) return
            field = value
            updateContent()
        }

    private var optionStyles: List<String> = listOf()
    private var singleStyle = ""

    private var actionLabel = ""
    private var actionLabelProvider: (() -> String)? = null
    private var config: ToolBar.Button? = null

    /** Navigation buttons may reuse theme visuals while performing a different action. */
    var accessibilityLabel: CharSequence? = null
        set(value) {
            field = value
            updateAccessibilityLabel()
        }

    private var fontSize = 0f
    private var colorStateList: ColorStateList? = null

    constructor(
        context: Context,
        @DrawableRes icon: Int,
        scope: ThemeScope,
    ) : this(context, scope) {
        applyIconTint()
        image.padding = dp(4)
        setIcon(icon)
    }

    constructor(
        context: Context,
        config: ToolBar.Button,
        scope: ThemeScope,
        keyboard: Keyboard,
    ) : this(context, scope) {
        this.config = config
        val keyAction = scope.theme.resolveAction(config.action)
        isRepeatable = keyAction.isRepeatable

        val fg = config.foreground
        val toggle = keyAction.toggle
        option = toggle.takeIf { it.isNotEmpty() }
        if (option != null && fg.optionStyles.size == 2) {
            optionStyles = fg.optionStyles
        } else {
            singleStyle = fg.style
        }
        actionLabel = keyAction.getLabel(keyboard)
        actionLabelProvider = {
            if (RimeDaemon.getFirstSessionOrNull() != null) keyAction.getLabel(keyboard) else actionLabel
        }

        val padding = dp(fg.padding)
        image.padding = padding
        label.padding = padding

        fontSize = fg.fontSize
        label.textSize = fontSize

        label.typeface = scope.theme.fonts.toolbar

        applyConfigColors(config)
    }

    /** Applies the tint of icon-only buttons; re-run on scheme switches. */
    private fun applyIconTint() {
        image.imageTintList =
            ColorStateList.valueOf(scope.colors.candidateTextColor)
    }

    /** Applies scheme-dependent colors; re-run on scheme switches. */
    private fun applyConfigColors(config: ToolBar.Button) {
        val fg = config.foreground
        colorStateList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(
                scope.color(fg.highlight.ifEmpty { "hilited_candidate_text_color" }),
                scope.color(fg.normal.ifEmpty { "candidate_text_color" }),
            ),
        )
        label.setTextColor(colorStateList)
        updateContent()

        val bg = config.background
        background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                backgroundStateDrawable(bg, highlight = true),
            )
            addState(
                intArrayOf(),
                backgroundStateDrawable(bg, highlight = false),
            )
        }
    }

    /** Restyles this button after a scheme switch. */
    fun refreshColors() {
        val cfg = config
        if (cfg == null) {
            applyIconTint()
        } else {
            applyConfigColors(cfg)
        }
    }

    init {
        add(
            label,
            lParams {
                gravity = gravityCenter
            },
        )
        add(
            image,
            lParams {
                gravity = gravityCenter
            },
        )
    }

    fun setIcon(@DrawableRes src: Int) {
        image.imageResource = src
        label.isVisible = false
        image.isVisible = true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = Button::class.java.name
        info.isCheckable = option != null && accessibilityLabel == null
        val checked = info.isCheckable && optionEnabled
        info.setCheckedState(checked)
        info.isSelected = checked
    }

    private fun updateAccessibilityLabel() {
        contentDescription = accessibilityLabel
            ?: actionLabel.takeIf { it.isNotBlank() }
            ?: label.text.takeIf { label.isVisible && it.isNotBlank() }
            ?: context.getString(R.string.a11y_keyboard_options)
        isSelected = accessibilityLabel == null && option != null && optionEnabled
    }

    fun updateStyle(option: String, enabled: Boolean) {
        if (option == this.option) {
            optionEnabled = enabled
        }
    }

    private fun backgroundStateDrawable(bg: ToolBar.Button.Background, highlight: Boolean): Drawable {
        val color = if (highlight) {
            scope.color(bg.highlight.ifEmpty { "hilited_candidate_button_color" })
        } else {
            bg.normal.takeIf { it.isNotEmpty() }?.let(scope::color) ?: 0
        }
        return when (bg.type) {
            ToolBar.Button.Background.Type.RECTANGLE -> GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(bg.cornerRadius)
            }

            ToolBar.Button.Background.Type.CIRCLE -> ShapeDrawable(OvalShape()).apply { paint.color = color }
        }.let {
            val vInset = dp(bg.verticalInset)
            val hInset = dp(bg.horizontalInset)
            LayerDrawable(arrayOf(it)).apply {
                setLayerInset(0, hInset, vInset, hInset, vInset)
            }
        }
    }

    private fun updateContent() {
        actionLabel = actionLabelProvider?.invoke() ?: actionLabel
        val style = if (option != null && optionStyles.size == 2) {
            optionStyles[if (optionEnabled) 1 else 0]
        } else {
            singleStyle
        }

        // drawable: local image or iconics
        var useLocalImage = false
        val drawable = if (IMAGE_PATTERN.matches(style)) {
            useLocalImage = true
            scope.drawable(style)
        } else if (style.startsWith("ic@")) {
            val icon = "cmd_${style.substring(3)}"
            IconicsDrawable(context, icon).apply {
                sizeDp = fontSize.toInt()
            }
        } else {
            null
        }

        if (drawable != null) { // image icon
            image.imageDrawable = drawable
            image.imageTintList = if (!useLocalImage) colorStateList else null
            label.isVisible = false
            image.isVisible = true
        } else { // text icon
            label.text = if (useLocalImage || style.isEmpty()) actionLabel else style
            image.isVisible = false
            label.isVisible = true
        }
        updateAccessibilityLabel()
    }

    companion object {
        private val IMAGE_PATTERN = ".*\\.(png|jpg|gif|webp)$".toRegex()
    }
}
