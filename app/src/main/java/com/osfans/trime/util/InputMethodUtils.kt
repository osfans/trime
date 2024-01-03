package com.osfans.trime.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.osfans.trime.TrimeImeService
import splitties.systemservices.inputMethodManager
import timber.log.Timber

object InputMethodUtils {
    private val serviceName =
        ComponentName(appContext, TrimeImeService::class.java).flattenToShortString()

    private fun getSecureSettings(name: String) = Settings.Secure.getString(appContext.contentResolver, name)

    fun checkIsTrimeEnabled(): Boolean {
        val activeImeIds = getSecureSettings(Settings.Secure.ENABLED_INPUT_METHODS) ?: "(none)"
        Timber.i("List of active IMEs: $activeImeIds")
        return activeImeIds.split(":").contains(serviceName)
    }

    fun checkIsTrimeSelected(): Boolean {
        val selectedImeIds = getSecureSettings(Settings.Secure.DEFAULT_INPUT_METHOD) ?: "(none)"
        Timber.i("Selected IME: $selectedImeIds")
        return selectedImeIds == serviceName
    }

    fun showImeEnablerActivity(context: Context) = context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))

    fun showImePicker(): Boolean {
        inputMethodManager.showInputMethodPicker()
        return true
    }
}
