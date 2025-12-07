/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.License
import com.osfans.trime.R
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import kotlinx.coroutines.launch

class LicenseFragment : PaddingPreferenceFragment() {
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        lifecycleScope.launch {
            val context = preferenceManager.context
            preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
                val jsonString = resources.openRawResource(R.raw.aboutlibraries)
                    .bufferedReader()
                    .use { it.readText() }
                Libs
                    .Builder()
                    .withJson(jsonString)
                    .build()
                    .libraries
                    .sortedBy {
                        if (it.tag == "native") it.uniqueId.uppercase() else it.uniqueId.lowercase()
                    }.forEach {
                        addPreference(
                            Preference(context).apply {
                                isIconSpaceReserved = false
                                title = "${it.uniqueId}:${it.artifactVersion}"
                                summary = it.licenses.joinToString { l -> l.spdxId ?: l.name }
                                setOnPreferenceClickListener { _ ->
                                    showLicenseDialog(it.uniqueId, it.licenses)
                                }
                            },
                        )
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.disableTopOptionsMenu()
    }

    private fun showLicenseDialog(
        uniqueId: String,
        licenses: Set<License>,
    ): Boolean {
        when (licenses.size) {
            0 -> {}
            1 -> showLicenseContent(licenses.first())
            else -> {
                val licenseArray = licenses.toTypedArray()
                val licenseNames = licenseArray.map { it.spdxId ?: it.name }.toTypedArray()
                AlertDialog
                    .Builder(requireContext())
                    .setTitle(uniqueId)
                    .setItems(licenseNames) { _, idx ->
                        showLicenseContent(licenseArray[idx])
                    }.setPositiveButton(android.R.string.cancel, null)
                    .show()
            }
        }
        return true
    }

    private fun showLicenseContent(license: License) {
        if (license.url?.isNotBlank() == true) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(license.url)))
        }
    }
}
