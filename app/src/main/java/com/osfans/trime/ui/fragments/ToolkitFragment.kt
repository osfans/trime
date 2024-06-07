// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.fragments

import android.app.AlertDialog
import android.os.Bundle
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import com.blankj.utilcode.util.ToastUtils
import com.osfans.trime.R
import com.osfans.trime.data.storage.StorageUtils
import com.osfans.trime.ui.components.PaddingPreferenceFragment
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.util.Logcat
import com.osfans.trime.util.ShortcutUtils

class ToolkitFragment : PaddingPreferenceFragment() {
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)
        screen.addPreference(
            Preference(context).apply {
                setTitle(R.string.real_time_logs)
                isIconSpaceReserved = false
                setOnPreferenceClickListener {
                    ShortcutUtils.launchLogActivity(context)
                    true
                }
            },
        )
        screen.addPreference(
            Preference(context).apply {
                setTitle(R.string.real_time_logs_clear)
                isIconSpaceReserved = false
                setOnPreferenceClickListener {
                    AlertDialog.Builder(context)
                        .setMessage(R.string.real_time_logs_confirm)
                        .setPositiveButton(R.string.ok) { _, _ ->
                            Logcat.default.clearLog()
                        }.setNegativeButton(R.string.cancel, null)
                        .show()
                    true
                }
            },
        )
        screen.addPreference(
            Preference(context).apply {
                setTitle(R.string.pref_toolkit_internal_dir)
                isIconSpaceReserved = false
                setOnPreferenceClickListener {
                    val intent = StorageUtils.getViewDirIntent(context)
                    intent?.let {
                        startActivity(it)
                    } ?: run {
                        ToastUtils.showShort("No Document App Found")
                    }
                    true
                }
            },
        )
        preferenceScreen = screen
    }

    override fun onResume() {
        super.onResume()
        viewModel.setToolbarTitle(getString(R.string.pref_toolkit))
        viewModel.disableTopOptionsMenu()
    }
}
