/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.osfans.trime.ui.common.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.preference.DialogPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.osfans.trime.ui.common.PaddingPreferenceFragment

internal object PublicPreferenceDialogFactory {
    // Keep the same scope and tag as the previously displayed Preference dialogs.
    private const val DIALOG_TAG = "androidx.preference.PreferenceFragment.DIALOG"

    fun showIfSupported(owner: PaddingPreferenceFragment, preference: Preference): Boolean {
        val dialog = when (preference) {
            is PublicBoundEditTextPreference -> PublicEditTextPreferenceDialogFragment()

            is ListPreference -> PublicListPreferenceDialogFragment()

            is MultiSelectListPreference -> PublicMultiSelectPreferenceDialogFragment()

            // An arbitrary EditTextPreference may own a package-private binding callback.
            // Preserve its original public behavior; it is not a migrated window.
            else -> return false
        }
        if (dispatchPublicCallbacks(owner, preference)) return true
        val manager = owner.parentFragmentManager
        if (manager.findFragmentByTag(DIALOG_TAG) != null) return true
        dialog.arguments = Bundle().apply {
            putString(PublicPreferenceDialogFragment.ARG_KEY, checkNotNull(preference.key))
            manager.putFragment(this, PublicPreferenceDialogFragment.OWNER_REFERENCE, owner)
        }
        dialog.show(manager, DIALOG_TAG)
        return true
    }

    private fun dispatchPublicCallbacks(owner: PaddingPreferenceFragment, preference: Preference): Boolean {
        // Preference1.2.1 getCallbackFragment is restricted/default-null; no app override exists.
        var fragment: Fragment? = owner
        while (fragment != null) {
            if ((fragment as? PreferenceFragmentCompat.OnPreferenceDisplayDialogCallback)
                    ?.onPreferenceDisplayDialog(owner, preference) == true
            ) {
                return true
            }
            fragment = fragment.parentFragment
        }
        if ((owner.context as? PreferenceFragmentCompat.OnPreferenceDisplayDialogCallback)
                ?.onPreferenceDisplayDialog(owner, preference) == true
        ) {
            return true
        }
        return (owner.activity as? PreferenceFragmentCompat.OnPreferenceDisplayDialogCallback)
            ?.onPreferenceDisplayDialog(owner, preference) == true
    }
}

