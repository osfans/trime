/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.theme

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.RimeUnavailableException
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.theme.ThemeDiagnostics
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.databinding.ActivityThemeDiagnosticsBinding
import com.osfans.trime.ui.common.PublicWindowPilotActivity
import com.osfans.trime.util.DeviceInfo
import com.osfans.trime.util.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import splitties.systemservices.clipboardManager
import splitties.views.recyclerview.verticalLayoutManager
import timber.log.Timber

/**
 * Shows what the static checks of [ThemeDiagnostics] found in the theme in
 * use, and copies or shares them as a report.
 */
class ThemeDiagnosticsActivity : PublicWindowPilotActivity() {
    private lateinit var binding: ActivityThemeDiagnosticsBinding
    private val daemonSessionName = "${ThemeDiagnosticsActivity::class.java.name}@${System.identityHashCode(this)}"
    private var daemonSessionCreated = false
    private var report: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityThemeDiagnosticsBinding.inflate(layoutInflater)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = systemBars.left
                rightMargin = systemBars.right
                bottomMargin = systemBars.bottom
            }
            binding.diagnosticsToolbar.toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemBars.top
            }
            windowInsets
        }
        WindowCompat
            .getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        setContentView(binding.root)
        with(binding) {
            diagnosticsToolbar.toolbar.apply {
                setTitle(R.string.theme_diagnostics)
                val indicator = TypedValue()
                val themedIcon =
                    if (context.theme.resolveAttribute(androidx.appcompat.R.attr.homeAsUpIndicator, indicator, true) &&
                        indicator.resourceId != 0
                    ) {
                        AppCompatResources.getDrawable(context, indicator.resourceId)
                    } else {
                        null
                    }
                navigationIcon = themedIcon ?: DrawerArrowDrawable(context).apply {
                    progress = 1f
                    color = ContextCompat.getColor(context, R.color.toolbarForegroundColor)
                }
                setNavigationContentDescription(R.string.public_window_navigate_up)
                setNavigationOnClickListener { navigateToManifestParent() }
            }
            findingsList.layoutManager = verticalLayoutManager()
            emptyState.isVisible = false
            copyButton.isEnabled = false
            shareButton.isEnabled = false
            copyButton.setOnClickListener {
                val text = report ?: return@setOnClickListener
                clipboardManager.setPrimaryClip(ClipData.newPlainText("theme-diagnostics", text))
                if (clipboardManager.hasPrimaryClip()) {
                    toast(R.string.copy_done)
                }
            }
            shareButton.setOnClickListener {
                val text = report ?: return@setOnClickListener
                val target =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.theme_diagnostics))
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                startActivity(Intent.createChooser(target, getString(R.string.share)))
            }
        }

        lifecycleScope.launch {
            try {
                // Settings can be opened before the IME process has initialized a
                // theme. Own a session while the suspend loader may need librime.
                RimeDaemon.createSession(daemonSessionName)
                daemonSessionCreated = true
                ThemeManager.init(resources.configuration)
                showDiagnostics()
            } catch (e: CancellationException) {
                if (e !is RimeUnavailableException) throw e
                Timber.e(e, "Rime is unavailable while initializing theme diagnostics")
                showUnavailableReport()
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize theme diagnostics")
                showUnavailableReport()
            }
        }
    }

    private fun showDiagnostics() {
        val theme = ThemeManager.activeTheme
        val findings = ThemeManager.activeFindings
        val themeId = ThemeManager.prefs.selectedTheme.getValue()
        report = DeviceInfo.get(this) + ThemeDiagnostics.format(themeId, theme.name, findings)
        binding.diagnosticsToolbar.toolbar.subtitle = theme.name
        binding.findingsList.adapter = ThemeDiagnosticListAdapter(findings.orEmpty())
        binding.emptyState.isVisible = findings.isNullOrEmpty()
        binding.emptyState.setText(
            if (findings == null) {
                R.string.theme_diagnostics_unchecked
            } else {
                R.string.theme_diagnostics_no_findings
            },
        )
        binding.copyButton.isEnabled = true
        binding.shareButton.isEnabled = true
    }

    private fun showUnavailableReport() {
        val themeId = ThemeManager.prefs.selectedTheme.getValue()
        report = DeviceInfo.get(this) + ThemeDiagnostics.format(themeId, themeId, null)
        binding.diagnosticsToolbar.toolbar.subtitle = themeId
        binding.findingsList.adapter = ThemeDiagnosticListAdapter(emptyList())
        binding.emptyState.isVisible = true
        binding.emptyState.setText(R.string.theme_diagnostics_unchecked)
        binding.copyButton.isEnabled = true
        binding.shareButton.isEnabled = true
    }

    override fun onDestroy() {
        if (daemonSessionCreated) {
            RimeDaemon.destroySession(daemonSessionName)
            daemonSessionCreated = false
        }
        super.onDestroy()
    }
}
