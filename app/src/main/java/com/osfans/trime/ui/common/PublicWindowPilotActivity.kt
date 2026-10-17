/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ui.common

import android.content.Context
import android.os.Bundle
import android.view.Window
import androidx.annotation.Keep
import androidx.core.app.ActivityCompat
import androidx.core.app.NavUtils
import androidx.core.app.TaskStackBuilder
import androidx.fragment.app.FragmentActivity
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate

/** Shared settings/diagnostics Window owner; no AppCompatDelegate is installed. */
abstract class PublicWindowPilotActivity : FragmentActivity() {
    private var attachedUiMode = AppPrefs.Advanced.UiMode.AUTO
    private var listenerRegistered = false
    private var recreationRequested = false

    @Keep
    private val uiModeChanged = PreferenceDelegate.OnChangeListener<AppPrefs.Advanced.UiMode> { _, _ ->
        runOnUiThread { recreateForChangedUiMode() }
    }

    override fun attachBaseContext(newBase: Context) {
        attachedUiMode = AppPrefs.defaultInstance().advanced.uiMode.getValue()
        super.attachBaseContext(PublicWindowPilotConfiguration.wrap(newBase, attachedUiMode))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The manifest theme also sets the framework attributes for the starting Window.
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        AppPrefs.defaultInstance().advanced.uiMode.registerOnChangeListener(uiModeChanged)
        listenerRegistered = true
    }

    override fun onStart() {
        super.onStart()
        recreateForChangedUiMode()
    }

    private fun recreateForChangedUiMode() {
        if (isFinishing || isDestroyed || recreationRequested) return
        if (AppPrefs.defaultInstance().advanced.uiMode.getValue() == attachedUiMode) return
        // Recreate even if the resolved dark bit matches. AUTO must remove a prior forced override.
        recreationRequested = true
        ActivityCompat.recreate(this)
    }

    /** Match the existing AppCompat parent-task policy, rather than treating Up as Back. */
    protected fun navigateToManifestParent() {
        val parent = NavUtils.getParentActivityIntent(this) ?: return
        if (shouldUpRecreateTask(parent)) {
            TaskStackBuilder.create(this).addNextIntentWithParentStack(parent).startActivities()
            try {
                finishAffinity()
            } catch (_: IllegalStateException) {
                finish()
            }
        } else {
            navigateUpTo(parent)
        }
    }

    override fun onDestroy() {
        if (listenerRegistered) {
            AppPrefs.defaultInstance().advanced.uiMode.unregisterOnChangeListener(uiModeChanged)
            listenerRegistered = false
        }
        super.onDestroy()
    }
}
