/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ui.main.settings

import android.os.Bundle
import android.view.View
import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.ChineseVariant
import com.osfans.trime.data.prefs.InputLayout
import com.osfans.trime.data.prefs.InputSetupController
import com.osfans.trime.data.prefs.InputSetupException
import com.osfans.trime.data.prefs.InputSetupFailure
import com.osfans.trime.data.prefs.InputSetupSelection
import com.osfans.trime.data.prefs.InputSetupState
import com.osfans.trime.data.prefs.PreferenceDelegateProvider
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.ui.main.NavigationRoute
import com.osfans.trime.util.addCategory
import com.osfans.trime.util.addPreference
import com.osfans.trime.util.navigateWithAnim
import com.osfans.trime.util.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class QuickSettingsFragment : PaddingPreferenceFragment() {
    private val prefs = AppPrefs.defaultInstance()
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var layoutPreference: ListPreference
    private lateinit var variantPreference: ListPreference
    private var state: InputSetupState? = null
    private var changingInput = false
    private var inputReadFailed = false

    @Keep
    private val keyboardPreferencesChanged = PreferenceDelegateProvider.OnChangeListener {
        viewLifecycleOwner.lifecycleScope.launch { refreshKeyboardDependencies() }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext()).apply {
            addCategory(R.string.quick_settings_input) {
                layoutPreference = ListPreference(context).apply {
                    key = "quick_input_layout"
                    isPersistent = false
                    isIconSpaceReserved = false
                    setTitle(R.string.quick_settings_layout)
                    setDialogTitle(R.string.quick_settings_layout)
                    entries = arrayOf(getString(R.string.quick_settings_t9), getString(R.string.quick_settings_full_pinyin))
                    entryValues = InputLayout.entries.map { it.name }.toTypedArray()
                    setOnPreferenceChangeListener { _, value ->
                        if (!changingInput) {
                            changeInput(
                                InputSetupSelection(
                                    InputLayout.valueOf(value.toString()),
                                    state?.selection?.variant ?: ChineseVariant.SIMPLIFIED,
                                ),
                            )
                        }
                        false // Only a confirmed native selection updates the displayed value.
                    }
                }
                addPreference(layoutPreference)
                variantPreference = ListPreference(context).apply {
                    key = "quick_chinese_variant"
                    isPersistent = false
                    isIconSpaceReserved = false
                    setTitle(R.string.quick_settings_variant)
                    setDialogTitle(R.string.quick_settings_variant)
                    entries = arrayOf(getString(R.string.quick_settings_simplified), getString(R.string.quick_settings_traditional))
                    entryValues = ChineseVariant.entries.map { it.name }.toTypedArray()
                    setOnPreferenceChangeListener { _, value ->
                        val selected = state?.selection
                        if (!changingInput && selected != null) {
                            changeInput(selected.copy(variant = ChineseVariant.valueOf(value.toString())))
                        }
                        false
                    }
                }
                addPreference(variantPreference)
                addDestination(R.string.schemata, NavigationRoute.SchemaList)
            }
            addCategory(R.string.quick_settings_appearance) {
                addExisting(prefs.candidates, AppPrefs.Candidates.TEXT_SIZE)
                addExisting(prefs.candidates, "candidate_show_comments")
                addExisting(prefs.candidates, "candidate_horizontal_scroll")
                addExisting(prefs.keyboard, AppPrefs.Keyboard.KEYBOARD_HEIGHT)
                addExisting(prefs.keyboard, AppPrefs.Keyboard.T9_ONE_HAND)
            }
            addCategory(R.string.quick_settings_feedback) {
                addExisting(prefs.keyboard, AppPrefs.Keyboard.VIBRATE_ON_KEY_PRESS)
                addExisting(prefs.keyboard, AppPrefs.Keyboard.VIBRATION_DURATION)
                addExisting(prefs.keyboard, AppPrefs.Keyboard.VIBRATION_AMPLITUDE)
                addExisting(prefs.keyboard, AppPrefs.Keyboard.SOUND_ON_KEYPRESS)
            }
            addCategory(R.string.quick_settings_fuzzy) {
                addPreference(
                    Preference(context).apply {
                        setTitle(R.string.quick_settings_fuzzy_hint)
                        isSelectable = false
                        isIconSpaceReserved = false
                        isSingleLineTitle = false
                    },
                )
                prefs.keyboard.t9AssistOptions.take(6).forEach { addExisting(prefs.keyboard, it.key) }
            }
            addCategory(R.string.quick_settings_dictionary) {
                addExisting(prefs.candidates, AppPrefs.Candidates.NEXT_WORD_PREDICTION)
                addDestination(R.string.user_dictionary, NavigationRoute.UserDict)
                addDestination(R.string.profile, NavigationRoute.Profile)
            }
            addCategory(R.string.advanced) {
                addDestination(R.string.virtual_keyboard, NavigationRoute.VirtualKeyboard)
                addDestination(R.string.candidates_window, NavigationRoute.CandidatesWindow)
                addDestination(R.string.theme, NavigationRoute.Theme)
                addDestination(R.string.advanced, NavigationRoute.Advanced)
            }
        }
        updateInputUi()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs.keyboard.registerOnChangeListener(keyboardPreferencesChanged)
        refreshKeyboardDependencies()
    }

    private fun PreferenceGroup.addExisting(provider: PreferenceDelegateProvider, key: String) {
        val ui = provider.preferenceDelegatesUi.first { it.key == key }
        addPreference(ui.createUi(context).apply { isEnabled = ui.isEnabled() })
    }

    private fun PreferenceGroup.addDestination(@StringRes title: Int, route: NavigationRoute) {
        addPreference(title) { findNavController().navigateWithAnim(route) }
    }

    private fun refreshKeyboardDependencies() {
        prefs.keyboard.preferenceDelegatesUi.forEach { ui ->
            findPreference<Preference>(ui.key)?.isEnabled = ui.isEnabled()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!changingInput) readInputState()
    }

    private fun readInputState() {
        changingInput = true
        inputReadFailed = false
        updateInputUi()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                state = viewModel.rime.runOnReady { InputSetupController.inspect(this) }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                state = null
                inputReadFailed = true
                context?.toast(R.string.quick_settings_read_failed)
            } finally {
                changingInput = false
                if (isAdded && view != null) updateInputUi()
            }
        }
    }

    private fun changeInput(selection: InputSetupSelection) {
        changingInput = true
        updateInputUi()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                state = viewModel.rime.runOnReady { InputSetupController.apply(this, selection) }
                context?.toast(R.string.quick_settings_applied)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: InputSetupException) {
                if (!failure.previousRestored) {
                    state = null
                    inputReadFailed = true
                }
                val message = when {
                    !failure.previousRestored -> R.string.quick_settings_restore_failed
                    failure.reason == InputSetupFailure.UNAVAILABLE_SCHEMA -> R.string.quick_settings_schema_unavailable
                    failure.reason == InputSetupFailure.PENDING_INPUT -> R.string.quick_settings_pending_input
                    failure.reason == InputSetupFailure.UNSUPPORTED_CONFIGURATION -> R.string.quick_settings_unsupported
                    else -> R.string.quick_settings_apply_failed
                }
                context?.toast(message)
            } catch (_: Exception) {
                context?.toast(R.string.quick_settings_apply_failed)
            } finally {
                changingInput = false
                if (isAdded && view != null) updateInputUi()
            }
        }
    }

    private fun updateInputUi() {
        val selected = state?.selection
        layoutPreference.value = selected?.layout?.name
        variantPreference.value = selected?.variant?.name
        layoutPreference.summary = if (selected != null) {
            layoutPreference.entry
        } else {
            state?.schemaName?.takeIf { it.isNotBlank() }
                ?: state?.schemaId?.takeIf { it.isNotBlank() }
                ?: getString(if (inputReadFailed) R.string.quick_settings_read_failed else R.string.quick_settings_loading)
        }
        variantPreference.summary = variantPreference.entry ?: getString(R.string.quick_settings_keep_custom)
        layoutPreference.isEnabled = !changingInput && state != null
        variantPreference.isEnabled = !changingInput && selected != null
    }

    override fun onDestroyView() {
        prefs.keyboard.unregisterOnChangeListener(keyboardPreferencesChanged)
        super.onDestroyView()
    }
}
