// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.sizeDp
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.FontManager
import com.osfans.trime.data.theme.KeyActionManager
import com.osfans.trime.data.theme.model.ToolBar
import com.osfans.trime.ime.keyboard.GestureFrame
import com.osfans.trime.ime.keyboard.KeyboardSwitcher
import com.osfans.trime.util.circlePressHighlightDrawable
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.imageDrawable
import splitties.views.imageResource
import splitties.views.padding

class ToolButton : GestureFrame {
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

    enum class ContentType {
        ICON,
        TEXT,
        LOCAL_IMAGE,
    }

    var contentType = ContentType.TEXT
        private set
    private var config: ToolBar.Button? = null
    private var toggleKey: String? = null

    constructor(context: Context, @DrawableRes icon: Int) : super(context) {
        setupContent(ContentType.ICON, icon = icon)
    }

    constructor(context: Context, config: ToolBar.Button) : super(context) {
        initFromConfig(config)
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

    fun setIconTint(@ColorInt color: Int?) {
        image.imageTintList = ColorStateList.valueOf(color ?: ColorManager.getColor("candidate_text_color"))
    }

    fun updateStyle() {
        val config = this.config ?: return

        if (needsStyleUpdate()) {
            removeAllViews()
            setupFromConfig(config)
        }
    }

    private fun needsStyleUpdate(): Boolean = toggleKey != null ||
        !config?.foreground?.style.isNullOrEmpty() ||
        !config?.foreground?.optionStyles.isNullOrEmpty()

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
                if (icon != null) {
                    image.imageResource = icon
                } else if (!text.isNullOrEmpty()) {
                    image.imageDrawable = IconicsDrawable(context, text).apply {
                        sizeDp = foreground?.fontSize?.toInt() ?: 12
                    }
                }
                image.padding = dp(foreground?.padding ?: 10)
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
        val normalColor = foreground?.fgNormal?.takeIf { it.isNotEmpty() }?.let(ColorManager::getColor)
            ?: ColorManager.getColor("candidate_text_color")

        val colorStateList = createColorStateList(foreground, normalColor)

        when (contentType) {
            ContentType.ICON -> image.imageTintList = colorStateList
            ContentType.TEXT -> label.setTextColor(colorStateList)
            ContentType.LOCAL_IMAGE -> image.imageTintList = null
        }
    }

    private fun createColorStateList(
        foreground: ToolBar.Button.Foreground?,
        normalColor: Int,
    ): ColorStateList = foreground?.fgHighlight?.takeIf { it.isNotEmpty() }?.let { highlight ->
        ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(ColorManager.getColor(highlight), normalColor),
        )
    } ?: ColorStateList.valueOf(normalColor)

    private fun initFromConfig(config: ToolBar.Button) {
        this.config = config
        val toggle = KeyActionManager.getAction(config.action).toggle

        if (toggle.isNotEmpty() && config.foreground?.optionStyles?.size == 2) {
            this.toggleKey = toggle
        }

        setupFromConfig(config)
    }

    private fun getActiveStyle(config: ToolBar.Button): String {
        val optionStyles = config.foreground?.optionStyles
        val style = config.foreground?.style

        if (toggleKey != null && optionStyles?.size == 2) {
            val rime = RimeDaemon.getFirstSessionOrNull()
            val isOptionOn = rime?.run { getRuntimeOption(toggleKey!!) } ?: false
            return if (isOptionOn) optionStyles[1] else optionStyles[0]
        }

        return style ?: ""
    }

    private fun setupFromConfig(config: ToolBar.Button) {
        val style = getActiveStyle(config)
        val foreground = config.foreground

        when {
            style.matches(IMAGE_PATTERN) -> {
                ColorManager.getDrawable(style)?.let { drawable ->
                    setupContent(ContentType.LOCAL_IMAGE, drawable = drawable, foreground = foreground)
                } ?: setupFallbackContent(config, foreground)
            }
            style.isNotEmpty() -> {
                if (style.startsWith("ic@")) {
                    val iconName = style.replace("ic@", "cmd_")
                    setupContent(ContentType.ICON, text = iconName, foreground = foreground)
                } else {
                    setupContent(ContentType.TEXT, text = style, foreground = foreground)
                }
            }
            else -> {
                setupFallbackContent(config, foreground)
            }
        }

        setupBackground(config.background)
    }

    private fun setupFallbackContent(
        config: ToolBar.Button?,
        foreground: ToolBar.Button.Foreground?,
    ) {
        val action = config?.action ?: ""
        val fallbackText = KeyActionManager.getAction(action)
            .getLabel(KeyboardSwitcher.currentKeyboard)

        setupContent(ContentType.TEXT, text = fallbackText, foreground = foreground)
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

        fun getContentType(style: String?): ContentType = when {
            style.isNullOrEmpty() -> ContentType.TEXT
            style.matches(IMAGE_PATTERN) -> ContentType.LOCAL_IMAGE
            style.startsWith("ic@") -> ContentType.ICON
            else -> ContentType.TEXT
        }
    }
}
