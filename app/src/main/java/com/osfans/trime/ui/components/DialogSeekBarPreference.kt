// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.osfans.trime.R
import com.osfans.trime.databinding.SeekBarDialogBinding

/**
 * Custom preference which represents a seek bar which shows the current value in the summary. The
 * value can be changed by clicking on the preference, which brings up a dialog which a seek bar.
 * This implementation also allows for a min / max step value, while being backwards compatible.
 *
 * @see R.styleable.DialogSeekBarPreferenceAttrs for which xml attributes this preference accepts
 *  besides the default Preference attributes.
 *
 * @property defaultValue The default value of this preference.
 * @property min The minimum value of the seek bar. Must not be greater or equal than [max].
 * @property max The maximum value of the seek bar. Must not be lesser or equal than [min].
 * @property step The step in which the seek bar increases per move. If the provided value is less
 *  than 1, 1 will be used as step. Note that the xml attribute's name for this property is
 *  [R.styleable.DialogSeekBarPreferenceAttrs_seekBarIncrement].
 * @property unit The unit to show after the value. Set to an empty string to disable this feature.
 */
class DialogSeekBarPreference : Preference {
    var defaultValue: Int
    var systemDefaultValue: Int
    var systemDefaultValueText: String
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
                defaultValue =
                    getInt(R.styleable.DialogSeekBarPreferenceAttrs_android_defaultValue, 0)
                systemDefaultValue =
                    getInt(R.styleable.DialogSeekBarPreferenceAttrs_systemDefaultValue, -1)
                systemDefaultValueText =
                    getString(R.styleable.DialogSeekBarPreferenceAttrs_systemDefaultValueText) ?: ""
                unit = getString(R.styleable.DialogSeekBarPreferenceAttrs_unit) ?: ""
                if (getBoolean(R.styleable.DialogSeekBarPreferenceAttrs_useSimpleSummaryProvider, false)) {
                    summaryProvider = SimpleSummaryProvider
                }
            } finally {
                recycle()
            }
        }
    }

    private val currentValue: Int
        get() = getPersistedInt(defaultValue)

    override fun onClick() {
        showSeekBarDialog()
    }

    /**
     * Generates the text for the given [value] and adds the defined [unit] at the end.
     * If [systemDefaultValueText] is not null this method tries to match the given [value] with
     * [systemDefaultValue] and returns [systemDefaultValueText] upon matching.
     */
    private fun getTextForValue(value: Int): String =
        if (value == systemDefaultValue && systemDefaultValueText.isNotEmpty()) {
            systemDefaultValueText
        } else {
            "$value $unit"
        }

    /**
     * Shows the seek bar dialog.
     */
    private fun showSeekBarDialog() =
        with(context) {
            val initValue = currentValue
            val dialogView = SeekBarDialogBinding.inflate(LayoutInflater.from(this))
            dialogView.textView.text = getTextForValue(initValue)
            dialogView.seekBar.apply {
                max = getProgressForValue(this@DialogSeekBarPreference.max)
                progress = getProgressForValue(initValue)
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean,
                        ) {
                            dialogView.textView.text = getTextForValue(getValueForProgress(progress))
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                    },
                )
            }
            AlertDialog
                .Builder(this)
                .setTitle(this@DialogSeekBarPreference.title)
                .setView(dialogView.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val actualValue = getValueForProgress(dialogView.seekBar.progress)
                    if (callChangeListener(actualValue)) {
                        persistInt(actualValue)
                        notifyChanged()
                    }
                }.setNeutralButton(R.string.pref__default) { _, _ ->
                    persistInt(defaultValue)
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
        override fun provideSummary(preference: DialogSeekBarPreference): CharSequence = preference.getTextForValue(preference.currentValue)
    }
}
