/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings

import android.os.Bundle
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.osfans.trime.R
import com.osfans.trime.core.RimeMaintenanceMutex
import com.osfans.trime.core.data.ConfigurationImportException
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.core.data.DataStorageMode
import com.osfans.trime.core.data.UserDataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.prefs.PreferenceDelegate
import com.osfans.trime.ui.common.PaddingPreferenceFragment
import com.osfans.trime.ui.common.dialog.PublicAlertDialogs
import com.osfans.trime.ui.common.withLoadingDialog
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.util.ResourceUtils
import com.osfans.trime.util.addCategory
import com.osfans.trime.util.addPreference
import com.osfans.trime.util.buildDocumentsProviderIntent
import com.osfans.trime.util.customFormatTimeInDefault
import com.osfans.trime.util.toast
import io.github.whiredplanck.storageaccess.StorageAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class ProfileSettingsFragment : PaddingPreferenceFragment() {
    private val viewModel: MainViewModel by activityViewModels()
    private val prefs = AppPrefs.defaultInstance().profile
    private var dataStorageMode by prefs.dataStorageMode
    private val externalRimeDir by prefs.externalRimeTreeUri
    private val backgroundSyncEnable = prefs.periodicBackgroundSync
    private val lastSyncTime by prefs.lastBackgroundSyncTime
    private val lastSyncStatus by prefs.lastBackgroundSyncStatus

    /** Set while the picker runs as part of switching the storage mode to external sync. */
    private var pendingExternalSyncSetup = false
    private var storageBusy = false

    private fun runStorageChange(block: suspend () -> Unit) {
        if (storageBusy) return
        storageBusy = true
        updateStorageModeUi()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Unable to change storage settings")
                context?.toast(R.string.setup__data_path_import_failed)
            } finally {
                storageBusy = false
                updateStorageModeUi()
            }
        }
    }

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
        PreferenceDelegate.OnChangeListener<DataStorageMode> { _, _ ->
            updateStorageModeUi()
        }

    private lateinit var dataStorageModePreference: ListPreference
    private lateinit var dataPathPreference: Preference

    private lateinit var editSyncIntervalPreference: EditTextIntPreference

    private val storageAccess = StorageAccess(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs.periodicBackgroundSync.registerOnChangeListener(onBackgroundSyncEnable)
        prefs.periodicBackgroundSyncInterval.registerOnChangeListener(onSyncIntervalChange)
        prefs.externalRimeTreeUri.registerOnChangeListener(onDataPathChange)
        prefs.dataStorageMode.registerOnChangeListener(onStorageModeChange)
    }

    private fun provideDataPathSummary(): String? {
        if (externalRimeDir.isEmpty()) return null
        return runCatching {
            val dirUri = externalRimeDir.toUri()
            val docId = DocumentsContract.getTreeDocumentId(dirUri)
            if (docId.contains(':')) {
                val (volume, rel) = docId.split(':', limit = 2)
                when (volume) {
                    "raw" -> rel
                    "primary" -> "/$rel"
                    else -> "/storage/$volume/$rel"
                }
            } else {
                docId
            }
        }.getOrNull()?.removePrefix("/storage/emulated/0") ?: externalRimeDir
    }

    private fun updateDataPathSummary() {
        if (::dataPathPreference.isInitialized) {
            dataPathPreference.summary = provideDataPathSummary()
        }
    }

    private fun updateStorageModeUi() {
        if (::dataStorageModePreference.isInitialized) {
            dataStorageModePreference.value = dataStorageMode.name
            dataStorageModePreference.isEnabled = !storageBusy
        }
        if (::dataPathPreference.isInitialized) {
            dataPathPreference.isEnabled =
                dataStorageMode == DataStorageMode.EXTERNAL_SYNC && !storageBusy
        }
    }

    private fun pickDataPath() {
        if (storageBusy) return
        lifecycleScope.launch {
            val picked = try {
                storageAccess.pickDirectory()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Unable to pick data folder")
                context?.toast(R.string.setup__data_path_import_failed)
                return@launch
            }
            if (picked == null) {
                // Cancelling the picker never nags: the current folder stays as it is, and a
                // storage mode switch the user did not finish setting up is undone silently.
                if (pendingExternalSyncSetup) {
                    pendingExternalSyncSetup = false
                    cancelExternalSyncSetup()
                }
                return@launch
            }
            val ctx = requireContext()
            pendingExternalSyncSetup = false
            runStorageChange {
                withLoadingDialog(ctx) {
                    try {
                        val stats = withContext(Dispatchers.IO) {
                            UserDataManager.importConfigurationWithSession(viewModel.rime, picked.uri.toString())
                        }
                        updateDataPathSummary()
                        ctx.toast(ctx.getString(R.string.configuration_import_validated, stats.validatedConfigs, stats.copied + stats.deleted))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "Unable to validate or activate selected data folder")
                        updateDataPathSummary()
                        val failure = e as? ConfigurationImportException
                        val message = when {
                            failure?.previousConfigurationRestored == true -> getString(R.string.configuration_import_rejected)
                            failure?.recoveryDirectory != null -> getString(R.string.configuration_import_recovery_needed, failure.recoveryDirectory)
                            failure != null -> getString(R.string.configuration_import_recovery_failed)
                            else -> getString(R.string.setup__data_path_import_failed)
                        }
                        withContext(Dispatchers.Main.immediate) {
                            if (PublicAlertDialogs.canShow(ctx)) {
                                PublicAlertDialogs.builder(ctx).setMessage(message).setPositiveButton(android.R.string.ok, null).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun promptSelectAnotherDirectory() {
        PublicAlertDialogs
            .builder(requireContext())
            .setMessage(R.string.select_another_directory_to_sync)
            .setPositiveButton(R.string.select_another_directory) { _, _ ->
                // The current folder stays in place until the user picks another one.
                pickDataPath()
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptExternalSyncFolderSelection() {
        val ctx = requireContext()
        PublicAlertDialogs
            .builder(ctx)
            .setMessage(R.string.external_sync_select_folder_message)
            .setPositiveButton(R.string.setup__select_data_path) { _, _ ->
                pendingExternalSyncSetup = true
                pickDataPath()
            }.setNegativeButton(android.R.string.cancel) { _, _ ->
                cancelExternalSyncSetup()
            }.setOnCancelListener {
                cancelExternalSyncSetup()
            }.show()
    }

    /** Cancelling the picker keeps the previously selected storage mode and directory. */
    private fun cancelExternalSyncSetup() {
        pendingExternalSyncSetup = false
        updateStorageModeUi()
        updateDataPathSummary()
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        val ctx = requireContext()
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            addCategory(R.string.storage) {
                isIconSpaceReserved = false
                val storageModes = DataStorageMode.entries
                addPreference(
                    ListPreference(ctx).apply {
                        dataStorageModePreference = this
                        key = AppPrefs.Profile.DATA_STORAGE_MODE
                        isIconSpaceReserved = false
                        setTitle(R.string.data_storage_mode)
                        entries = storageModes.map { getString(it.stringRes) }.toTypedArray()
                        entryValues = storageModes.map { it.name }.toTypedArray()
                        value = dataStorageMode.name
                        summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
                        setOnPreferenceChangeListener { _, newValue ->
                            val mode =
                                DataStorageMode.valueOf(newValue as String)
                            runStorageChange {
                                val needsPicker = withContext(Dispatchers.IO) {
                                    RimeMaintenanceMutex.withLock {
                                        val needsPicker = mode == DataStorageMode.EXTERNAL_SYNC &&
                                            !UserDataManager.hasExternalAccess()
                                        if (!needsPicker) prefs.dataStorageMode.setValue(mode)
                                        needsPicker
                                    }
                                }
                                updateDataPathSummary()
                                if (needsPicker) {
                                    withContext(Dispatchers.Main.immediate) {
                                        if (PublicAlertDialogs.canShow(ctx)) promptExternalSyncFolderSelection()
                                    }
                                }
                            }
                            false
                        }
                    },
                )
                addPreference(
                    Preference(ctx).apply {
                        dataPathPreference = this
                        key = AppPrefs.Profile.EXTERNAL_RIME_TREE_URI
                        isIconSpaceReserved = false
                        setTitle(R.string.user_data_dir)
                        summary = provideDataPathSummary()
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
                                viewModel.rime.runOnReady { syncUserData() }
                            }.onSuccess { success ->
                                ctx.toast(
                                    when {
                                        !success -> R.string.sync_user_data_failure

                                        UserDataManager.usesExternalSync() ->
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
                    runCatching {
                        ctx.startActivity(buildDocumentsProviderIntent())
                    }.onFailure {
                        ctx.toast(R.string.browse_app_data_dir_failed)
                    }
                }
                addPreference(R.string.reset, R.string.reset_hint) {
                    val items = ctx.assets.list("shared") ?: return@addPreference
                    val checked = BooleanArray(items.size) { false }
                    PublicAlertDialogs
                        .builder(ctx)
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
        prefs.dataStorageMode.unregisterOnChangeListener(onStorageModeChange)
    }

    override fun onResume() {
        super.onResume()
        updateStorageModeUi()
        val ctx = requireContext()
        if (
            UserDataManager.usesExternalSync() &&
            prefs.externalRimeTreeUri.getValue().isNotBlank() &&
            !UserDataManager.hasExternalAccess()
        ) {
            ctx.toast(R.string.data_path_permission_revoked)
        }
    }
}
