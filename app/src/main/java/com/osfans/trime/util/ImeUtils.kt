package com.osfans.trime.util

import android.content.Context
import android.provider.Settings
import timber.log.Timber

object ImeUtils {
    private const val IME_ID: String = "com.osfans.trime/.TrimeImeService"

    fun checkIfImeIsEnabled(context: Context): Boolean {
        val activeImeIds = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_INPUT_METHODS
        ) ?: "(none)"
        Timber.i("List of active IMEs: $activeImeIds")
        return activeImeIds.split(":").contains(IME_ID)
    }

    fun checkIfImeIsSelected(context: Context): Boolean {
        val selectedImeIds = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: "(none)"
        Timber.i("Selected IME: $selectedImeIds")
        return selectedImeIds == IME_ID
    }
}
