package com.osfans.trime.util

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import com.osfans.trime.tasker.CopyEventActivity

fun triggerCopyEvent(context: Context) = CopyEventActivity::class.java.requestQuery(context)
