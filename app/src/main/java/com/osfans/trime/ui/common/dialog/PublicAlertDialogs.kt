/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ui.common.dialog

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import androidx.annotation.MainThread
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ui.common.PublicWindowPilotConfiguration
import java.util.IdentityHashMap

/** Framework Window owner only; explicit AppCompat widgets remain available to callers. */
internal object PublicAlertDialogs {
    /**
     * The supplied context must still lead to a live Activity. Preserve its font/locale/display
     * wrappers rather than replacing it with an application context or the unwrapped Activity.
     * Callers retain their existing create/show/dismiss, cancellation and lifecycle ownership.
     */
    @MainThread
    fun builder(context: Context): AlertDialog.Builder {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Create dialog builders on the main thread" }
        val activity = requireActivity(context)
        check(!activity.isFinishing && !activity.isDestroyed) { "Dialog owner is no longer live" }
        val themed = PublicWindowPilotConfiguration.wrap(
            context,
            AppPrefs.defaultInstance().advanced.uiMode.getValue(),
            R.style.Theme_TrimePublicAlertDialog,
        )
        return AlertDialog.Builder(themed, R.style.Theme_TrimePublicAlertDialog)
    }

    /**
     * Asynchronous UI work may outlive an owner's request to finish, before lifecycle cancellation.
     * Call immediately before create/show in the same main-thread turn. Invalid contexts and
     * off-main use still fail: only an Activity that is ending makes this check return false.
     */
    @MainThread
    fun canShow(context: Context): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Check dialog owners on the main thread" }
        val activity = requireActivity(context)
        return !activity.isFinishing && !activity.isDestroyed
    }

    private fun requireActivity(context: Context): Activity {
        val seen = IdentityHashMap<Context, Boolean>()
        var current = context
        repeat(32) {
            val candidate = current
            require(seen.put(candidate, true) == null) { "Cyclic dialog context chain" }
            if (candidate is Activity) return candidate
            current = (candidate as? ContextWrapper)?.baseContext
                ?: throw IllegalArgumentException("A dialog context must be backed by an Activity")
        }
        throw IllegalArgumentException("Dialog context chain exceeds 32 wrappers")
    }
}
