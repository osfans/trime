/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.osfans.trime.R
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.data.sync.RimeDataSync
import com.osfans.trime.data.sync.UserDbMigration
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import com.osfans.trime.ui.common.withLoadingDialog
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.util.ResourceUtils
import com.osfans.trime.util.addCategory
import com.osfans.trime.util.addPreference
import com.osfans.trime.util.customFormatTimeInDefault
import com.osfans.trime.util.launchBrowseAppRimeDataDir
import com.osfans.trime.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileSettingsFragment : PaddingPreferenceFragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private val prefs = AppPrefs.Companion.defaultInstance().profile
    private val backgroundSyncEnable = prefs.periodicBackgroundSync
    private val lastSyncTime by prefs.lastBackgroundSyncTime
    private val lastSyncStatus by prefs.lastBackgroundSyncStatus

    private var pendingPickerCancelToAppStorage = false
    private var pendingResetDataPath = false

    private val onBackgroundSyncEnable = PreferenceDelegate.OnChangeListener<Boolean> { _, v ->
        editSyncIntervalPreference.isEnabled = v
    }

    private val onSyncIntervalChange =
        PreferenceDelegate.OnChangeListener<Int> { _, _ ->
            if (backgroundSyncEnable.getValue()) {
                viewModel.restartBackgroundSyncWork.value = true
            }
        }

    private val onDataPathChange = PreferenceDelegate.OnChangeListener<String> { _, _ ->
        updateDataPathSummary()
    }

    private val onStorageModeChange =
        PreferenceDelegate.OnChangeListener<AppPrefs.Profile.DataStorageMode> { _, _ ->
            updateStorageModeUi()
        }

    private val dataPathPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) {
                when {
                    pendingResetDataPath -> promptResetDataPathCancelled()
                    pendingPickerCancelToAppStorage -> fallbackToAppStorage()
                }
                return@registerForActivityResult
            }
            handleTreePicked(uri, pendingPickerCancelToAppStorage)
        }

    private lateinit var editSyncIntervalPreference: EditTextIntPreference
    private lateinit var dataPathPreference: Preference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs.periodicBackgroundSync.registerOnChangeListener(onBackgroundSyncEnable)
        prefs.periodicBackgroundSyncInterval.registerOnChangeListener(onSyncIntervalChange)
        prefs.externalRimeTreeUri.registerOnChangeListener(onDataPathChange)
        prefs.externalRimeDisplayName.registerOnChangeListener(onDataPathChange)
        prefs.dataStorageMode.registerOnChangeListener(onStorageModeChange)
    }

    private fun dataPathSummary(): String {
        val displayName = prefs.externalRimeDisplayName.getValue()
        if (displayName.isNotEmpty()) return displayName
        val uri = prefs.externalRimeTreeUri.getValue()
        if (uri.isNotEmpty()) return uri
        return getString(R.string.data_path_not_selected)
    }

    private fun updateDataPathSummary() {
        findPreference<Preference>(AppPrefs.Profile.EXTERNAL_RIME_TREE_URI)?.summary = dataPathSummary()
    }

    private fun updateStorageModeUi() {
        val externalSync = RimeDataSync.usesExternalSync()
        if (::dataPathPreference.isInitialized) {
            dataPathPreference.isEnabled = externalSync
        }
        findPreference<ListPreference>(AppPrefs.Profile.DATA_STORAGE_MODE)?.value =
            prefs.dataStorageMode.getValue().name
    }

    private fun launchDataPathPicker(cancelToAppStorage: Boolean) {
        pendingPickerCancelToAppStorage = cancelToAppStorage
        pendingResetDataPath = false
        dataPathPicker.launch(null as Uri?)
    }

    private fun launchResetDataPathPicker() {
        pendingPickerCancelToAppStorage = false
        pendingResetDataPath = true
        dataPathPicker.launch(null as Uri?)
    }

    private fun handleTreePicked(
        uri: Uri,
        onCancelToAppStorage: Boolean,
    ) {
        val ctx = requireContext()
        lifecycleScope.launch {
            withLoadingDialog(ctx) {
                runCatching {
                    withContext(Dispatchers.IO) {
                        RimeDataSync.persistTreeUri(ctx, uri)
                        RimeDataSync.importToLocal(ctx).getOrThrow()
                        viewModel.rime.runOnReady { deploy(skipImport = true) }
                    }
                }.onSuccess {
                    updateDataPathSummary()
                    ctx.toast(R.string.setup__data_path_imported)
                }.onFailure {
                    if (onCancelToAppStorage) {
                        fallbackToAppStorage()
                    } else {
                        ctx.toast(R.string.setup__data_path_import_failed)
                    }
                }
            }
        }
    }

    private fun promptSelectAnotherDirectory() {
        AlertDialog
            .Builder(requireContext())
            .setMessage(R.string.select_another_directory_to_sync)
            .setPositiveButton(R.string.select_another_directory) { _, _ ->
                RimeDataSync.clearExternalTree(requireContext())
                updateDataPathSummary()
                launchResetDataPathPicker()
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptResetDataPathCancelled() {
        AlertDialog
            .Builder(requireContext())
            .setMessage(R.string.reset_data_path_cancelled_message)
            .setPositiveButton(R.string.reset_data_path_pick_again) { _, _ ->
                launchResetDataPathPicker()
            }.setNegativeButton(R.string.reset_data_path_use_app_storage) { _, _ ->
                fallbackToAppStorage()
            }.setOnCancelListener {
                launchResetDataPathPicker()
            }.show()
    }

    private fun promptExternalSyncFolderSelection() {
        val ctx = requireContext()
        AlertDialog
            .Builder(ctx)
            .setMessage(R.string.external_sync_select_folder_message)
            .setPositiveButton(R.string.setup__select_data_path) { _, _ ->
                launchDataPathPicker(cancelToAppStorage = true)
            }.setNegativeButton(android.R.string.cancel) { _, _ ->
                fallbackToAppStorage()
            }.setOnCancelListener {
                fallbackToAppStorage()
            }.show()
    }

    private fun fallbackToAppStorage() {
        RimeDataSync.clearExternalTree(requireContext())
        UserDbMigration.onStorageModeChanged(
            AppPrefs.Profile.DataStorageMode.EXTERNAL_SYNC,
            AppPrefs.Profile.DataStorageMode.APP_STORAGE,
        )
        prefs.dataStorageMode.setValue(AppPrefs.Profile.DataStorageMode.APP_STORAGE)
        updateStorageModeUi()
        updateDataPathSummary()
        AlertDialog
            .Builder(requireContext())
            .setMessage(R.string.external_sync_fallback_app_storage)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        val ctx = requireContext()
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            addCategory(R.string.storage) {
                isIconSpaceReserved = false
                val storageModes = AppPrefs.Profile.DataStorageMode.entries
                addPreference(
                    ListPreference(ctx).apply {
                        key = AppPrefs.Profile.DATA_STORAGE_MODE
                        isIconSpaceReserved = false
                        setTitle(R.string.data_storage_mode)
                        entries = storageModes.map { getString(it.stringRes) }.toTypedArray()
                        entryValues = storageModes.map { it.name }.toTypedArray()
                        value = prefs.dataStorageMode.getValue().name
                        summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                        setOnPreferenceChangeListener { _, newValue ->
                            val oldMode = prefs.dataStorageMode.getValue()
                            val mode =
                                AppPrefs.Profile.DataStorageMode.valueOf(newValue as String)
                            UserDbMigration.onStorageModeChanged(oldMode, mode)
                            prefs.dataStorageMode.setValue(mode)
                            if (
                                oldMode == AppPrefs.Profile.DataStorageMode.APP_STORAGE &&
                                mode == AppPrefs.Profile.DataStorageMode.EXTERNAL_SYNC &&
                                !RimeDataSync.hasExternalAccess()
                            ) {
                                promptExternalSyncFolderSelection()
                            } else if (
                                oldMode == AppPrefs.Profile.DataStorageMode.EXTERNAL_SYNC &&
                                mode == AppPrefs.Profile.DataStorageMode.APP_STORAGE
                            ) {
                                RimeDataSync.clearExternalTree(ctx)
                                updateDataPathSummary()
                            }
                            true
                        }
                    },
                )
                addPreference(
                    Preference(requireContext()).apply {
                        dataPathPreference = this
                        key = AppPrefs.Profile.EXTERNAL_RIME_TREE_URI
                        isIconSpaceReserved = false
                        setTitle(R.string.user_data_dir)
                        summary = dataPathSummary()
                        setOnPreferenceClickListener {
                            promptSelectAnotherDirectory()
                            true
                        }
                    },
                )
            }
            addCategory(R.string.synchronization) {
                isIconSpaceReserved = false
                addPreference(R.string.sync_user_data_immediately) {
                    lifecycleScope.launch {
                        withLoadingDialog(ctx) {
                            runCatching {
                                RimeDataSync.syncUserDataWithOptionalExport(ctx) {
                                    viewModel.rime.runOnReady { syncUserData() }
                                }
                            }.onSuccess { success ->
                                ctx.toast(
                                    when {
                                        !success -> R.string.sync_user_data_failure
                                        RimeDataSync.usesExternalSync(ctx) ->
                                            R.string.sync_user_data_success_external
                                        else -> R.string.sync_user_data_success
                                    },
                                )
                            }.onFailure {
                                ctx.toast(R.string.sync_user_data_failure)
                            }
                        }
                    }
                }
                addPreference(
                    SwitchPreferenceCompat(ctx).apply {
                        key = AppPrefs.Profile.PERIODIC_BACKGROUND_SYNC
                        isIconSpaceReserved = false
                        setTitle(R.string.periodic_background_sync)
                        setDefaultValue(false)
                        summaryProvider = Preference.SummaryProvider<SwitchPreferenceCompat> {
                            if (backgroundSyncEnable.getValue()) {
                                val lastTime: String
                                val lastStatus: String
                                if (lastSyncTime != 0L) {
                                    lastTime = customFormatTimeInDefault("yyyy-MM-dd HH:mm", lastSyncTime)
                                    lastStatus = getString(if (lastSyncStatus) R.string.success else R.string.failure)
                                } else {
                                    lastTime = "N/A"
                                    lastStatus = "N/A"
                                }
                                getString(
                                    R.string.periodic_background_sync_status,
                                    lastTime,
                                    lastStatus,
                                )
                            } else {
                                ""
                            }
                        }
                    },
                )
                addPreference(
                    EditTextIntPreference(ctx).apply {
                        editSyncIntervalPreference = this
                        key = AppPrefs.Profile.PERIODIC_BACKGROUND_SYNC_INTERVAL
                        isIconSpaceReserved = false
                        setTitle(R.string.periodic_background_sync_interval)
                        min = 15
                        setDefaultValue(30)
                        summaryProvider = EditTextIntPreference.SimpleSummaryProvider
                        isEnabled = backgroundSyncEnable.getValue()
                    },
                )
            }
            addCategory(R.string.maintenance) {
                isIconSpaceReserved = false
                addPreference(
                    title = getString(R.string.browse_app_data_dir),
                    summary = DataManager.userDataDir.absolutePath,
                ) {
                    ctx.launchBrowseAppRimeDataDir()
                }
                addPreference(R.string.reset, R.string.reset_hint) {
                    val items = ctx.assets.list("shared") ?: return@addPreference
                    val checked = BooleanArray(items.size) { false }
                    AlertDialog
                        .Builder(ctx)
                        .setTitle(R.string.reset)
                        .setMultiChoiceItems(items, checked) { _, id, isChecked ->
                            checked[id] = isChecked
                        }.setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            lifecycleScope.launch {
                                var res = true
                                withLoadingDialog(ctx) {
                                    withContext(Dispatchers.IO) {
                                        res =
                                            items
                                                .filterIndexed { index, _ -> checked[index] }
                                                .fold(true) { acc, asset ->
                                                    val destPath =
                                                        DataManager.sharedDataDir.resolve(asset).absolutePath
                                                    ResourceUtils
                                                        .copyFile("shared/$asset", destPath)
                                                        .fold({ acc and true }, { acc and false })
                                                }
                                    }
                                }
                                ctx.toast((if (res) R.string.reset_success else R.string.reset_failure))
                            }
                        }.show()
                }
            }
        }
        updateStorageModeUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.periodicBackgroundSync.unregisterOnChangeListener(onBackgroundSyncEnable)
        prefs.periodicBackgroundSyncInterval.unregisterOnChangeListener(onSyncIntervalChange)
        prefs.externalRimeTreeUri.unregisterOnChangeListener(onDataPathChange)
        prefs.externalRimeDisplayName.unregisterOnChangeListener(onDataPathChange)
        prefs.dataStorageMode.unregisterOnChangeListener(onStorageModeChange)
    }

    override fun onResume() {
        super.onResume()
        updateStorageModeUi()
        val ctx = requireContext()
        if (
            RimeDataSync.usesExternalSync(ctx) &&
            prefs.externalRimeTreeUri.getValue().isNotEmpty() &&
            !RimeDataSync.hasExternalAccess(ctx)
        ) {
            ctx.toast(R.string.data_path_permission_revoked)
        }
    }
}
