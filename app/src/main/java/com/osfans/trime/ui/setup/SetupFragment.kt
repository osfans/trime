/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.sync.DataStorageMode
import com.osfans.trime.data.sync.RimeDataSync
import com.osfans.trime.databinding.FragmentSetupBinding
import com.osfans.trime.util.serializable
import kotlinx.coroutines.launch
import timber.log.Timber

class SetupFragment : Fragment() {
    private lateinit var binding: FragmentSetupBinding

    private val page: SetupPage by lazy { requireArguments().serializable("page")!! }

    private val prefs = AppPrefs.defaultInstance().profile

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentSetupBinding.inflate(inflater).apply {
            storageModeOptions.setOnCheckedChangeListener { _, checkedId ->
                val oldMode = prefs.dataStorageMode.getValue()
                val newMode = when (checkedId) {
                    R.id.sync_from_external_option -> DataStorageMode.EXTERNAL_SYNC
                    R.id.app_specific_storage_option -> DataStorageMode.APP_STORAGE
                    else -> return@setOnCheckedChangeListener
                }
                if (oldMode == DataStorageMode.EXTERNAL_SYNC &&
                    newMode == DataStorageMode.APP_STORAGE
                ) {
                    prefs.userDbMigrated.setValue(false)
                    // Commit the mode only after the cleanup finished: a
                    // cancelled cleanup would leave the stale external grant
                    // behind and silently reuse it when switching back.
                    lifecycleScope.launch {
                        val cleared =
                            runCatching { RimeDataSync.clearExternalTree(requireContext()) }.isSuccess
                        // Re-check the selection before the delayed commit: the
                        // user may have picked the external mode again while
                        // the cleanup was waiting on the sync lock.
                        if (cleared &&
                            prefs.dataStorageMode.getValue() == DataStorageMode.EXTERNAL_SYNC
                        ) {
                            prefs.dataStorageMode.setValue(newMode)
                        } else if (!cleared) {
                            Timber.e("Failed to clear the external tree; keeping $oldMode")
                        }
                        sync()
                        (requireActivity() as SetupActivity).updateButtons()
                    }
                } else {
                    prefs.dataStorageMode.setValue(newMode)
                    sync()
                    (requireActivity() as SetupActivity).updateButtons()
                }
            }
            syncFromExternalDesc.setOnClickListener { syncFromExternalOption.isChecked = true }
            appSpecificStorageDesc.setOnClickListener { appSpecificStorageOption.isChecked = true }
        }
        sync()
        return binding.root
    }

    // Called on window focus changed
    fun sync() {
        val done = page.isDone()
        val isStorageModePage = page == SetupPage.Mode
        val checkedId = when (prefs.dataStorageMode.getValue()) {
            DataStorageMode.EXTERNAL_SYNC -> R.id.sync_from_external_option
            DataStorageMode.APP_STORAGE -> R.id.app_specific_storage_option
        }
        with(binding) {
            storageModeOptions.visibility = if (isStorageModePage) View.VISIBLE else View.GONE
            storageModeOptions.check(checkedId)

            stepText.text = page.getStepText(requireContext())
            hintText.text = page.getHintText(requireContext())
            val showActionButton = !done && page.showActionButton()
            actionButton.visibility = if (showActionButton) View.VISIBLE else View.GONE
            actionButton.text = page.getButtonText(requireContext())
            actionButton.setOnClickListener { page.getButtonAction(requireActivity()) }
            doneText.visibility = if (done) View.VISIBLE else View.GONE
            doneIcon.visibility = if (done) View.VISIBLE else View.GONE
        }
    }
}
