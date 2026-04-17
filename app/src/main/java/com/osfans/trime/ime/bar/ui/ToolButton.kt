/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.bar.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.shapes.OvalShape
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
        ICON_ONLY,
    }

    var contentType = ContentType.TEXT
        private set
    private lateinit var config: ToolBar.Button
    private var toggleKey: String? = null

    constructor(context: Context, @DrawableRes icon: Int) : super(context) {
        config = ToolBar.Button()
        setupContent(ContentType.ICON_ONLY, icon = icon)
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
        if (needsStyleUpdate()) {
            removeAllViews()
            setupFromConfig()
        }
    }

    private fun needsStyleUpdate(): Boolean = toggleKey != null ||
        config.foreground.style.isNotEmpty() ||
        config.foreground.optionStyles.isNotEmpty()

    private fun setupContent(
        type: ContentType,
        @DrawableRes icon: Int? = null,
        text: String? = null,
        drawable: android.graphics.drawable.Drawable? = null,
    ) {
        val foreground = config.foreground
        contentType = type
        removeAllViews()

        when (type) {
            ContentType.ICON -> text?.let {
                image.imageDrawable = IconicsDrawable(context, it).apply {
                    sizeDp = foreground.fontSize.toInt()
                }
            }
            ContentType.TEXT -> {
                text?.let { label.text = it }
                label.textSize = foreground.fontSize
                label.padding = dp(foreground.padding)
                label.typeface = FontManager.getTypeface("toolbar_font")
                add(label, lParams(wrapContent, wrapContent, gravityCenter))
            }
            ContentType.LOCAL_IMAGE -> drawable?.let { image.setImageDrawable(it) }
            ContentType.ICON_ONLY -> icon?.let { image.imageResource = it }
        }

        if (type != ContentType.TEXT) {
            image.padding = dp(foreground.padding)
            add(image, lParams(wrapContent, wrapContent, gravityCenter))
        }

        applyColors()
    }

    private fun applyColors() {
        val foreground = config.foreground
        val normalColor = foreground.normal.takeIf { it.isNotEmpty() }
            ?.let(ColorManager::getColor) ?: ColorManager.getColor("candidate_text_color")

        val highlightColor = foreground.highlight.takeIf { it.isNotEmpty() }
            ?.let(ColorManager::getColor) ?: ColorManager.getColor("hilited_candidate_text_color")
        val colorStateList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(highlightColor, normalColor),
        )

        when (contentType) {
            ContentType.ICON -> image.imageTintList = colorStateList
            ContentType.TEXT -> label.setTextColor(colorStateList)
            ContentType.LOCAL_IMAGE -> image.imageTintList = null
            ContentType.ICON_ONLY -> image.imageTintList = ColorStateList.valueOf(normalColor)
        }
    }

    private fun initFromConfig(config: ToolBar.Button) {
        this.config = config
        val keyAction = KeyActionManager.getAction(config.action)
        val toggle = keyAction.toggle

        if (toggle.isNotEmpty() && config.foreground.optionStyles.size == 2) {
            this.toggleKey = toggle
        }

        isRepeatable = keyAction.isRepeatable
        setupFromConfig()
    }

    private fun getActiveStyle(): String {
        val optionStyles = config.foreground.optionStyles
        if (toggleKey != null && optionStyles.size == 2) {
            val rime = RimeDaemon.getFirstSessionOrNull()!!
            val toggleOn = rime.run { getRuntimeOption(toggleKey!!) }
            return optionStyles[if (toggleOn) 1 else 0]
        }
        return config.foreground.style
    }

    private fun setupFromConfig() {
        val style = getActiveStyle()

        when {
            style.matches(IMAGE_PATTERN) -> {
                ColorManager.getDrawable(style)?.let { setupContent(ContentType.LOCAL_IMAGE, drawable = it) }
                    ?: setupFallbackContent(config)
            }
            style.isNotEmpty() -> {
                val type = if (style.startsWith("ic@")) ContentType.ICON else ContentType.TEXT
                val text = if (style.startsWith("ic@")) style.replace("ic@", "cmd_") else style
                setupContent(type, text = text)
            }
            else -> setupFallbackContent(config)
        }

        val bg = config.background
        val normalColor = bg.normal.takeIf { it.isNotEmpty() }?.let(ColorManager::getColor) ?: 0
        val highlightColor = bg.highlight.takeIf { it.isNotEmpty() }?.let(ColorManager::getColor)
            ?: ColorManager.getColor("hilited_candidate_button_color")

        val vInset = dp(bg.verticalInset)
        val hInset = dp(bg.horizontalInset)
        background = StateListDrawable().apply {
            listOf(
                highlightColor to intArrayOf(android.R.attr.state_pressed),
                normalColor to intArrayOf(),
            ).forEach { (color, state) ->
                val shape = when (bg.type) {
                    "rectangle" -> GradientDrawable().apply {
                        setColor(color)
                        cornerRadius = dp(bg.cornerRadius.toInt()).toFloat()
                    }
                    "circle" -> ShapeDrawable(OvalShape()).apply { paint.color = color }
                    else -> return@forEach
                }
                addState(state, LayerDrawable(arrayOf(shape)).apply { setLayerInset(0, hInset, vInset, hInset, vInset) })
            }
        }
    }

    private fun setupFallbackContent(config: ToolBar.Button) {
        val action = KeyActionManager.getAction(config.action).getLabel(KeyboardSwitcher.currentKeyboard)
        setupContent(ContentType.TEXT, text = action)
    }

    companion object {
        private val IMAGE_PATTERN = ".*\\.(png|jpg|gif|webp)$".toRegex()

        fun getContentType(style: String): ContentType = when {
            style.isEmpty() -> ContentType.TEXT
            style.matches(IMAGE_PATTERN) -> ContentType.LOCAL_IMAGE
            style.startsWith("ic@") -> ContentType.ICON
            else -> ContentType.TEXT
        }
    }
}
