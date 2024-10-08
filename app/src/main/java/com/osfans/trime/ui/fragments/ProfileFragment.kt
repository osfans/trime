// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.fragments

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.Preference.SummaryProvider
import androidx.preference.get
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ui.components.FolderPickerPreference
import com.osfans.trime.ui.components.PaddingPreferenceFragment
import com.osfans.trime.ui.components.TimePickerPreference
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.util.ResourceUtils
import com.osfans.trime.util.appContext
import com.osfans.trime.util.formatDateTime
import com.osfans.trime.util.rimeActionWithResultDialog
import com.osfans.trime.util.toast
import com.osfans.trime.util.withLoadingDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.systemservices.alarmManager

class ProfileFragment :
    PaddingPreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val viewModel: MainViewModel by activityViewModels()
    private val prefs get() = AppPrefs.defaultInstance()

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

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.profile_preference)
        with(preferenceScreen) {
            get<FolderPickerPreference>("profile_user_data_dir")?.apply {
                setDefaultValue(DataManager.defaultDataDirectory.path)
                registerDocumentTreeLauncher()
            }
            get<Preference>("profile_sync_user_data")?.setOnPreferenceClickListener {
                lifecycleScope.launch {
                    this@ProfileFragment.context?.rimeActionWithResultDialog("rime.trime", "W", 1) {
                        Rime.syncRimeUserData()
                        RimeDaemon.restartRime(true)
                        true
                    }
                }
                true
            }
            get<TimePickerPreference>("profile_timing_background_sync_set_time")?.apply {
                setDefaultValue(false to System.currentTimeMillis())
                summaryProvider =
                    SummaryProvider<TimePickerPreference> {
                        if (prefs.profile.timingBackgroundSyncEnabled) {
                            val (lastTime, lastStatus) =
                                if (prefs.profile.lastBackgroundSyncTime != 0L) {
                                    formatDateTime(prefs.profile.lastBackgroundSyncTime) to
                                        context.getString(if (prefs.profile.lastSyncStatus) R.string.success else R.string.failure)
                                } else {
                                    "N/A" to "N/A"
                                }
                            context.getString(
                                R.string.profile_timing_background_sync_status,
                                lastTime,
                                lastStatus,
                                formatDateTime(prefs.profile.timingBackgroundSyncSetTime),
                            )
                        } else {
                            context.getString(R.string.profile_timing_background_sync_hint)
                        }
                    }
            }
            get<Preference>("profile_reset")?.setOnPreferenceClickListener {
                val base = "shared"
                val items = appContext.assets.list(base)!!
                val checkedItems = items.map { false }.toBooleanArray()
                AlertDialog
                    .Builder(context)
                    .setTitle(R.string.profile_reset)
                    .setMultiChoiceItems(items, checkedItems) { _, id, isChecked ->
                        checkedItems[id] = isChecked
                    }.setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        var res = true
                        lifecycleScope.withLoadingDialog(context) {
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

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?,
    ) {
        // 监听定时同步偏好设置
        // 设置待发送的同步事件
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent("com.osfans.trime.timing.sync"),
                if (VERSION.SDK_INT >= VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                },
            )
        if (prefs.profile.timingBackgroundSyncEnabled) {
            val timeAtMillis = prefs.profile.timingBackgroundSyncSetTime
            if (VERSION.SDK_INT < VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                if (VERSION.SDK_INT >= VERSION_CODES.M) { // 根据 API Level 设置 alarm 任务
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        timeAtMillis,
                        pendingIntent,
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        timeAtMillis,
                        pendingIntent,
                    )
                }
            }
        } else {
            alarmManager.cancel(pendingIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setToolbarTitle(getString(R.string.pref_profile))
        viewModel.disableTopOptionsMenu()
        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }
}
