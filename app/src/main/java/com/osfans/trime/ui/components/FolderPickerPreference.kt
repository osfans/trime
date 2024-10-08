// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.components

import android.content.Context
import android.content.res.TypedArray
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.osfans.trime.R
import com.osfans.trime.databinding.FolderPickerDialogBinding
import com.osfans.trime.util.getFileFromUri
import com.osfans.trime.util.getUriForFile
import java.io.File

class FolderPickerPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    ) : Preference(context, attrs, defStyleAttr) {
        private var value = ""
        private lateinit var dialogView: FolderPickerDialogBinding

        var default = ""

        var documentTreeLauncher: ActivityResultLauncher<Uri?>? = null

        init {
            context.theme.obtainStyledAttributes(attrs, R.styleable.FolderPickerPreferenceAttrs, 0, 0).run {
                try {
                    if (getBoolean(R.styleable.FolderPickerPreferenceAttrs_useSimpleSummaryProvider, false)) {
                        summaryProvider = SummaryProvider<FolderPickerPreference> { it.value }
                    }
                } finally {
                    recycle()
                }
            }
        }

        override fun persistString(value: String): Boolean =
            super.persistString(value).also {
                if (it) this.value = value
            }

        override fun setDefaultValue(defaultValue: Any?) {
            super.setDefaultValue(defaultValue)
            default = defaultValue as? String ?: ""
        }

        override fun onGetDefaultValue(
            a: TypedArray,
            index: Int,
        ): Any = a.getString(index) ?: default

        override fun onSetInitialValue(defaultValue: Any?) {
            value = getPersistedString(defaultValue as? String ?: default)
        }

        override fun onClick() = showPickerDialog()

        private fun showPickerDialog() {
            val initValue = value
            dialogView = FolderPickerDialogBinding.inflate(LayoutInflater.from(context))
            dialogView.editText.setText(initValue)
            dialogView.button.setOnClickListener {
                documentTreeLauncher?.launch(context.getUriForFile(File(initValue)))
            }
            AlertDialog
                .Builder(context)
                .setTitle(this@FolderPickerPreference.title)
                .setView(dialogView.root)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val value = dialogView.editText.text.toString()
                    setValue(value)
                }.setNeutralButton(R.string.pref__default) { _, _ ->
                    setValue(default)
                }.setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        private fun setValue(value: String) {
            if (callChangeListener(value)) {
                persistString(value)
                notifyChanged()
            }
        }

        fun onResult(result: Uri) {
            dialogView.editText.setText(context.getFileFromUri(result)?.absolutePath)
        }
    }
