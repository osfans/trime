/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.get
import com.osfans.trime.R
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import com.osfans.trime.util.Const
import com.osfans.trime.util.formatDateTime

class AboutFragment : PaddingPreferenceFragment() {

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.about_preference, rootKey)
        with(preferenceScreen) {
            get<Preference>("current_version")?.apply {
                summary = Const.VERSION_NAME
                isCopyingEnabled = true
                intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("${Const.GIT_REPO}/commit/${Const.BUILD_COMMIT_HASH}"),
                    )
            }

            get<Preference>("librime_version")?.apply {
                val code = Const.LIBRIME_VERSION
                val hash = extractCommitHash(code)
                summary = code
                intent?.data?.also {
                    intent!!.data = Uri.withAppendedPath(it, "commit/$hash")
                }
            }
            get<Preference>("opencc_version")?.apply {
                val code = Const.OPENCC_VERSION
                val hash = extractCommitHash(code)
                summary = code
                intent?.data?.also {
                    intent!!.data = Uri.withAppendedPath(it, "commit/$hash")
                }
            }
            get<Preference>("build_info")?.apply {
                summary =
                    requireContext().getString(
                        R.string.build_info_format,
                        Const.BUILDER,
                        Const.BUILD_COMMIT_HASH,
                        formatDateTime(Const.BUILD_TIMESTAMP),
                    )
                isCopyingEnabled = true
            }
            get<Preference>("source_code")?.apply {
                intent = Intent(Intent.ACTION_VIEW, Uri.parse(Const.GIT_REPO))
            }
            get<Preference>("open_source_licenses")?.apply {
                setOnPreferenceClickListener {
                    findNavController().navigate(R.id.action_aboutFragment_to_licenseFragment)
                    true
                }
            }
        }
    }

    companion object {
        private val DASH_G_PATTERN = Regex("^(.*-g)([0-9a-f]+)(.*)$")
        private val COMMON_PATTERN = Regex("^([^-]*)(-.*)$")

        private fun extractCommitHash(versionCode: String): String = DASH_G_PATTERN.find(versionCode)?.groupValues?.get(2)
            ?: COMMON_PATTERN.find(versionCode)?.groupValues?.get(1)
            ?: versionCode
    }
}
