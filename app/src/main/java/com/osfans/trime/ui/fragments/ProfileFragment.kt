// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.fragments

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.get
import com.blankj.utilcode.util.ToastUtils
import com.osfans.trime.R
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.ui.components.FolderPickerPreference
import com.osfans.trime.ui.components.PaddingPreferenceFragment
import com.osfans.trime.ui.main.MainViewModel
import com.osfans.trime.util.ResourceUtils
import com.osfans.trime.util.ShortcutUtils
import com.osfans.trime.util.appContext
import com.osfans.trime.util.formatDateTime
import com.osfans.trime.util.rimeActionWithResultDialog
import com.osfans.trime.util.withLoadingDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.systemservices.alarmManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ProfileFragment :
    PaddingPreferenceFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private val viewModel: MainViewModel by activityViewModels()
    private val prefs get() = AppPrefs.defaultInstance()

    private fun FolderPickerPreference.registerDocumentTreeLauncher() {
        documentTreeLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val context = requireContext()
                    result.data?.data?.also { uri ->
                        val contentResolver = context.contentResolver

                        val takeFlags: Int =
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        contentResolver.takePersistableUriPermission(uri, takeFlags)

                        assignValue(uri.toString())
                    }
                }
            }
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.profile_preference)
        with(preferenceScreen) {
            get<FolderPickerPreference>(AppPrefs.Profile.SHARED_DATA_DIR)?.apply {
                registerDocumentTreeLauncher()
            }
            get<FolderPickerPreference>(AppPrefs.Profile.USER_DATA_DIR)?.apply {
                registerDocumentTreeLauncher()
            }
            get<Preference>("profile_sync_user_data")?.setOnPreferenceClickListener {
                lifecycleScope.launch {
                    this@ProfileFragment.context?.rimeActionWithResultDialog("rime.trime", "W", 1) {
                        viewModel.setToLoading()
                        val result = ShortcutUtils.sync(true)
                        viewModel.setToReady()
                        result
                    }
                }
                true
            }
            get<SwitchPreferenceCompat>(AppPrefs.Profile.SYNC_BACKGROUND_ENABLED)?.apply {
                val lastBackgroundSync = prefs.profile.lastBackgroundSync
                summaryOn =
                    if (lastBackgroundSync.isBlank()) {
                        context.getString(R.string.profile_never_sync_in_background)
                    } else {
                        context.getString(
                            R.string.profile_last_sync_in_background,
                            formatDateTime(lastBackgroundSync.toLong()),
                            context.getString(
                                if (prefs.profile.lastSyncStatus) {
                                    R.string.success
                                } else {
                                    R.string.failure
                                },
                            ),
                        )
                    }
                summaryOff = context.getString(R.string.profile_enable_syncing_in_background)
            }
            get<SwitchPreferenceCompat>(AppPrefs.Profile.TIMING_SYNC_ENABLED)?.apply { // 定时同步偏好描述
                val timingSyncPreference: SwitchPreferenceCompat? = findPreference("profile_timing_sync")
                timingSyncPreference?.summaryProvider =
                    Preference.SummaryProvider<SwitchPreferenceCompat> {
                        if (prefs.profile.timingSyncEnabled) {
                            context.getString(
                                R.string.profile_timing_sync_trigger_time,
                                formatDateTime(prefs.profile.timingSyncTriggerTime),
                            )
                        } else {
                            context.getString(R.string.profile_enable_timing_sync)
                        }
                    }
            }
            get<SwitchPreferenceCompat>(AppPrefs.Profile.TIMING_SYNC_ENABLED)?.setOnPreferenceClickListener { // 监听定时同步偏好设置
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
                val cal = Calendar.getInstance()
                if (get<SwitchPreferenceCompat>(AppPrefs.Profile.TIMING_SYNC_ENABLED)?.isChecked == true) { // 当定时同步偏好打开时
                    val timeSetListener = // 监听时间选择器设置
                        TimePickerDialog.OnTimeSetListener { _, hour, minute ->
                            cal.set(Calendar.HOUR_OF_DAY, hour)
                            cal.set(Calendar.MINUTE, minute)
                            val triggerTime = cal.timeInMillis // 设置的时间
                            if (triggerTime > System.currentTimeMillis() + 1200000L) { // 设置的时间小于当前时间20分钟时将同步推迟到明天
                                prefs.profile.timingSyncTriggerTime = triggerTime // 更新定时同步偏好值
                                if (VERSION.SDK_INT < VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                                    if (VERSION.SDK_INT >= VERSION_CODES.M) { // 根据 API Level 设置 alarm 任务
                                        alarmManager.setExactAndAllowWhileIdle(
                                            AlarmManager.RTC_WAKEUP,
                                            triggerTime,
                                            pendingIntent,
                                        )
                                    } else {
                                        alarmManager.setExact(
                                            AlarmManager.RTC_WAKEUP,
                                            triggerTime,
                                            pendingIntent,
                                        )
                                    }
                                }
                            } else {
                                prefs.profile.timingSyncTriggerTime =
                                    triggerTime + TimeUnit.DAYS.toMillis(1)
                                if (VERSION.SDK_INT < VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                                    if (VERSION.SDK_INT >= VERSION_CODES.M) {
                                        alarmManager.setExactAndAllowWhileIdle(
                                            AlarmManager.RTC_WAKEUP,
                                            triggerTime + TimeUnit.DAYS.toMillis(1),
                                            pendingIntent,
                                        )
                                    } else {
                                        alarmManager.setExact(
                                            AlarmManager.RTC_WAKEUP,
                                            triggerTime + TimeUnit.DAYS.toMillis(1),
                                            pendingIntent,
                                        )
                                    }
                                }
                            }
                        }
                    // 时间选择器设置
                    val tpDialog =
                        TimePickerDialog(
                            context,
                            timeSetListener,
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            true,
                        )
                    tpDialog.setOnCancelListener { // 当取消时间选择器时重置偏好
                        get<SwitchPreferenceCompat>("profile_timing_sync")?.isChecked = false
                    }
                    tpDialog.show()
                } else {
                    alarmManager.cancel(pendingIntent) // 取消alarm任务
                }
                true
            }
            get<Preference>("profile_reset")?.setOnPreferenceClickListener {
                val rimeFolder = "rime"
                val items = appContext.assets.list(rimeFolder)!!
                val checkedItems = items.map { false }.toBooleanArray()
                AlertDialog.Builder(context)
                    .setTitle(R.string.profile_reset)
                    .setMultiChoiceItems(items, checkedItems) { _, id, isChecked ->
                        checkedItems[id] = isChecked
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        var res = true
                        lifecycleScope.withLoadingDialog(context) {
                            withContext(Dispatchers.IO) {
                                res =
                                    items.filterIndexed { index, _ -> checkedItems[index] }
                                        .fold(true) { acc, asset ->
                                            ResourceUtils.copyFiles(
                                                "$rimeFolder/$asset",
                                                DataManager.sharedDataDir,
                                                "$rimeFolder/",
                                            )
                                                .fold({ acc and true }, { acc and false })
                                        }
                            }
                            ToastUtils.showShort(
                                if (res) R.string.reset_success else R.string.reset_failure,
                            )
                        }
                    }.show()
                true
            }
        }
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?,
    ) { // 实时更新定时同步偏好描述
        val timingSyncPreference: SwitchPreferenceCompat? = findPreference("profile_timing_sync")
        when (key) {
            AppPrefs.Profile.TIMING_SYNC_TRIGGER_TIME,
            -> {
                timingSyncPreference?.summaryProvider =
                    Preference.SummaryProvider<SwitchPreferenceCompat> {
                        if (prefs.profile.timingSyncEnabled) {
                            context?.getString(
                                R.string.profile_timing_sync_trigger_time,
                                formatDateTime(prefs.profile.timingSyncTriggerTime),
                            )
                        } else {
                            context?.getString(R.string.profile_enable_timing_sync)
                        }
                    }
            }
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
