/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.theme

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.osfans.trime.R
import com.osfans.trime.data.theme.ThemeDiagnostics
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.databinding.ActivityThemeDiagnosticsBinding
import com.osfans.trime.util.DeviceInfo
import com.osfans.trime.util.toast
import splitties.systemservices.clipboardManager
import splitties.views.recyclerview.verticalLayoutManager

/**
 * Shows what the static checks of [ThemeDiagnostics] found in the theme in
 * use, and copies or shares them as a report.
 */
class ThemeDiagnosticsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivityThemeDiagnosticsBinding.inflate(layoutInflater)
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
        val theme = ThemeManager.activeTheme
        val findings = ThemeManager.activeFindings
        val themeId = ThemeManager.prefs.selectedTheme.getValue()
        val report = DeviceInfo.get(this) + ThemeDiagnostics.format(themeId, theme.name, findings)

        with(binding) {
            setSupportActionBar(diagnosticsToolbar.toolbar)
            supportActionBar!!.apply {
                setDisplayHomeAsUpEnabled(true)
                setTitle(R.string.theme_diagnostics)
                subtitle = theme.name
            }
            findingsList.layoutManager = verticalLayoutManager()
            findingsList.adapter = ThemeDiagnosticListAdapter(findings.orEmpty())
            emptyState.isVisible = findings.isNullOrEmpty()
            emptyState.setText(
                if (findings == null) {
                    R.string.theme_diagnostics_unchecked
                } else {
                    R.string.theme_diagnostics_no_findings
                },
            )
            copyButton.setOnClickListener {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("theme-diagnostics", report))
                if (clipboardManager.hasPrimaryClip()) {
                    toast(R.string.copy_done)
                }
            }
            shareButton.setOnClickListener {
                val target =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, getString(R.string.theme_diagnostics))
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                startActivity(Intent.createChooser(target, getString(R.string.share)))
            }
        }
    }
}
