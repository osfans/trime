// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.osfans.trime.R
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.ime.keyboard.KeyboardSwitcher
import com.osfans.trime.util.circlePressHighlightDrawable
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.imageResource
import splitties.views.padding

class ToolButton : FrameLayout {
    private val image = imageView {
        isClickable = false
        isFocusable = false
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = true
    }

    private val label = textView {
        isClickable = false
        isFocusable = false
        setSingleLine()
        ellipsize = null
    }

    private enum class ContentType {
        ICON,
        TEXT,
        LOCAL_IMAGE,
    }

    private var contentType = ContentType.TEXT
    private var config: ToolBar.Button? = null
    private var buttonName: String? = null
    private var toggleKey: String? = null

    constructor(context: Context, @DrawableRes icon: Int) : super(context) {
        setupContent(ContentType.ICON, icon = icon)
    }

    constructor(context: Context, text: String) : super(context) {
        setupContent(ContentType.TEXT, text = text)
    }

    constructor(context: Context, config: ToolBar.Button, buttonName: String) : super(context) {
        initFromConfig(config, buttonName)
    }

    fun setIcon(@DrawableRes icon: Int) {
        image.imageResource = icon
    }

    fun setText(text: String) {
        label.text = text
    }

    fun setTextColor(@ColorInt color: Int) {
        label.setTextColor(color)
    }

    fun setIconTint(@ColorInt color: Int) {
        image.imageTintList = ColorStateList.valueOf(color)
    }

    fun updateStyle() {
        val config = this.config ?: return
        val buttonName = this.buttonName ?: return

        if (needsStyleUpdate()) {
            removeAllViews()
            setupFromConfig(config, buttonName)
        }
    }

    private fun needsStyleUpdate(): Boolean = toggleKey != null || config?.foreground?.style?.firstOrNull()?.isEmpty() != false

    private fun setupContent(
        type: ContentType,
        @DrawableRes icon: Int? = null,
        text: String? = null,
        drawable: android.graphics.drawable.Drawable? = null,
        foreground: ToolBar.Button.Foreground? = null,
    ) {
        contentType = type
        removeAllViews()

        when (type) {
            ContentType.ICON -> {
                icon?.let { image.imageResource = it }
                foreground?.padding?.let { image.padding = dp(it) } ?: run { image.padding = dp(10) }
                add(image, lParams(wrapContent, wrapContent, gravityCenter))
            }
            ContentType.TEXT -> {
                text?.let { label.text = it }
                foreground?.fontSize?.let { label.textSize = it }
                foreground?.padding?.let { label.padding = dp(it) }
                label.typeface = FontManager.getTypeface("toolbar_font")
                add(label, lParams(wrapContent, wrapContent, gravityCenter))
            }
            ContentType.LOCAL_IMAGE -> {
                drawable?.let { image.setImageDrawable(it) }
                foreground?.padding?.let { image.padding = dp(it) }
                add(image, lParams(wrapContent, wrapContent, gravityCenter))
            }
        }

        applyColors(foreground)
    }

    private fun applyColors(foreground: ToolBar.Button.Foreground?) {
        val normalColor = foreground?.bgNormal?.takeIf { it.isNotEmpty() }?.let(ColorManager::getColor)
            ?: ColorManager.getColor("candidate_text_color")

        val colorStateList = createColorStateList(foreground, normalColor)

        when (contentType) {
            ContentType.ICON -> image.imageTintList = colorStateList
            ContentType.TEXT -> label.setTextColor(colorStateList)
            ContentType.LOCAL_IMAGE -> { /* 本地图片不进行着色 */ }
        }
    }

