package com.osfans.trime.settings.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceManager
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
    private var defaultValue: Int = 0
    private var systemDefaultValue: Int = -1
    private var systemDefaultValueText: String? = null
    private var min: Int = 0
    private var max: Int = 100
    private var step: Int = 1
    private var unit: String = ""

    private val currentValue: Int
        get() = sharedPreferences.getInt(key, defaultValue)

    @Suppress("unused")
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, R.attr.preferenceStyle)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        context.obtainStyledAttributes(attrs, R.styleable.DialogSeekBarPreferenceAttrs).apply {
            min = getInt(R.styleable.DialogSeekBarPreferenceAttrs_min, min)
            max = getInt(R.styleable.DialogSeekBarPreferenceAttrs_max, max)
            step = getInt(R.styleable.DialogSeekBarPreferenceAttrs_seekBarIncrement, step)
            if (step < 1) {
                step = 1
            }
            defaultValue = getInt(R.styleable.DialogSeekBarPreferenceAttrs_android_defaultValue, defaultValue)
            systemDefaultValue = getInt(R.styleable.DialogSeekBarPreferenceAttrs_systemDefaultValue, min - 1)
            systemDefaultValueText = getString(R.styleable.DialogSeekBarPreferenceAttrs_systemDefaultValueText)
            unit = getString(R.styleable.DialogSeekBarPreferenceAttrs_unit) ?: unit
            recycle()
        }
    }

    override fun onClick() {
        showSeekBarDialog()
    }

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager?) {
        super.onAttachedToHierarchy(preferenceManager)
        summary = getTextForValue(currentValue)
    }

    /**
     * Generates the text for the given [value] and adds the defined [unit] at the end.
     * If [systemDefaultValueText] is not null this method tries to match the given [value] with
     * [systemDefaultValue] and returns [systemDefaultValueText] upon matching.
     */
    private fun getTextForValue(value: Int): String {
        val systemDefValText = systemDefaultValueText
        return if (value == systemDefaultValue && systemDefValText != null) {
            systemDefValText
        } else {
            value.toString() + unit
        }
    }

    /**
     * Shows the seek bar dialog.
     */
    private fun showSeekBarDialog() {
        val dialogView = SeekBarDialogBinding.inflate(LayoutInflater.from(context))
        val initValue = currentValue
        dialogView.seekBar.max = actualValueToSeekBarProgress(max)
        dialogView.seekBar.progress = actualValueToSeekBarProgress(initValue)
        dialogView.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                dialogView.value.text = getTextForValue(seekBarProgressToActualValue(progress))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        dialogView.value.text = getTextForValue(initValue)
        AlertDialog.Builder(context).apply {
            setTitle(this@DialogSeekBarPreference.title)
            setCancelable(true)
            setView(dialogView.root)
            setPositiveButton(android.R.string.ok) { _, _ ->
                val actualValue = seekBarProgressToActualValue(dialogView.seekBar.progress)
                if (callChangeListener(actualValue)) {
                    sharedPreferences.edit().putInt(key, actualValue).apply()
                    summary = getTextForValue(currentValue)
                }
            }
            setNeutralButton(R.string.pref__default) { _, _ ->
                sharedPreferences.edit().putInt(key, defaultValue).apply()
                summary = getTextForValue(currentValue)
            }
            setNegativeButton(android.R.string.cancel, null)
        }.show()
    }

    /**
     * Converts the actual value to a progress value which the Android SeekBar implementation can
     * handle. (Android's SeekBar step is fixed at 1 and min at 0)
     *
     * @param actual The actual value.
     * @return the internal value which is used to allow different min and step values.
     */
    private fun actualValueToSeekBarProgress(actual: Int): Int {
        return (actual - min) / step
    }

    /**
     * Converts the Android SeekBar value to the actual value.
     *
     * @param progress The progress value of the SeekBar.
     * @return the actual value which is ready to use.
     */
    private fun seekBarProgressToActualValue(progress: Int): Int {
        return (progress * step) + min
    }
}
