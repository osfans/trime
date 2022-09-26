package com.osfans.trime.ui.components

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup.LayoutParams
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.Preference
import com.osfans.trime.R
import com.osfans.trime.databinding.FolderPickerDialogBinding

class FolderPickerPreference : Preference {
    private var value = ""

    @Suppress("unused")
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) :
            this(context, attrs, androidx.preference.R.attr.preferenceStyle)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        context.theme.obtainStyledAttributes(attrs, R.styleable.FolderPickerPreferenceAttrs, 0, 0).run {
            try {
                if (getBoolean(R.styleable.FolderPickerPreferenceAttrs_useSimpleSummaryProvider, false)) {
                    summaryProvider = SummaryProvider<FolderPickerPreference> { it.currentValue }
                }
            } finally {
                recycle()
            }
        }
    }

    private val currentValue: String
        get() = getPersistedString(value)

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getString(index) ?: ""
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        value = defaultValue as? String ?: getPersistedString("")
    }

    override fun onClick() = showPickerDialog()

    private fun showPickerDialog() {
        val initValue = currentValue
        val dialogView = FolderPickerDialogBinding.inflate(LayoutInflater.from(context))
        dialogView.editText.setText(initValue)
        dialogView.button.setOnClickListener {

        }
        AlertDialog.Builder(context)
            .setTitle(this@FolderPickerPreference.title)
            .setView(dialogView.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = dialogView.editText.text.toString()
                if (callChangeListener(value)) {
                    persistString(value)
                    notifyChanged()
                }
            }
            .setNeutralButton(R.string.pref__default) { _, _ ->
                persistString(value)
                notifyChanged()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
