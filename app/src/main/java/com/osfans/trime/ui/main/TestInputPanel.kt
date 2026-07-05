/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.widget.TextViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentActivity
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.databinding.TestInputPanelBinding
import splitties.resources.styledColor
import splitties.systemservices.inputMethodManager

class TestInputPanel
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    private val binding: TestInputPanelBinding
    private val activity: FragmentActivity by lazy {
        var ctx: Context = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is FragmentActivity) return@lazy ctx
            ctx = ctx.baseContext
        }
        throw IllegalStateException("TestInputPanel must be inflated with a FragmentActivity context.")
    }
    private var onVisibilityChanged: ((visible: Boolean) -> Unit)? = null

    private data class InputTypeOption(
        val pill: TextView,
        @StringRes val hintRes: Int,
        @DrawableRes val iconRes: Int,
        val inputType: Int,
    )

    private lateinit var options: List<InputTypeOption>

    init {
        orientation = VERTICAL
        setBackgroundResource(R.drawable.bg_test_input_panel)
        val paddingPx = (12 * resources.displayMetrics.density).toInt()
        setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        binding = TestInputPanelBinding.inflate(LayoutInflater.from(context), this)
    }

    fun bind(onVisibilityChanged: (visible: Boolean) -> Unit = {}) {
        this.onVisibilityChanged = onVisibilityChanged

        options =
            listOf(
                InputTypeOption(
                    binding.pillText,
                    R.string.test_input,
                    R.drawable.ic_baseline_keyboard_24,
                    InputType.TYPE_CLASS_TEXT,
                ),
                InputTypeOption(
                    binding.pillNumber,
                    R.string.test_input_number,
                    R.drawable.ic_baseline_numbers_24,
                    InputType.TYPE_CLASS_NUMBER,
                ),
                InputTypeOption(
                    binding.pillPassword,
                    R.string.test_input_password,
                    R.drawable.ic_baseline_lock_24,
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                ),
                InputTypeOption(
                    binding.pillPhone,
                    R.string.test_input_phone,
                    R.drawable.ic_baseline_phone_24,
                    InputType.TYPE_CLASS_PHONE,
                ),
                InputTypeOption(
                    binding.pillEmail,
                    R.string.test_input_email,
                    R.drawable.ic_baseline_email_24,
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                ),
                InputTypeOption(
                    binding.pillUrl,
                    R.string.test_input_url,
                    R.drawable.ic_baseline_link_24,
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
                ),
            )

        options.forEach { option ->
            option.pill.setOnClickListener {
                selectInputType(option, showIme = true)
            }
        }

        binding.testInputClear.setOnClickListener {
            binding.testInput.text = null
        }

        binding.testInput.doAfterTextChanged { text ->
            binding.testInputClear.visibility =
                if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        binding.testInputCollapse.setOnClickListener {
            val currentlyExpanded = binding.testInputContent.visibility == View.VISIBLE
            setExpanded(expanded = !currentlyExpanded)
        }

        binding.testInputHide.setOnClickListener {
            confirmHide()
        }

        binding.testInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                inputMethodManager.hideSoftInputFromWindow(binding.testInput.windowToken, 0)
            }
        }

        binding.testInputTitle.setOnClickListener {
            if (binding.testInputContent.visibility != View.VISIBLE) {
                setExpanded(expanded = true)
            }
        }

        selectInputType(options.first(), showIme = false)

        val prefs = AppPrefs.defaultInstance().general
        if (!prefs.testInputVisible.getValue()) {
            setVisible(visible = false, persist = false)
        } else {
            setExpanded(expanded = prefs.testInputExpanded.getValue(), persist = false)
        }
    }

    fun showExpanded() {
        setVisible(visible = true)
        setExpanded(expanded = true)
    }

    private fun confirmHide() {
        AlertDialog
            .Builder(activity)
            .setTitle(R.string.hide_test_input)
            .setMessage(R.string.hide_test_input_confirm)
            .setPositiveButton(R.string.ok) { _, _ ->
                setVisible(visible = false)
            }.setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setExpanded(
        expanded: Boolean,
        persist: Boolean = true,
    ) {
        binding.testInputContent.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.testInputCollapse.setImageResource(
            if (expanded) {
                R.drawable.ic_baseline_expand_less_24
            } else {
                R.drawable.ic_baseline_expand_more_24
            },
        )
        binding.testInputCollapse.contentDescription =
            activity.getString(
                if (expanded) R.string.collapse_test_input else R.string.expand_test_input,
            )

        if (!expanded && binding.testInput.hasFocus()) {
            inputMethodManager.hideSoftInputFromWindow(binding.testInput.windowToken, 0)
            binding.testInput.clearFocus()
        }

        if (persist) {
            AppPrefs.defaultInstance().general.testInputExpanded.setValue(expanded)
        }
    }

    private fun setVisible(
        visible: Boolean,
        persist: Boolean = true,
    ) {
        visibility = if (visible) View.VISIBLE else View.GONE

        if (!visible) {
            inputMethodManager.hideSoftInputFromWindow(binding.testInput.windowToken, 0)
            binding.testInput.clearFocus()
        }

        onVisibilityChanged?.invoke(visible)

        if (persist) {
            AppPrefs.defaultInstance().general.testInputVisible.setValue(visible)
        }
    }

    private fun selectInputType(
        selected: InputTypeOption,
        showIme: Boolean,
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            option.pill.isSelected = isSelected
            updatePillIcon(option.pill, option.iconRes)
        }

        binding.testInput.inputType = selected.inputType
        binding.testInput.setHint(selected.hintRes)
        updateFieldIcon(selected.iconRes)

        if (showIme) {
            binding.testInput.requestFocus()
            inputMethodManager.showSoftInput(binding.testInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun updatePillIcon(
        pill: TextView,
        @DrawableRes iconRes: Int,
    ) {
        val icon = ContextCompat.getDrawable(context, iconRes)?.mutate()
        pill.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        TextViewCompat.setCompoundDrawableTintList(pill, pill.textColors)
    }

    private fun updateFieldIcon(
        @DrawableRes iconRes: Int,
    ) {
        val icon =
            ContextCompat.getDrawable(context, iconRes)?.mutate()?.also {
                DrawableCompat.setTint(it, context.styledColor(android.R.attr.textColorSecondary))
            }
        binding.testInput.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        binding.testInput.compoundDrawablePadding =
            (8 * resources.displayMetrics.density).toInt()
    }
}