    private fun createColorStateList(
        foreground: ToolBar.Button.Foreground?,
        normalColor: Int,
    ): ColorStateList = foreground?.bgHighlight?.takeIf { it.isNotEmpty() }?.let { highlight ->
        ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(ColorManager.getColor(highlight), normalColor),
        )
    } ?: ColorStateList.valueOf(normalColor)

    private fun initFromConfig(config: ToolBar.Button, buttonName: String) {
        this.config = config
        this.buttonName = buttonName

        val action = config.action.takeIf { it.isNotEmpty() } ?: buttonName
        val toggle = KeyActionManager.getAction(action).toggle

        if (toggle.isNotEmpty() && config.foreground?.style?.size == 2) {
            this.toggleKey = toggle
        }

        setupFromConfig(config, buttonName)
    }

    private fun getActiveStyle(config: ToolBar.Button): String {
        val styles = config.foreground?.style ?: return ""

        if (toggleKey != null && styles.size == 2) {
            val rime = RimeDaemon.getFirstSessionOrNull()
            val isOptionOn = rime?.run { getRuntimeOption(toggleKey!!) } ?: false
            return if (isOptionOn) styles[0] else styles[1]
        }

        return styles.firstOrNull() ?: ""
    }

    private fun setupFromConfig(config: ToolBar.Button, buttonName: String) {
        val style = getActiveStyle(config)
        val foreground = config.foreground

        when {
            style.startsWith("_") -> {
                getBuiltinIconResource(style)?.let { iconRes ->
                    setupContent(ContentType.ICON, icon = iconRes, foreground = foreground)
                } ?: setupFallbackContent(config, buttonName, foreground)
            }
            style.matches(IMAGE_PATTERN) -> {
                ColorManager.getDrawable(style)?.let { drawable ->
                    setupContent(ContentType.LOCAL_IMAGE, drawable = drawable, foreground = foreground)
                } ?: setupFallbackContent(config, buttonName, foreground)
            }
            style.isNotEmpty() -> {
                setupContent(ContentType.TEXT, text = style, foreground = foreground)
            }
            else -> {
                setupFallbackContent(config, buttonName, foreground)
            }
        }

        setupBackground(config.background)
    }

    private fun setupFallbackContent(
        config: ToolBar.Button?,
        buttonName: String?,
        foreground: ToolBar.Button.Foreground?,
    ) {
        val action = config?.action?.takeIf { it.isNotEmpty() } ?: buttonName ?: ""
        val fallbackText = KeyActionManager.getAction(action)
            .getLabel(KeyboardSwitcher.currentKeyboard)
            .takeIf { it.isNotEmpty() } ?: buttonName ?: ""

        setupContent(ContentType.TEXT, text = fallbackText, foreground = foreground)
    }

    private fun getBuiltinIconResource(style: String): Int? = when (style) {
        "_hide" -> R.drawable.ic_baseline_arrow_drop_down_24
        "_more" -> R.drawable.ic_baseline_more_horiz_24
        "_assignment" -> R.drawable.ic_baseline_assignment_24
        else -> null
    }

    private fun setupBackground(background: ToolBar.Button.Background?) {
        val highlightColor = getHighlightColor(background)

        when (background?.type) {
            "rectangle" -> setRectangleBackground(background, highlightColor)
            else -> setPressHighlightColor(highlightColor)
        }
    }

    private fun getHighlightColor(background: ToolBar.Button.Background?): Int = background?.bgHighlight?.takeIf { it.isNotEmpty() }?.let(ColorManager::getColor)
        ?: ColorManager.getColor("hilited_candidate_button_color")

    private fun setRectangleBackground(background: ToolBar.Button.Background, highlightColor: Int) {
        val normalColor = background.bgNormal.takeIf { it.isNotEmpty() }?.let(ColorManager::getColor) ?: 0
        val cornerRadius = dp(background.cornerRadius.toInt()).toFloat()
        val vInset = dp(background.verticalInset)
        val hInset = dp(background.horizontalInset)

        val stateListDrawable = StateListDrawable().apply {
            listOf(
                intArrayOf(android.R.attr.state_pressed) to highlightColor,
                intArrayOf() to normalColor,
            ).forEach { (state, color) ->
                val drawable = createRoundedDrawable(color, cornerRadius)
                val layerDrawable = LayerDrawable(arrayOf(drawable)).apply {
                    setLayerInset(0, hInset, vInset, hInset, vInset)
                }
                addState(state, layerDrawable)
            }
        }

        this.background = stateListDrawable
    }

    private fun createRoundedDrawable(color: Int, cornerRadius: Float) = GradientDrawable().apply {
        setColor(color)
        this.cornerRadius = cornerRadius
    }

    private fun setPressHighlightColor(@ColorInt color: Int) {
        this.background = circlePressHighlightDrawable(color)
    }

    companion object {
        private val IMAGE_PATTERN = ".*\\.(png|jpg|gif|webp)$".toRegex()
    }
}
