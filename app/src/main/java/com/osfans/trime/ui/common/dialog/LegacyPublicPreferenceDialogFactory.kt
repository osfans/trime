/* SPDX-License-Identifier: GPL-3.0-or-later */

package com.osfans.trime.ui.common.dialog

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory

/** Decode only the three original built-in Preference dialogs; preserve unrelated construction. */
open class LegacyPublicPreferenceDialogFactory(private val delegate: FragmentFactory) : FragmentFactory() {
    override fun instantiate(classLoader: ClassLoader, className: String): Fragment = when (className) {
        "androidx.preference.EditTextPreferenceDialogFragmentCompat" -> LegacyPublicEditTextPreferenceDialogFragment()
        "androidx.preference.ListPreferenceDialogFragmentCompat" -> LegacyPublicListPreferenceDialogFragment()
        "androidx.preference.MultiSelectListPreferenceDialogFragmentCompat" -> LegacyPublicMultiSelectPreferenceDialogFragment()
        else -> delegate.instantiate(classLoader, className)
    }
}
