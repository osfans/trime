package com.osfans.trime.common

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.osfans.trime.TrimeApplication
import timber.log.Timber

object InputMethodUtils {
    private const val IME_ID: String = "com.osfans.trime/.TrimeImeService"
    private val context: Context
        get() = TrimeApplication.getInstance().applicationContext

    fun checkIsTrimeEnabled(): Boolean {
        val activeImeIds = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_INPUT_METHODS
        ) ?: "(none)"
        Timber.i("List of active IMEs: $activeImeIds")
        return activeImeIds.split(":").contains(IME_ID)
    }

    fun checkIsTrimeSelected(): Boolean {
        val selectedImeIds = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: "(none)"
        Timber.i("Selected IME: $selectedImeIds")
        return selectedImeIds == IME_ID
    }

    fun showImeEnablerActivity(context: Context) {
        val intent = Intent()
        intent.action = Settings.ACTION_INPUT_METHOD_SETTINGS
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

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
