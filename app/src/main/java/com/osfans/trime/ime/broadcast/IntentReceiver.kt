// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.broadcast

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.PowerManager.PARTIAL_WAKE_LOCK
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.systemservices.alarmManager
import splitties.systemservices.powerManager
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** 接收 Intent 廣播事件  */
class IntentReceiver :
    BroadcastReceiver(),
    CoroutineScope by MainScope() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val command = intent.action ?: return
        Timber.d("Received Command = %s", command)
        when (command) {
            COMMAND_DEPLOY ->
                launch {
                    withContext(Dispatchers.IO) {
                        RimeDaemon.restartRime(true)
                    }
                    context.toast(R.string.deploy_finish, Toast.LENGTH_LONG)
                }
            COMMAND_SYNC ->
                launch {
                    withContext(Dispatchers.IO) {
                        Rime.syncRimeUserData()
                        RimeDaemon.restartRime(true)
                    }
                }
            COMMAND_TIMING_SYNC ->
                launch {
                    withContext(Dispatchers.IO) {
                        // 获取唤醒锁
                        val wakeLock =
                            powerManager.newWakeLock(PARTIAL_WAKE_LOCK, "com.osfans.trime:WakeLock")
                        wakeLock.acquire(600000) // 10分钟超时
                        val cal = Calendar.getInstance()
                        val triggerTime = cal.timeInMillis + TimeUnit.DAYS.toMillis(1) // 下次同步时间
                        AppPrefs.defaultInstance().profile.timingBackgroundSyncSetTime =
                            triggerTime // 更新定时同步偏好值
                        // 设置待发送的同步事件
                        val pendingIntent =
                            PendingIntent.getBroadcast(
                                context,
                                0,
                                Intent(COMMAND_TIMING_SYNC),
                                if (VERSION.SDK_INT >= VERSION_CODES.M) {
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                } else {
                                    PendingIntent.FLAG_UPDATE_CURRENT
                                },
                            )
                        if (VERSION.SDK_INT < VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                            if (VERSION.SDK_INT >= VERSION_CODES.M) { // 根据SDK设置alarm任务
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

                        Rime.syncRimeUserData()
                        RimeDaemon.restartRime(true)
                        wakeLock.release() // 释放唤醒锁
                    }
                }
            else -> return
        }
    }

    fun registerReceiver(context: Context) {
        ContextCompat.registerReceiver(
            context,
            this,
            IntentFilter(COMMAND_DEPLOY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            context,
            this,
            IntentFilter(COMMAND_SYNC),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            context,
            this,
            IntentFilter(COMMAND_TIMING_SYNC),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        context.registerReceiver(this, IntentFilter(Intent.ACTION_SHUTDOWN))
    }

    fun unregisterReceiver(context: Context) {
        context.unregisterReceiver(this)
    }

    companion object {
        private const val COMMAND_DEPLOY = "com.osfans.trime.deploy"
        private const val COMMAND_SYNC = "com.osfans.trime.sync"
        private const val COMMAND_TIMING_SYNC = "com.osfans.trime.timing.sync"
    }
}
