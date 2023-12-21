/*
 * Copyright (C) 2015-present, osfans
 * waxaca@163.com https://github.com/osfans
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.osfans.trime.ime.broadcast

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.POWER_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.PowerManager
import android.os.PowerManager.PARTIAL_WAKE_LOCK
import com.blankj.utilcode.util.ToastUtils
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.ime.core.RimeWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** 接收 Intent 廣播事件  */
class IntentReceiver : BroadcastReceiver(), CoroutineScope by MainScope() {
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
                        RimeWrapper.deploy()
                    }
                    ToastUtils.showLong(R.string.deploy_finish)
                }
            COMMAND_SYNC ->
                launch {
                    withContext(Dispatchers.IO) {
                        Rime.syncRimeUserData()
                        RimeWrapper.deploy()
                    }
                }
            COMMAND_TIMING_SYNC ->
                launch {
                    withContext(Dispatchers.IO) {
                        // 获取唤醒锁
                        val powerManager = context.getSystemService(POWER_SERVICE) as PowerManager
                        val wakeLock =
                            powerManager.newWakeLock(PARTIAL_WAKE_LOCK, "com.osfans.trime:WakeLock")
                        wakeLock.acquire(600000) // 10分钟超时
                        val cal = Calendar.getInstance()
                        val triggerTime = cal.timeInMillis + TimeUnit.DAYS.toMillis(1) // 下次同步时间
                        AppPrefs.defaultInstance().profile.timingSyncTriggerTime =
                            triggerTime // 更新定时同步偏好值
                        val alarmManager =
                            context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
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
                        Rime.syncRimeUserData()
                        RimeWrapper.deploy()
                        wakeLock.release() // 释放唤醒锁
                    }
                }
            else -> return
        }
    }

    fun registerReceiver(context: Context) {
        context.registerReceiver(this, IntentFilter(COMMAND_DEPLOY))
        context.registerReceiver(this, IntentFilter(COMMAND_SYNC))
        context.registerReceiver(this, IntentFilter(COMMAND_TIMING_SYNC))
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
