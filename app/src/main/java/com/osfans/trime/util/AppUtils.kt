// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SparseArray
import android.view.KeyEvent
import androidx.annotation.IdRes
import androidx.navigation.NavDeepLinkBuilder
import com.osfans.trime.R
import com.osfans.trime.ime.symbol.SymbolBoardType
import com.osfans.trime.ui.main.LiquidKeyboardEditActivity
import com.osfans.trime.ui.main.LogActivity
import com.osfans.trime.ui.main.PrefMainActivity
import timber.log.Timber

object AppUtils {
    private val applicationLaunchKeyCategories =
        SparseArray<String>().apply {
            append(KeyEvent.KEYCODE_EXPLORER, Intent.CATEGORY_APP_BROWSER)
            append(KeyEvent.KEYCODE_ENVELOPE, Intent.CATEGORY_APP_EMAIL)
            append(KeyEvent.KEYCODE_CONTACTS, Intent.CATEGORY_APP_CONTACTS)
            append(KeyEvent.KEYCODE_CALENDAR, Intent.CATEGORY_APP_CALENDAR)
            append(KeyEvent.KEYCODE_MUSIC, Intent.CATEGORY_APP_MUSIC)
            append(KeyEvent.KEYCODE_CALCULATOR, Intent.CATEGORY_APP_CALCULATOR)
        }

    fun launchKeyCategory(
        context: Context,
        keyCode: Int,
    ): Boolean =
        applicationLaunchKeyCategories[keyCode]?.let {
            Timber.d("launchKeyCategory: $it")
            try {
                context.startActivity(
                    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, it).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
                    },
                )
                true
            } catch (_: Exception) {
                false
            }
        } ?: false

    fun launchMainActivity(context: Context) {
        context.startActivity<PrefMainActivity> {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
    }

    private fun launchMainToDest(
        context: Context,
        @IdRes dest: Int,
        arguments: Bundle? = null,
    ) {
        NavDeepLinkBuilder(context)
            .setComponentName(PrefMainActivity::class.java)
            .setGraph(R.navigation.pref_nav)
            .addDestination(dest, arguments)
            .createTaskStackBuilder()
            /**
             * [androidx.core.app.TaskStackBuilder.getIntents] would add unwanted flags
             * [Intent.FLAG_ACTIVITY_CLEAR_TASK] and [Intent.FLAG_ACTIVITY_TASK_ON_HOME]
             * so we must launch the Intent by ourselves
             */
            .editIntentAt(0)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                context.startActivity(this)
            }
    }

    fun launchMainToSchemaList(context: Context) = launchMainToDest(context, R.id.schemaListFragment)

    fun launchLogActivity(context: Context) {
        context.startActivity<LogActivity>()
    }

    fun launchLiquidKeyboardEdit(
        context: Context,
        type: SymbolBoardType,
        id: Int,
        text: String,
    ) {
        context.startActivity<LiquidKeyboardEditActivity> {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(LiquidKeyboardEditActivity.DB_BEAN_ID, id)
            putExtra(LiquidKeyboardEditActivity.DB_BEAN_TEXT, text)
            putExtra(LiquidKeyboardEditActivity.LIQUID_KEYBOARD_TYPE, type.name)
        }
    }
}
