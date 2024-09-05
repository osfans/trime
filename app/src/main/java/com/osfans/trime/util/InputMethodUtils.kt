// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.osfans.trime.BuildConfig
import com.osfans.trime.ime.core.TrimeInputMethodService
import splitties.systemservices.inputMethodManager
import timber.log.Timber

object InputMethodUtils {
    private val serviceName = TrimeInputMethodService::class.java.name
    private val componentName =
        ComponentName(appContext, TrimeInputMethodService::class.java).flattenToShortString()

    private fun getSecureSettings(name: String) = Settings.Secure.getString(appContext.contentResolver, name)

    fun checkIsTrimeEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            inputMethodManager.enabledInputMethodList
                .also {
                    Timber.i("List of active IMEs: $it")
                }.any {
                    it.packageName == BuildConfig.APPLICATION_ID && it.serviceName == serviceName
                }
        } else {
            val activeImeIds = getSecureSettings(Settings.Secure.ENABLED_INPUT_METHODS) ?: "(none)"
            Timber.i("List of active IMEs: $activeImeIds")
            activeImeIds.split(":").contains(componentName)
        }

    fun checkIsTrimeSelected(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            inputMethodManager.currentInputMethodInfo?.let {
                Timber.i("Selected IME: ${it.serviceName}")
                it.packageName == BuildConfig.APPLICATION_ID && it.serviceName == serviceName
            } ?: false
        } else {
            val selectedImeIds = getSecureSettings(Settings.Secure.DEFAULT_INPUT_METHOD) ?: "(none)"
            Timber.i("Selected IME: $selectedImeIds")
            selectedImeIds == componentName
        }

    fun showImeEnablerActivity(context: Context) = context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))

    fun showImePicker(): Boolean {
        inputMethodManager.showInputMethodPicker()
        return true
    }
}
