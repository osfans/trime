// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.fragments

import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.Preference.SummaryProvider
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.get
import com.osfans.trime.R
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.ui.components.FolderPickerPreference
import com.osfans.trime.ui.components.PaddingPreferenceFragment
import com.osfans.trime.ui.components.withLoadingDialog
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.ui.main.settings.EditTextIntPreference
import com.osfans.trime.util.ResourceUtils
import com.osfans.trime.util.appContext
import com.osfans.trime.util.customFormatTimeInDefault
import com.osfans.trime.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.content.Context
import com.osfans.trime.data.prefs.AppPrefs.Profile


class ProfileFragment : PaddingPreferenceFragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private val prefs = AppPrefs.defaultInstance().profile
    private val backgroundSyncEnable by prefs.periodicBackgroundSync
    private val lastSyncTime by prefs.lastBackgroundSyncTime
    private val lastSyncStatus by prefs.lastBackgroundSyncStatus

    private fun FolderPickerPreference.registerDocumentTreeLauncher() {
        documentTreeLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
                it ?: return@registerForActivityResult
                val uri =
                    DocumentsContract.buildDocumentUriUsingTree(
                        it,
                        DocumentsContract.getTreeDocumentId(it),
                    )
                onResult(uri)
            }
    }

    private val onSyncIntervalChange =
        PreferenceDelegate.OnChangeListener<Int> { _, _ ->
            if (backgroundSyncEnable) {
                viewModel.restartBackgroundSyncWork.value = true
            }
        }
    private suspend fun importData(context: Context) {
        withContext(Dispatchers.IO) {
            val externalDir = File(prefs.userDataDir)
            val internalDir = File(appContext.filesDir, "user_data")
            internalDir.mkdirs()
            externalDir.copyRecursively(internalDir, overwrite = true)
        }
        context.toast(R.string.import_success)
    }
    private val onInternalStorageChange =
        PreferenceDelegate.OnChangeListener<Boolean> { _, newValue ->
            if (newValue && !prefs.internalStorageFirstImport) {
                // 只在首次开启内部存储模式时询问用户是否导入数据
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.import_data_title)
                    .setMessage(R.string.import_data_message)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        lifecycleScope.withLoadingDialog(requireContext()) {
                            importData(requireContext())
                            // 设置首次导入标记为 true
                            prefs.internalStorageFirstImport  = true
                        }
                    }
                    .setNegativeButton(R.string.no) { _, _ ->
                        // 用户选择不导入数据，也设置首次导入标记为 true
                        prefs.internalStorageFirstImport = true
                    }
                    .show()
            }
        }
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.profile_preference)
        prefs.periodicBackgroundSyncInterval.registerOnChangeListener(onSyncIntervalChange)
        val useInternalStorageDelegate = prefs.preferenceDelegates[Profile.USE_INTERNAL_STORAGE] as? PreferenceDelegate<Boolean>
        useInternalStorageDelegate?.registerOnChangeListener(onInternalStorageChange)
        with(preferenceScreen) {
            get<Preference>("import_user_data")?.setOnPreferenceClickListener {
                lifecycleScope.launch {
                    val externalDir = File(prefs.userDataDir)
                    val internalDir = File(appContext.filesDir, "user_data")

                    lifecycleScope.withLoadingDialog(requireContext()) {
                        withContext(Dispatchers.IO) {
                            internalDir.mkdirs()
                            externalDir.copyRecursively(internalDir, overwrite = true)
                            context.toast(R.string.import_success)
                        }
                    }
                }
                true
            }
            get<Preference>("export_user_data")?.setOnPreferenceClickListener {
                lifecycleScope.launch {
                    val internalDir = File(appContext.filesDir, "user_data")
                    val externalDir = File(prefs.userDataDir)

                    lifecycleScope.withLoadingDialog(requireContext()) {
                        withContext(Dispatchers.IO) {
                            if (internalDir.exists()) {
                                internalDir.copyRecursively(externalDir, overwrite = true)
                                context.toast(R.string.export_success)
                            } else {
                                context.toast(R.string.export_failure)
                            }
                        }
                    }
                }
                true
            }
            get<FolderPickerPreference>("profile_user_data_dir")?.apply {
                setDefaultValue(DataManager.defaultDataDirectory.path)
                registerDocumentTreeLauncher()
            }
            get<Preference>("sync_user_data")?.setOnPreferenceClickListener {
                lifecycleScope.launch {
                    viewModel.rime.launchOnReady { it.syncUserData() }
                }
                true
            }
            get<SwitchPreferenceCompat>("periodic_background_sync")?.apply {
                summaryProvider =
                    SummaryProvider<SwitchPreferenceCompat> {
                        if (backgroundSyncEnable) {
                            getString(
                                R.string.periodic_background_sync_status,
                                customFormatTimeInDefault("yyyy-MM-dd HH:mm", lastSyncTime),
                                if (lastSyncStatus) getString(R.string.success) else getString(R.string.failure),
                            )
                        } else {
                            ""
                        }
                    }
            }
            get<EditTextIntPreference>("periodic_background_sync_interval")?.apply {
                min = 15
                summaryProvider = EditTextIntPreference.SimpleSummaryProvider
            }
            get<Preference>("profile_reset")?.setOnPreferenceClickListener {
                val base = "shared"
                val items = appContext.assets.list(base)!!
                val checkedItems = items.map { false }.toBooleanArray()
                AlertDialog
                    .Builder(requireContext())
                    .setTitle(R.string.profile_reset)
                    .setMultiChoiceItems(items, checkedItems) { _, id, isChecked ->
                        checkedItems[id] = isChecked
                    }.setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        var res = true
                        lifecycleScope.withLoadingDialog(requireContext()) {
                            withContext(Dispatchers.IO) {
                                res =
                                    items
                                        .filterIndexed { index, _ -> checkedItems[index] }
                                        .fold(true) { acc, asset ->
                                            val destPath = DataManager.sharedDataDir.resolve(asset).absolutePath
                                            ResourceUtils
                                                .copyFile("$base/$asset", destPath)
                                                .fold({ acc and true }, { acc and false })
                                        }
                            }
                            context.toast((if (res) R.string.reset_success else R.string.reset_failure))
                        }
                    }.show()
                true
            }
        }
    }

    override fun onPause() {
        super.onPause()
        prefs.periodicBackgroundSyncInterval.unregisterOnChangeListener(onSyncIntervalChange)
        val useInternalStorageDelegate = prefs.preferenceDelegates[Profile.USE_INTERNAL_STORAGE] as PreferenceDelegate<Boolean>
        useInternalStorageDelegate.unregisterOnChangeListener(onInternalStorageChange)

    }
}
