/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.preference.DialogPreference
import com.osfans.trime.R
import splitties.dimensions.dp
import splitties.resources.resolveThemeAttribute
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalMargin
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.seekBar
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.verticalMargin
import splitties.views.gravityHorizontalCenter
import splitties.views.textAppearance

/**
 * Custom preference which represents a seek bar which shows the current value in the summary. The
 * value can be changed by clicking on the preference, which brings up a dialog which a seek bar.
 * This implementation also allows for a min / max step value, while being backwards compatible.
 *
 * @see R.styleable.DialogSeekBarPreferenceAttrs for which xml attributes this preference accepts
 *  besides the default Preference attributes.
 *
 * @property default The default value of this preference.
 * @property min The minimum value of the seek bar. Must not be greater or equal than [max].
 * @property max The maximum value of the seek bar. Must not be lesser or equal than [min].
 * @property step The step in which the seek bar increases per move. If the provided value is less
 *  than 1, 1 will be used as step. Note that the xml attribute's name for this property is
 *  [R.styleable.DialogSeekBarPreferenceAttrs_seekBarIncrement].
 * @property unit The unit to show after the value. Set to an empty string to disable this feature.
 */
class DialogSeekBarPreference : DialogPreference {
    private var value = 0
    var default: Int
    var systemDefaultText: String? = null
    var min: Int
    var max: Int
    var step: Int
    var unit: String

    @Suppress("unused")
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) :
        this(context, attrs, androidx.preference.R.attr.preferenceStyle)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        context.theme.obtainStyledAttributes(attrs, R.styleable.DialogSeekBarPreferenceAttrs, 0, 0).run {
            try {
                min = getInt(R.styleable.DialogSeekBarPreferenceAttrs_min, 0)
                max = getInt(R.styleable.DialogSeekBarPreferenceAttrs_max, 100)
                step = getInt(R.styleable.DialogSeekBarPreferenceAttrs_seekBarIncrement, 1)
                default =
                    getInt(R.styleable.DialogSeekBarPreferenceAttrs_android_defaultValue, 0)
                systemDefaultText =
                    getString(R.styleable.DialogSeekBarPreferenceAttrs_systemDefaultValueText)
                unit = getString(R.styleable.DialogSeekBarPreferenceAttrs_unit) ?: ""
                if (getBoolean(R.styleable.DialogSeekBarPreferenceAttrs_useSimpleSummaryProvider, false)) {
                    summaryProvider = SimpleSummaryProvider
                }
            } finally {
                recycle()
            }
        }
    }

    override fun persistInt(value: Int): Boolean = super.persistInt(value).also {
        if (it) this@DialogSeekBarPreference.value = value
    }

    override fun setDefaultValue(defaultValue: Any?) {
        super.setDefaultValue(defaultValue)
        default = defaultValue as? Int ?: 0
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any = a.getInteger(index, default)

    override fun onSetInitialValue(defaultValue: Any?) {
        value = getPersistedInt(defaultValue as? Int ?: default)
    }

    override fun onClick() {
        showSeekBarDialog()
    }

    /**
     * Generates the text for the given [value] and adds the defined [unit] at the end.
     * If [systemDefaultText] is not null this method tries to match the given [value] with
     * [default] and returns [systemDefaultText] upon matching.
     */
    private fun getTextForValue(value: Int): String = if (value == default && systemDefaultText != null) {
        systemDefaultText!!
    } else {
        "$value $unit"
    }

    /**
     * Shows the seek bar dialog.
     */
    private fun showSeekBarDialog() = with(context) {
        val textView = textView {
            text = getTextForValue(value)
            textAppearance = resolveThemeAttribute(android.R.attr.textAppearanceListItem)
        }
        val seekBar = seekBar {
            max = getProgressForValue(this@DialogSeekBarPreference.max)
            progress = getProgressForValue(value)
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean,
                    ) {
                        textView.text = getTextForValue(getValueForProgress(progress))
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                },
            )
        }
        val dialogView = verticalLayout {
            gravity = gravityHorizontalCenter
            if (dialogMessage != null) {
                val messageText = textView { text = dialogMessage }
                add(
                    messageText,
                    lParams {
                        verticalMargin = dp(8)
                        horizontalMargin = dp(24)
                    },
                )
            }
            add(
                textView,
                lParams {
                    verticalMargin = dp(24)
                },
            )
            add(
                seekBar,
                lParams {
                    width = matchParent
                    horizontalMargin = dp(10)
                    bottomMargin = dp(10)
                },
            )
        }
        AlertDialog
            .Builder(this)
            .setTitle(this@DialogSeekBarPreference.title)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val actualValue = getValueForProgress(seekBar.progress)
                if (callChangeListener(actualValue)) {
                    persistInt(actualValue)
                    notifyChanged()
                }
            }.setNeutralButton(R.string.default_) { _, _ ->
                persistInt(default)
                notifyChanged()
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Converts the actual value to a progress value which the Android SeekBar implementation can
     * handle. (Android's SeekBar step is fixed at 1 and min at 0)
     *
     * @param value The actual value.
     * @return the internal value which is used to allow different min and step values.
     */
    private fun getProgressForValue(value: Int) = (value - min) / step

    /**
     * Converts the Android SeekBar value to the actual value.
     *
     * @param progress The progress value of the SeekBar.
     * @return the actual value which is ready to use.
     */
    private fun getValueForProgress(progress: Int) = (progress * step) + min

    object SimpleSummaryProvider : SummaryProvider<DialogSeekBarPreference> {
        override fun provideSummary(preference: DialogSeekBarPreference): CharSequence = preference.getTextForValue(preference.value)
    }
}