/** Platform Window, with public FragmentManager identity rather than deprecated target fragments. */
abstract class PublicPreferenceDialogFragment :
    DialogFragment(),
    DialogInterface.OnClickListener {
    private var dialogTitle: CharSequence? = null
    private var positiveText: CharSequence? = null
    private var negativeText: CharSequence? = null
    private var dialogMessage: CharSequence? = null
    private var dialogLayout = 0
    private var dialogIcon: Bitmap? = null
    private var whichButton = DialogInterface.BUTTON_NEGATIVE
    private var closeDelivered = false
    private var cancelOnStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val preference = checkNotNull(currentPreference()) { "Preference dialog owner or key is unavailable" }
            dialogTitle = preference.dialogTitle
            positiveText = preference.positiveButtonText
            negativeText = preference.negativeButtonText
            dialogMessage = preference.dialogMessage
            dialogLayout = preference.dialogLayoutResource
            dialogIcon = snapshotIcon(preference.dialogIcon)
        } else {
            dialogTitle = savedInstanceState.getCharSequence(STATE_TITLE)
            positiveText = savedInstanceState.getCharSequence(STATE_POSITIVE)
            negativeText = savedInstanceState.getCharSequence(STATE_NEGATIVE)
            dialogMessage = savedInstanceState.getCharSequence(STATE_MESSAGE)
            dialogLayout = savedInstanceState.getInt(STATE_LAYOUT)
            dialogIcon = BundleCompat.getParcelable(savedInstanceState, STATE_ICON, Bitmap::class.java)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putCharSequence(STATE_TITLE, dialogTitle)
        outState.putCharSequence(STATE_POSITIVE, positiveText)
        outState.putCharSequence(STATE_NEGATIVE, negativeText)
        outState.putCharSequence(STATE_MESSAGE, dialogMessage)
        outState.putInt(STATE_LAYOUT, dialogLayout)
        dialogIcon?.let { outState.putParcelable(STATE_ICON, it) }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        whichButton = DialogInterface.BUTTON_NEGATIVE
        closeDelivered = false
        cancelOnStart = false
        val builder = PublicAlertDialogs.builder(requireContext())
            .setTitle(dialogTitle)
            .setIcon(dialogIcon?.toDrawable(resources))
            .setPositiveButton(positiveText, this)
            .setNegativeButton(negativeText, this)
        if (dialogLayout != 0) {
            // Main/Advanced's public onCreateView hook remains on the cloned inflater chain.
            val content = layoutInflater.cloneInContext(builder.context).inflate(dialogLayout, null)
            bindDialogView(content, savedInstanceState != null)
            builder.setView(content)
        } else {
            builder.setMessage(dialogMessage)
        }
        prepareDialogBuilder(builder)
        return builder.create()
    }

    protected open fun bindDialogView(view: View, restoring: Boolean) {
        val message = view.findViewById<View>(android.R.id.message) ?: return
        val visible = !TextUtils.isEmpty(dialogMessage)
        if (visible && message is TextView) message.text = dialogMessage
        message.visibility = if (visible) View.VISIBLE else View.GONE
    }

    protected open fun prepareDialogBuilder(builder: android.app.AlertDialog.Builder) {}

    protected fun cancelForMissingOwner() {
        cancelOnStart = true
    }

    protected val canStartDialog: Boolean get() = !cancelOnStart

    override fun onStart() {
        super.onStart()
        if (cancelOnStart) dismiss()
    }

    /** Lookup is exact; avoid getFragment's exception/log on an already removed owner. */
    protected fun currentOwner(): PreferenceFragmentCompat? {
        val args = requireArguments()
        val reference = args.getString(OWNER_REFERENCE) ?: return null
        val manager = parentFragmentManager
        val candidate = manager.fragments.singleOrNull { fragment ->
            val identity = Bundle()
            manager.putFragment(identity, OWNER_REFERENCE, fragment)
            identity.getString(OWNER_REFERENCE) == reference
        } ?: return null
        val owner = manager.getFragment(args, OWNER_REFERENCE)
        if (owner !== candidate || owner !is PreferenceFragmentCompat) return null
        return owner
    }

    protected fun currentPreference(): DialogPreference? {
        val owner = currentOwner() ?: return null
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) return null
        if (owner.preferenceScreen == null) return null
        val key = requireArguments().getString(ARG_KEY) ?: return null
        return owner.findPreference<Preference>(key) as? DialogPreference
    }

    override fun onClick(dialog: DialogInterface, which: Int) {
        whichButton = which
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!closeDelivered) {
            closeDelivered = true
            onDialogClosed(!cancelOnStart && whichButton == DialogInterface.BUTTON_POSITIVE)
        }
    }

    protected abstract fun onDialogClosed(positive: Boolean)

    private fun snapshotIcon(icon: Drawable?): Bitmap? {
        if (icon == null) return null
        if (icon is BitmapDrawable) return icon.bitmap
        val bitmap = createBitmap(icon.intrinsicWidth, icon.intrinsicHeight)
        val canvas = Canvas(bitmap)
        icon.setBounds(0, 0, canvas.width, canvas.height)
        icon.draw(canvas)
        return bitmap
    }

    companion object {
        internal const val ARG_KEY = "key"
        internal const val OWNER_REFERENCE = "trime.public.preference.owner"
        private const val STATE_TITLE = "PreferenceDialogFragment.title"
        private const val STATE_POSITIVE = "PreferenceDialogFragment.positiveText"
        private const val STATE_NEGATIVE = "PreferenceDialogFragment.negativeText"
        private const val STATE_MESSAGE = "PreferenceDialogFragment.message"
        private const val STATE_LAYOUT = "PreferenceDialogFragment.layout"
        private const val STATE_ICON = "PreferenceDialogFragment.icon"
    }
}
