// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.databinding.FragmentSetupBinding
import com.osfans.trime.ui.setup.SetupPage.Companion.isLastPage
import com.osfans.trime.util.serializable

class SetupFragment : Fragment() {
    private val viewModel: SetupViewModel by activityViewModels()
    private lateinit var binding: FragmentSetupBinding

    private val page: SetupPage by lazy { requireArguments().serializable("page")!! }
    private val prefs = AppPrefs.defaultInstance().profile

    private var isDone: Boolean = false
        set(new) {
            if (new && page.isLastPage()) {
                viewModel.isAllDone.value = true
            }
            with(binding) {
                stepText.text = page.getStepText(requireContext())
                hintText.text = page.getHintText(requireContext())
                val showStorageMode = page == SetupPage.Permissions
                storageModeGroup.visibility = if (showStorageMode) View.VISIBLE else View.GONE
                val showActionButton = !new && page.showActionButton()
                actionButton.visibility = if (showActionButton) View.VISIBLE else View.GONE
                actionButton.text = page.getButtonText(requireContext())
                actionButton.setOnClickListener { page.getButtonAction(requireActivity()) }
                doneText.visibility = if (new) View.VISIBLE else View.GONE
                doneIcon.visibility = if (new) View.VISIBLE else View.GONE
            }
            field = new
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentSetupBinding.inflate(inflater)
        if (page == SetupPage.Permissions) {
            setupStorageModeRadios()
        }
        sync()
        return binding.root
    }

    private fun setupStorageModeRadios() {
        val mode = prefs.dataStorageMode.getValue()
        binding.radioExternalSync.isChecked = mode == AppPrefs.Profile.DataStorageMode.EXTERNAL_SYNC
        binding.radioAppStorage.isChecked = mode == AppPrefs.Profile.DataStorageMode.APP_STORAGE
        binding.storageModeGroup.setOnCheckedChangeListener { _: RadioGroup, checkedId ->
            val newMode =
                when (checkedId) {
                    R.id.radio_external_sync -> AppPrefs.Profile.DataStorageMode.EXTERNAL_SYNC
                    R.id.radio_app_storage -> AppPrefs.Profile.DataStorageMode.APP_STORAGE
                    else -> return@setOnCheckedChangeListener
                }
            if (prefs.dataStorageMode.getValue() != newMode) {
                prefs.dataStorageMode.setValue(newMode)
            }
            sync()
            (activity as? SetupActivity)?.refreshSkipButtonVisibility()
        }
    }

    // Called on window focus changed
    fun sync() {
        isDone = page.isDone()
    }

    override fun onResume() {
        super.onResume()
        sync()
    }
}
