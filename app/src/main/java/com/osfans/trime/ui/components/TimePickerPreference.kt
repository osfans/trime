/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.components

import android.app.TimePickerDialog
import android.content.Context
import android.util.AttributeSet
import androidx.core.content.edit
import androidx.preference.DialogPreference
import java.util.Calendar
import java.util.concurrent.TimeUnit

class TimePickerPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : DialogPreference(context, attrs) {
        var default: Long = System.currentTimeMillis()

        var value = System.currentTimeMillis()
            private set

        override fun onSetInitialValue(defaultValue: Any?) {
            super.onSetInitialValue(defaultValue)
            preferenceDataStore?.apply {
                value = getLong(key, default)
            } ?: sharedPreferences?.apply {
                value = getLong(key, default)
            }
        }

        override fun setDefaultValue(defaultValue: Any?) {
            val time = defaultValue as? Long ?: return
            value = time as? Long ?: System.currentTimeMillis()
        }

        private fun persistValues(setTime: Long) {
            if (!shouldPersist()) return
            value = setTime
            preferenceDataStore?.apply {
                putLong(key, setTime)
            } ?: sharedPreferences?.edit {
                putLong(key, setTime)
            }
        }

        override fun onClick() {
            showTimePickerDialog()
        }

        private fun showTimePickerDialog() {
            val cal = Calendar.getInstance()
            val timeSetListener = // 监听时间选择器设置
                TimePickerDialog.OnTimeSetListener { _, hour, minute ->
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, minute)
                    val timeInMillis = cal.timeInMillis // 设置的时间
                    val triggerAtMillis =
                        if (timeInMillis > System.currentTimeMillis() + PERIOD) {
                            timeInMillis
                        } else {
                            // 设置的时间小于当前时间 20 分钟时将同步推迟到明天
                            timeInMillis + TimeUnit.DAYS.toMillis(1)
                        }
                    setValue(triggerAtMillis)
                }
            // 时间选择器设置
            TimePickerDialog(
                context,
                timeSetListener,
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true,
            ).apply {
                setOnCancelListener {
                    // 当取消时间选择器时重置偏好
                    setValue(0L)
                }
                show()
            }
        }

        private fun setValue(setTime: Long) {
            if (callChangeListener(setTime)) {
                persistValues(setTime)
                notifyChanged()
            }
        }

        companion object {
            private const val PERIOD = 1_200_000L // 20 分钟
        }
    }
