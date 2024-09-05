// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.icu.text.DateFormat
import android.icu.util.Calendar
import android.icu.util.ULocale
import android.net.Uri
import android.os.Build
import android.text.TextUtils
import android.util.SparseArray
import android.view.KeyEvent
import com.osfans.trime.core.Rime
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.symbol.SymbolBoardType
import com.osfans.trime.ui.main.LiquidKeyboardEditActivity
import com.osfans.trime.ui.main.LogActivity
import com.osfans.trime.ui.main.PrefMainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import splitties.systemservices.clipboardManager
import timber.log.Timber
import java.text.FieldPosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Implementation to open/call specified application/function
 */
object ShortcutUtils {
    fun call(
        context: Context,
        command: String,
        option: String,
    ): CharSequence? {
        when (command) {
            "broadcast" -> context.sendBroadcast(Intent(option))
            "clipboard" -> return pasteFromClipboard(context)
            "date" -> return getDate(option)
            "commit" -> return option
            "run" -> context.startIntent(option)
            "share_text" -> TrimeInputMethodService.getService().shareText()
            "liquid_keyboard" -> TrimeInputMethodService.getService().selectLiquidKeyboard(option)
            else -> context.startIntent(command, option)
        }
        return null
    }

    private fun Context.startIntent(arg: String) {
        when {
            arg.contains(':') -> { // URI
                Intent.parseUri(arg, Intent.URI_INTENT_SCHEME)
            }
            arg.contains('/') -> { // Component name
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName.unflattenFromString(arg)
                }
            }
            else -> packageManager.getLaunchIntentForPackage(arg) // Package name
        }?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
        }?.let {
            runCatching {
                Timber.d("startIntent: arg=$arg")
                startActivity(it)
            }.getOrElse { Timber.e(it, "Error on starting activity with intent") }
        }
    }

    private fun Context.startIntent(
        action: String,
        arg: String,
    ) {
        val longAction = "android.intent.action.${action.uppercase()}"
        val intent = Intent(longAction)
        when (longAction) {
            // Search or open link
            // Note that web_search cannot directly open link
            Intent.ACTION_WEB_SEARCH, Intent.ACTION_SEARCH -> {
                if (arg.startsWith("http")) {
                    startIntent(arg)
                    return
                } else {
                    intent.putExtra(SearchManager.QUERY, arg)
                }
            }
            Intent.ACTION_SEND -> { // Share text
                intent.apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, arg)
                }
            }
            else -> { // Stage the data
                if (arg.isNotEmpty()) intent.data = Uri.parse(arg)
            }
        }
        intent.flags = (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
        runCatching {
            Timber.d("startIntent: action=$longAction, arg=$arg")
            startActivity(intent)
        }.getOrElse { Timber.e(it, "Error on starting activity with intent") }
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !TextUtils.isEmpty(locale)) {
            val ul = ULocale(locale)
            val cc = Calendar.getInstance(ul)
            val df =
                if (option.isEmpty()) {
                    DateFormat.getDateInstance(DateFormat.LONG, ul)
                } else {
                    android.icu.text.SimpleDateFormat(option, ul.toLocale())
                }
            df.format(cc, StringBuffer(256), FieldPosition(0)).toString()
        } else {
            SimpleDateFormat(string, Locale.getDefault()).format(Date()) // Time
        }
    }

    @JvmStatic
    fun pasteFromClipboard(context: Context): CharSequence? = clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(context)

    fun syncInBackground() {
        val prefs = AppPrefs.defaultInstance()
        prefs.profile.lastBackgroundSync = Date().time.toString()
        CoroutineScope(Dispatchers.IO).launch {
            prefs.profile.lastSyncStatus = Rime.syncRimeUserData().also { RimeDaemon.restartRime() }
        }
    }

    fun Context.openCategory(keyCode: Int): Boolean {
        val category = applicationLaunchKeyCategories[keyCode]
        return if (!category.isNullOrEmpty()) {
            Timber.d("openCategory: keyEvent=${KeyEvent.keyCodeToString(keyCode)}, category=$category")
            val intent =
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY
                }
            runCatching {
                startActivity(intent)
            }.getOrElse { Timber.e(it, "Error on starting activity with category") }
            true
        } else {
            false
        }
    }

    private val applicationLaunchKeyCategories =
        SparseArray<String>().apply {
            append(KeyEvent.KEYCODE_EXPLORER, "android.intent.category.APP_BROWSER")
            append(KeyEvent.KEYCODE_ENVELOPE, "android.intent.category.APP_EMAIL")
            append(KeyEvent.KEYCODE_CONTACTS, "android.intent.category.APP_CONTACTS")
            append(KeyEvent.KEYCODE_CALENDAR, "android.intent.category.APP_CALENDAR")
            append(KeyEvent.KEYCODE_MUSIC, "android.intent.category.APP_MUSIC")
            append(KeyEvent.KEYCODE_CALCULATOR, "android.intent.category.APP_CALCULATOR")
        }

    fun launchMainActivity(context: Context) {
        context.startActivity(
            Intent(context, PrefMainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        or Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
            },
        )
    }

    fun launchLogActivity(context: Context) {
        context.startActivity(
            Intent(context, LogActivity::class.java),
        )
    }

    fun launchLiquidKeyboardEdit(
        context: Context,
        type: SymbolBoardType,
        id: Int,
        text: String,
    ) {
        context.startActivity(
            Intent(context, LiquidKeyboardEditActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(LiquidKeyboardEditActivity.DB_BEAN_ID, id)
                putExtra(LiquidKeyboardEditActivity.DB_BEAN_TEXT, text)
                putExtra(LiquidKeyboardEditActivity.LIQUID_KEYBOARD_TYPE, type.name)
            },
        )
    }
}
