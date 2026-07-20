/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.text.InputType
import android.util.AttributeSet
import android.view.Window
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.core.widget.TextViewCompat
import androidx.core.widget.doAfterTextChanged
import com.osfans.trime.R
import splitties.dimensions.dp
import splitties.resources.drawable
import splitties.resources.styledColor
import splitties.resources.styledDrawable
import splitties.systemservices.inputMethodManager
import splitties.views.dsl.constraintlayout.after
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.editText
import splitties.views.dsl.core.endMargin
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.horizontalLayout
import splitties.views.dsl.core.imageButton
import splitties.views.dsl.core.imageView
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.wrapContent
import splitties.views.gravityCenter
import splitties.views.gravityEndCenter
import splitties.views.gravityVerticalCenter
import splitties.views.imageDrawable
import splitties.views.setPaddingDp

class TestInputPanel
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    data class InputTypeOption(
        @StringRes val label: Int,
        @StringRes val hint: Int,
        @DrawableRes val icon: Int,
        val inputType: Int,
    )

    inner class PillUi(val index: Int) : Ui {
        override val ctx: Context = this@TestInputPanel.context

        val option: InputTypeOption
            get() = inputTypeOptions[index]

        override val root = textView {
            background = StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_selected),
                    GradientDrawable().apply {
                        setColor(styledColor(android.R.attr.colorControlHighlight))
                        cornerRadius = dp(6f)
                    },
                )
                addState(
                    intArrayOf(),
                    GradientDrawable().apply {
                        setStroke(dp(1), styledColor(android.R.attr.colorControlNormal))
                        cornerRadius = dp(6f)
                    },
                )
            }
            compoundDrawablePadding = dp(6)
            gravity = gravityCenter
            isClickable = true
            isFocusable = true
            textSize = 13f
            setPaddingDp(8, 4, 8, 4)
            setText(option.label)
            val imageDrawable = ctx.drawable(option.icon)!!.apply {
                setTintList(textColors)
            }
            setCompoundDrawablesRelativeWithIntrinsicBounds(imageDrawable, null, null, null)

            setOnClickListener {
                selectInputType(this@PillUi)
            }
        }

        fun setActive(active: Boolean) {
            root.isSelected = active
            TextViewCompat.setCompoundDrawableTintList(root, root.textColors)
        }
    }

    private var selected = -1

    private val inputTypeOptions = listOf(
        InputTypeOption(
            R.string.text,
            R.string.type_text,
            R.drawable.ic_baseline_text_fields_24,
            InputType.TYPE_CLASS_TEXT,
        ),
        InputTypeOption(
            R.string.number,
            R.string.type_number,
            R.drawable.ic_baseline_numbers_24,
            InputType.TYPE_CLASS_NUMBER,
        ),
        InputTypeOption(
            R.string.password,
            R.string.type_password,
            R.drawable.ic_baseline_lock_24,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
        ),
    )

    private val pills = Array(inputTypeOptions.size) {
        PillUi(it).apply {
            setActive(false)
        }
    }

    private val header = horizontalLayout {
        add(
            imageView {
                imageDrawable = drawable(R.drawable.ic_input_box)!!.apply {
                    setTint(styledColor(android.R.attr.colorControlNormal))
                }
            },
            lParams(dp(20), dp(20)) {
                gravity = gravityVerticalCenter
            },
        )
        add(
            textView {
                gravity = gravityVerticalCenter
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setText(R.string.test_input)
                setTextColor(styledColor(android.R.attr.textColorPrimary))
            },
            lParams(0, wrapContent, weight = 1f) {
                gravity = gravityVerticalCenter
                marginStart = dp(8)
            },
        )
        add(
            imageButton {
                background = styledDrawable(android.R.attr.selectableItemBackgroundBorderless)
                imageDrawable = drawable(R.drawable.ic_outline_cancel_24)!!.apply {
                    setTint(styledColor(android.R.attr.colorControlNormal))
                }
                setPaddingDp(6)
                setOnClickListener { dismiss() }
            },
            lParams(dp(36), dp(36)) {
                gravity = gravityVerticalCenter
            },
        )
    }

    private val input: EditText = editText {
        background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                GradientDrawable().apply {
                    setStroke(dp(2), styledColor(android.R.attr.colorAccent))
                    cornerRadius = dp(6f)
                },
            )
            addState(
                intArrayOf(),
                GradientDrawable().apply {
                    setStroke(dp(1), styledColor(android.R.attr.colorControlNormal))
                    cornerRadius = dp(6f)
                },
            )
        }
        isCursorVisible = true
        isFocusable = true
        isFocusableInTouchMode = true
        inputType = InputType.TYPE_CLASS_TEXT
        minimumHeight = dp(48)
        textSize = 16f
        setPadding(dp(14))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
        }
    }.apply {
        doAfterTextChanged {
            clearButton.isVisible = !text.isNullOrEmpty()
        }
    }

    private val clearButton: ImageButton = imageButton {
        background = styledDrawable(android.R.attr.selectableItemBackgroundBorderless)
        imageDrawable = drawable(R.drawable.ic_outline_cancel_24)!!.apply {
            setTint(styledColor(android.R.attr.colorControlNormal))
        }
        setPadding(dp(8))
        isVisible = false
    }.apply {
        setOnClickListener {
            input.text.clear()
        }
    }

    private val content = verticalLayout {
        add(
            constraintLayout {
                pills.forEachIndexed { i, pillUi ->
                    add(
                        pillUi.root,
                        lParams(matchConstraints, wrapContent) {
                            centerVertically()
                            if (i == 0) startOfParent() else after(pills[i - 1].root, dp(8))
                            if (i == pills.size - 1) endOfParent() else before(pills[i + 1].root)
                        },
                    )
                }
            },
            lParams(matchParent, wrapContent),
        )
        add(
            frameLayout {
                add(input, lParams(matchParent, wrapContent))
                add(
                    clearButton,
                    lParams(dp(40), dp(40)) {
                        gravity = gravityEndCenter
                        endMargin = dp(4)
                    },
                )
            },
            lParams(matchParent, wrapContent) {
                topMargin = dp(10)
            },
        )
    }

    init {
        elevation = dp(4f)
        orientation = VERTICAL
        setPadding(dp(12))
        background = GradientDrawable().apply {
            val r = dp(8f)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            setColor(styledColor(android.R.attr.colorBackground))
        }
        add(
            header,
            lParams(matchParent, wrapContent) {
                gravity = gravityCenter
            },
        )
        add(
            content,
            lParams(matchParent, wrapContent) {
                topMargin = dp(8)
            },
        )
        selectInputType(pills[0])
    }

    fun show(window: Window) {
        if (isVisible) return
        alpha = 1f
        isVisible = true
        input.text.clear()
        input.requestFocus()
        // `inputMethodManager.showSoftInput` doesn't take effect on Android 11 or above
        WindowCompat.getInsetsController(window, input).show(WindowInsetsCompat.Type.ime())
    }

    fun dismiss() {
        if (!isVisible) return
        input.clearFocus()
        animate().alpha(0f)
            .setDuration(200L)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isVisible = false
                }
            })
        inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun selectInputType(pillUi: PillUi) {
        val index = pillUi.index
        if (index == selected) return
        if (selected >= 0) {
            pills[selected].setActive(false)
        }
        pillUi.setActive(true)
        selected = index

        val option = pillUi.option
        input.inputType = option.inputType
        input.setHint(option.hint)
        updateFieldIcon(option.icon)
    }

    private fun updateFieldIcon(
        @DrawableRes iconRes: Int,
    ) {
        val icon = drawable(iconRes)!!.apply {
            setTint(styledColor(android.R.attr.textColorSecondary))
        }
        input.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        input.compoundDrawablePadding = dp(8)
    }
}
