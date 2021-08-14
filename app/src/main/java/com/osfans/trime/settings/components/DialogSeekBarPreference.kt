package com.osfans.trime.settings.components

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.osfans.trime.R
import com.osfans.trime.databinding.PreferenceWidgetSeekbarBinding

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
    private var min: Int = 0
    private var max: Int = 100
    private var step: Int = 1
    private var unit: String = ""

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
            unit = getString(R.styleable.DialogSeekBarPreferenceAttrs_unit) ?: unit
            recycle()
        }
        onPreferenceChangeListener = OnPreferenceChangeListener { _, newValue ->
            summary = getTextForValue(newValue.toString())
            true
        }
        onPreferenceClickListener = OnPreferenceClickListener {
            showSeekBarDialog()
            true
        }
    }

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager?) {
        super.onAttachedToHierarchy(preferenceManager)
        summary = getTextForValue(sharedPreferences.getInt(key, defaultValue))
    }

    /**
     * Generates the text for the given [value] and adds the defined [unit] at the end.
     */
    private fun getTextForValue(value: Any): String {
        return if (value !is Int) {
            "??$unit"
        } else {
            value.toString() + unit
        }
    }

    /**
     * Shows the seek bar dialog.
     */
    private fun showSeekBarDialog() {
        val inflater =
            context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val dialogView = PreferenceWidgetSeekbarBinding.inflate(inflater)
        val initValue = sharedPreferences.getInt(key, defaultValue)
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
                sharedPreferences.edit().putInt(key, actualValue).apply()
            }
            setNeutralButton(R.string.pref__default) { _, _ ->
                sharedPreferences.edit().putInt(key, defaultValue).apply()
            }
            setNegativeButton(android.R.string.cancel, null)
            setOnDismissListener { summary = getTextForValue(sharedPreferences.getInt(key, defaultValue)) }
            create()
            show()
        }
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
