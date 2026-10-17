/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnAttach
import androidx.fragment.app.Fragment
import com.osfans.trime.R
import com.osfans.trime.core.data.DataStorageMode
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.ChineseVariant
import com.osfans.trime.data.prefs.InputLayout
import com.osfans.trime.databinding.FragmentSetupBinding
import com.osfans.trime.util.serializable

class SetupFragment : Fragment() {
    private lateinit var binding: FragmentSetupBinding

    private val page: SetupPage by lazy { requireArguments().serializable("page")!! }
    val setupPage get() = page

    private val prefs = AppPrefs.defaultInstance().profile
    private var rendering = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentSetupBinding.inflate(inflater).apply {
            storageModeOptions.setOnCheckedChangeListener { _, checkedId ->
                if (rendering) return@setOnCheckedChangeListener
                val newMode = when (checkedId) {
                    R.id.sync_from_external_option -> DataStorageMode.EXTERNAL_SYNC
                    R.id.app_specific_storage_option -> DataStorageMode.APP_STORAGE
                    else -> return@setOnCheckedChangeListener
                }
                val activity = requireActivity() as SetupActivity
                if (newMode == DataStorageMode.EXTERNAL_SYNC && prefs.dataStorageMode.getValue() != newMode) {
                    activity.pickDataPath()
                } else {
                    activity.changeStorageMode(newMode)
                }
            }
            syncFromExternalDesc.setOnClickListener { syncFromExternalOption.isChecked = true }
            appSpecificStorageDesc.setOnClickListener { appSpecificStorageOption.isChecked = true }
            fun updateInputChoice() {
                if (rendering) return
                val activity = requireActivity() as SetupActivity
                activity.chooseInput(
                    keepCurrent = inputLayoutOptions.checkedRadioButtonId == R.id.keep_current_input_option,
                    layout = if (inputLayoutOptions.checkedRadioButtonId == R.id.nine_key_option) {
                        InputLayout.T9
                    } else {
                        InputLayout.FULL_PINYIN
                    },
                    variant = if (charsetOptions.checkedRadioButtonId == R.id.traditional_option) {
                        ChineseVariant.TRADITIONAL
                    } else {
                        ChineseVariant.SIMPLIFIED
                    },
                )
            }
            inputLayoutOptions.setOnCheckedChangeListener { _, _ -> updateInputChoice() }
            charsetOptions.setOnCheckedChangeListener { _, _ -> updateInputChoice() }
            trialInput.onChineseCommitted = { (requireActivity() as SetupActivity).recordChineseCommit() }
            trialLaterButton.setOnClickListener { (requireActivity() as SetupActivity).deferTrial() }
            advancedButton.setOnClickListener { (requireActivity() as SetupActivity).openAdvancedSettings() }
        }
        sync()
        return binding.root
    }

    // Called on window focus changed
    fun sync() {
        if (!::binding.isInitialized || view == null) return
        val activity = requireActivity() as SetupActivity
        val done = activity.isPageDone(page)
        val isStorageModePage = page == SetupPage.Mode
        val checkedId = when (prefs.dataStorageMode.getValue()) {
            DataStorageMode.EXTERNAL_SYNC -> R.id.sync_from_external_option
            DataStorageMode.APP_STORAGE -> R.id.app_specific_storage_option
        }
        with(binding) {
            storageModeOptions.visibility = if (isStorageModePage) View.VISIBLE else View.GONE
            inputOptions.visibility = if (page == SetupPage.Input) View.VISIBLE else View.GONE
            trialOptions.visibility = if (page == SetupPage.Try) View.VISIBLE else View.GONE
            advancedButton.visibility = if (page == SetupPage.Input || page == SetupPage.Try) View.VISIBLE else View.GONE
            rendering = true
            storageModeOptions.check(checkedId)
            inputLayoutOptions.check(
                if (activity.keepCurrentInput) {
                    R.id.keep_current_input_option
                } else {
                    when (activity.inputChoice.layout) {
                        InputLayout.T9 -> R.id.nine_key_option
                        InputLayout.FULL_PINYIN -> R.id.full_pinyin_option
                    }
                },
            )
            charsetOptions.check(
                if (activity.inputChoice.variant == ChineseVariant.TRADITIONAL) {
                    R.id.traditional_option
                } else {
                    R.id.simplified_option
                },
            )
            rendering = false
            syncFromExternalOption.isEnabled = !activity.changingStorageMode
            appSpecificStorageOption.isEnabled = !activity.changingStorageMode
            syncFromExternalDesc.isEnabled = !activity.changingStorageMode
            appSpecificStorageDesc.isEnabled = !activity.changingStorageMode
            keepCurrentInputOption.isEnabled = !activity.changingInput
            fullPinyinOption.isEnabled = !activity.changingInput
            nineKeyOption.isEnabled = !activity.changingInput
            simplifiedOption.isEnabled = !activity.changingInput && !activity.keepCurrentInput
            traditionalOption.isEnabled = !activity.changingInput && !activity.keepCurrentInput
            inputStatus.text = activity.inputStatus.orEmpty()
            trialInput.isEnabled = activity.isPageDone(SetupPage.Input) && activity.isPageDone(SetupPage.Select)
            trialLaterButton.isEnabled = activity.isPageDone(SetupPage.Input)
            trialLaterButton.visibility = if (done) View.GONE else View.VISIBLE
            trialStatus.text = when {
                SetupProgress.trialCompleted -> getString(R.string.setup__trial_done)
                SetupProgress.deferred -> getString(R.string.setup__trial_deferred)
                else -> ""
            }

            stepText.text = page.getStepText(requireContext())
            hintText.text = page.getHintText(requireContext())
            val showActionButton = (page == SetupPage.Input || page == SetupPage.Try || !done) && page.showActionButton()
            actionButton.visibility = if (showActionButton) View.VISIBLE else View.GONE
            actionButton.isEnabled = !activity.changingStorageMode && !activity.changingInput &&
                when (page) {
                    SetupPage.Input -> activity.isPageDone(SetupPage.Select)
                    SetupPage.Try -> activity.isPageDone(SetupPage.Input) && activity.isPageDone(SetupPage.Select)
                    else -> true
                }
            actionButton.text = page.getButtonText(requireContext())
            actionButton.setOnClickListener { page.getButtonAction(requireActivity()) }
            doneText.visibility = if (done) View.VISIBLE else View.GONE
            doneRow.visibility = if (done) View.VISIBLE else View.GONE
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sync()
        val activity = requireActivity() as SetupActivity
        if (page == SetupPage.Input && activity.currentSetupPage == page) activity.loadInputState()
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (page == SetupPage.Try) {
            (requireActivity() as SetupActivity).restoreTrialFocusIfReady()
            // Fragment state is restored before ViewPager2 necessarily attaches this page.
            view?.doOnAttach { (activity as? SetupActivity)?.restoreTrialFocusIfReady() }
        }
    }

    fun trialInput() = if (::binding.isInitialized && view != null) binding.trialInput else null

    override fun onDestroyView() {
        if (::binding.isInitialized) binding.trialInput.onChineseCommitted = null
        super.onDestroyView()
    }
}
