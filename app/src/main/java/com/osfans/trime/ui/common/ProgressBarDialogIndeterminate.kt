/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import com.osfans.trime.R
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import splitties.dimensions.dp
import splitties.views.dsl.core.add
import splitties.views.dsl.core.horizontalMargin
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.styles.AndroidStyles
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.verticalMargin

@Suppress("FunctionName")
fun Context.ProgressBarDialogIndeterminate(
    @StringRes title: Int,
): AlertDialog.Builder {
    val androidStyles = AndroidStyles(this)
    return AlertDialog
        .Builder(this)
        .setTitle(title)
        .setView(
            verticalLayout {
                add(
                    androidStyles.progressBar.horizontal {
                        isIndeterminate = true
                    },
                    lParams {
                        width = matchParent
                        verticalMargin = dp(20)
                        horizontalMargin = dp(26)
                    },
                )
            },
        ).setCancelable(false)
}

fun LifecycleCoroutineScope.withLoadingDialog(
    context: Context,
    @StringRes title: Int = R.string.loading,
    threshold: Long = 200L,
    action: suspend () -> Unit,
) {
    var loadingDialog: AlertDialog? = null
    val loadingJob =
        launch {
            delay(threshold)
            loadingDialog = context.ProgressBarDialogIndeterminate(title).show()
        }
    launch {
        action()
        loadingJob.cancelAndJoin()
        loadingDialog?.dismiss()
    }
}
