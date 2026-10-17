/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ui.common.dialog

import android.content.Context
import android.util.AttributeSet
import android.widget.EditText
import androidx.preference.EditTextPreference

/** Keep the public binding callback accessible to our own platform dialog implementation. */
open class PublicBoundEditTextPreference : EditTextPreference {
    private var dialogBinding: EditTextPreference.OnBindEditTextListener? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
        super(context, attrs, defStyleAttr, defStyleRes)

    override fun setOnBindEditTextListener(listener: EditTextPreference.OnBindEditTextListener?) {
        dialogBinding = listener
        super.setOnBindEditTextListener(listener)
    }

    internal fun bindDialogEditText(editText: EditText) {
        dialogBinding?.onBindEditText(editText)
    }
}
