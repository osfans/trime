package com.osfans.trime.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.osfans.trime.TrimeImeService
import timber.log.Timber

object InputMethodUtils {
    private val serviceName =
        ComponentName(appContext, TrimeImeService::class.java).flattenToShortString()

    fun checkIsTrimeEnabled(): Boolean {
        val activeImeIds = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_INPUT_METHODS
        ) ?: "(none)"

        Timber.i("List of active IMEs: $activeImeIds")
        return activeImeIds.split(":").contains(serviceName)
    }

    fun checkIsTrimeSelected(): Boolean {
        val selectedImeIds = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: "(none)"
        Timber.i("Selected IME: $selectedImeIds")
        return selectedImeIds == serviceName
    }

    fun showImeEnablerActivity(context: Context) =
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))

    fun showImePicker(context: Context): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        return if (imm != null) {
            imm.showInputMethodPicker()
            true
        } else {
            false
        }
    }
}
