package com.osfans.trime.ui.components

import android.content.Context
import android.content.res.TypedArray
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.blankj.utilcode.util.UriUtils
import com.osfans.trime.R
import com.osfans.trime.databinding.FolderPickerDialogBinding
import java.io.File

class FolderPickerPreference : Preference {
    private var value = ""
    lateinit var documentTreeLauncher: ActivityResultLauncher<Uri?>
    lateinit var dialogView: FolderPickerDialogBinding

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

    override fun onGetDefaultValue(
        a: TypedArray,
        index: Int,
    ): Any {
        return a.getString(index) ?: ""
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        value = defaultValue as? String ?: getPersistedString("")
    }

    override fun onClick() = showPickerDialog()

    private fun showPickerDialog() {
        val initValue = currentValue
        dialogView = FolderPickerDialogBinding.inflate(LayoutInflater.from(context))
        dialogView.editText.setText(initValue)
        dialogView.button.setOnClickListener {
            documentTreeLauncher.launch(UriUtils.file2Uri(File(initValue)))
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
