package com.osfans.trime.util

import android.app.SearchManager
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.icu.text.DateFormat
import android.icu.util.Calendar
import android.icu.util.ULocale
import android.net.Uri
import android.os.Build
import android.util.SparseArray
import android.view.KeyEvent
import com.blankj.utilcode.util.ActivityUtils
import com.blankj.utilcode.util.IntentUtils
import com.osfans.trime.Rime
import com.osfans.trime.ime.core.Preferences
import java.text.FieldPosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Implementation to open/call specified application/function
 */
object ShortcutUtils {
    fun call(context: Context, command: String, option: String): Any? {
        when (command) {
            "broadcast" -> context.sendBroadcast(Intent(option))
            "clipboard" -> return pasteFromClipboard(context)
            "date" -> return getDate(option)
            "run" -> startIntent(option)
            else -> startIntent(command, option)
        }
        return null
    }

    private fun startIntent(arg: String) {
        val intent = when {
            arg.indexOf(':') >= 0 -> {
                Intent.parseUri(arg, Intent.URI_INTENT_SCHEME)
            }
            arg.indexOf('/') >= 0 -> {
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName.unflattenFromString(arg)
                }
            }
            else -> IntentUtils.getLaunchAppIntent(arg)
        }
        intent.flags = (
            Intent.FLAG_ACTIVITY_NEW_TASK
                or Intent.FLAG_ACTIVITY_NO_HISTORY
            )
        ActivityUtils.startActivity(intent)
    }

    private fun startIntent(action: String, arg: String) {
        val act = "android.intent.action.${action.uppercase()}"
        var intent = Intent(act)
        when (act) {
            // Search or open link
            // Note that web_search cannot directly open link
            Intent.ACTION_WEB_SEARCH, Intent.ACTION_SEARCH -> {
                if (arg.startsWith("http")) {
                    startIntent(arg)
                    ActivityUtils.startLauncherActivity()
                    return
                } else {
                    intent.putExtra(SearchManager.QUERY, arg)
                }
            }
            // Share text
            Intent.ACTION_SEND -> intent = IntentUtils.getShareTextIntent(arg)
            // Stage the data
            else -> {
                if (arg.isNotEmpty()) Intent(act).data = Uri.parse(arg) else Intent(act)
            }
        }
        intent.flags = (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
        ActivityUtils.startActivity(intent)
    }

    private fun getDate(string: String): CharSequence {
        var option = ""
        var locale = ""
        if (string.contains("@")) {
            val opt = string.split(" ".toRegex(), 2)
            if (opt.size == 2 && opt[0].contains("@")) {
                locale = opt[0]
                option = opt[1]
            } else {
                locale = opt[0]
            }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val ul = ULocale(locale)
            val cc = Calendar.getInstance(ul)
            val df = if (option.isEmpty()) {
                DateFormat.getDateInstance(DateFormat.LONG, ul)
            } else {
                android.icu.text.SimpleDateFormat(option, ul.toLocale())
            }
            df.format(cc, StringBuffer(256), FieldPosition(0)).toString()
        } else {
            SimpleDateFormat(option, Locale.getDefault()).format(Date()) // Time
        }
    }

    private fun pasteFromClipboard(context: Context): CharSequence {
        val systemClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val systemPrimaryClip = systemClipboardManager.primaryClip
        return if (systemPrimaryClip?.getItemAt(0)?.text == null) { "" } else systemPrimaryClip.getItemAt(0)?.text!!
    }

    fun syncInBackground(context: Context) {
        val prefs = Preferences.defaultInstance()
        prefs.conf.lastSyncTime = Date().time
        prefs.conf.lastSyncStatus = Rime.syncUserData(context)
    }

    fun openCategory(keyCode: Int): Boolean {
        val category = applicationLaunchKeyCategories[keyCode]
        return if (category != null) {
            val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
            ActivityUtils.startActivity(intent)
            true
        } else false
    }

    private val applicationLaunchKeyCategories = SparseArray<String>().apply {
        append(KeyEvent.KEYCODE_EXPLORER, "android.intent.category.APP_BROWSER")
        append(KeyEvent.KEYCODE_ENVELOPE, "android.intent.category.APP_EMAIL")
        append(KeyEvent.KEYCODE_CONTACTS, "android.intent.category.APP_CONTACTS")
        append(KeyEvent.KEYCODE_CALENDAR, "android.intent.category.APP_CALENDAR")
        append(KeyEvent.KEYCODE_MUSIC, "android.intent.category.APP_MUSIC")
        append(KeyEvent.KEYCODE_CALCULATOR, "android.intent.category.APP_CALCULATOR")
    }
}
