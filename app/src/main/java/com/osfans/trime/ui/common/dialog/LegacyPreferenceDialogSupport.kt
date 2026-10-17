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

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.BundleCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.preference.DialogPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDialogFragmentCompat
import com.osfans.trime.R

internal enum class LegacyPreferenceDialogKind { EDIT_TEXT, LIST, MULTI_SELECT }

internal data class LegacyPreferenceDialogClose(val positive: Boolean, val preference: DialogPreference?)

/** Composition avoids replacing the original editor's opaque callback/private state implementation. */
internal class LegacyPreferenceDialogWindow(
    private val fragment: PreferenceDialogFragmentCompat,
    private val kind: LegacyPreferenceDialogKind,
) {
    private var title: CharSequence? = null
    private var positiveText: CharSequence? = null
    private var negativeText: CharSequence? = null
    private var message: CharSequence? = null
    private var layout = 0
    private var icon: Bitmap? = null
    private var ownerAtCreate: Fragment? = null
    private var preferenceAtCreate: DialogPreference? = null
    private var closeDelivered = false

    fun onCreate(savedState: Bundle?) {
        ownerAtCreate = currentLegacyOwner()
        // Original super.onCreate already validates the wider TargetFragment contract and
        // FragmentStateManager advances that exact legacy target before attaching this dialog.
        if (savedState == null) {
            val preference = checkNotNull(fragment.preference)
            preferenceAtCreate = preference
            title = preference.dialogTitle
            positiveText = preference.positiveButtonText
            negativeText = preference.negativeButtonText
            message = preference.dialogMessage
            layout = preference.dialogLayoutResource
            icon = snapshotIcon(preference.dialogIcon)
        } else {
            title = savedState.getCharSequence(STATE_TITLE)
            positiveText = savedState.getCharSequence(STATE_POSITIVE)
            negativeText = savedState.getCharSequence(STATE_NEGATIVE)
            message = savedState.getCharSequence(STATE_MESSAGE)
            layout = savedState.getInt(STATE_LAYOUT)
            icon = BundleCompat.getParcelable(savedState, STATE_ICON, Bitmap::class.java)
        }
    }

    fun createDialog(
        bind: (View) -> Unit,
        prepare: (AlertDialog.Builder) -> Unit = {},
    ): Dialog {
        closeDelivered = false
        val builder = PublicAlertDialogs.builder(fragment.requireContext())
            .setTitle(title)
            .setIcon(icon?.toDrawable(fragment.resources))
            .setPositiveButton(positiveText, fragment)
            .setNegativeButton(negativeText, fragment)
        if (layout != 0) {
            // The inherited onCreateDialogView(Context) ignores its parameter. Use the same
            // resource with the real F dialog context, then retain the inherited binding hook.
            val content = LayoutInflater.from(builder.context).inflate(layout, null)
            bind(content)
            builder.setView(content)
        } else {
            builder.setMessage(message)
        }
        prepare(builder)
        val dialog = builder.create()
        if (kind != LegacyPreferenceDialogKind.EDIT_TEXT) {
            // Keep the old hierarchy key before Dialog restores the saved list and focus.
            checkNotNull(dialog.listView).id = R.id.select_dialog_listview
        }
        // Reset the original superclass's private disposition using only its public API and
        // this actual Window's Dialog; no fake DialogInterface or private-field access.
        fragment.onClick(dialog, DialogInterface.BUTTON_NEGATIVE)
        return dialog
    }

    fun recordBoundPreference() {
        // Only after the original editor binding has lawfully performed its own lazy lookup.
        if (preferenceAtCreate == null) preferenceAtCreate = fragment.preference
    }

    fun takeClose(positive: Boolean): LegacyPreferenceDialogClose? {
        if (closeDelivered) return null
        closeDelivered = true
        if (!positive) return LegacyPreferenceDialogClose(false, null)
        val owner = currentLegacyOwner()
        if (owner == null || owner !== ownerAtCreate ||
            !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)
        ) {
            return LegacyPreferenceDialogClose(false, null)
        }
        val key = fragment.requireArguments().getString("key") ?: return LegacyPreferenceDialogClose(false, null)
        val current = (owner as DialogPreference.TargetFragment).findPreference<Preference>(key)
        if (current !is DialogPreference || !matchesKind(current)) return LegacyPreferenceDialogClose(false, null)
        // A restored List/Multi superclass can legitimately defer its first preference lookup
        // until positive close. Do not make saved metadata creation depend on an early screen.
        val original = preferenceAtCreate ?: fragment.preference.also { preferenceAtCreate = it }
        if (current !== original) return LegacyPreferenceDialogClose(false, null)
        return LegacyPreferenceDialogClose(true, current)
    }

    fun onDestroy() {
        ownerAtCreate = null
        preferenceAtCreate = null
        icon = null
    }

    private fun matchesKind(preference: DialogPreference): Boolean = when (kind) {
        LegacyPreferenceDialogKind.EDIT_TEXT -> preference is EditTextPreference
        LegacyPreferenceDialogKind.LIST -> preference is ListPreference
        LegacyPreferenceDialogKind.MULTI_SELECT -> preference is MultiSelectListPreference
    }

    // Compatibility for already-saved AndroidX target state only. New display flows never set
    // or read targets. This public deprecated getter still obeys real FragmentStrictMode policy.
    @Suppress("DEPRECATION")
    private fun currentLegacyOwner(): Fragment? {
        val target = fragment.targetFragment ?: return null
        if (target !is DialogPreference.TargetFragment || !target.isAdded) return null
        val manager = fragment.parentFragmentManager
        if (target.parentFragmentManager !== manager || manager.fragments.none { it === target }) return null
        return target
    }

    private fun snapshotIcon(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        if (drawable is BitmapDrawable) return drawable.bitmap
        val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    companion object {
        private const val STATE_TITLE = "PreferenceDialogFragment.title"
        private const val STATE_POSITIVE = "PreferenceDialogFragment.positiveText"
        private const val STATE_NEGATIVE = "PreferenceDialogFragment.negativeText"
        private const val STATE_MESSAGE = "PreferenceDialogFragment.message"
        private const val STATE_LAYOUT = "PreferenceDialogFragment.layout"
        private const val STATE_ICON = "PreferenceDialogFragment.icon"
    }
}

/** Own only this legacy editor's bounded public IME request; leave opaque bind focus choices intact. */
internal class LegacyPreferenceDialogIme {
    private var editor: EditText? = null
    private var pending: Runnable? = null

    fun show(dialog: Dialog, target: EditText) {
        cancel()
        // A legacy opaque binding callback may intentionally move or clear editor focus.
        if (!target.isFocused) return
        editor = target
        if (Build.VERSION.SDK_INT >= 30) {
            dialog.window?.let { WindowCompat.getInsetsController(it, target).show(WindowInsetsCompat.Type.ime()) }
        } else {
            val deadline = SystemClock.uptimeMillis() + 1_000L
            val request = object : Runnable {
                override fun run() {
                    if (editor !== target || !target.isFocused || !dialog.isShowing ||
                        SystemClock.uptimeMillis() >= deadline
                    ) {
                        cancel()
                        return
                    }
                    val manager = target.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    if (manager.showSoftInput(target, 0)) cancel() else target.postDelayed(this, 50L)
                }
            }
            pending = request
            target.post(request)
        }
    }

    fun cancel() {
        pending?.let { editor?.removeCallbacks(it) }
        pending = null
        editor = null
    }
}
