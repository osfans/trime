package com.osfans.trime.util

import android.content.Context
import com.osfans.trime.TrimeApplication
import java.text.SimpleDateFormat
import java.util.Date

val appContext: Context get() = TrimeApplication.getInstance().applicationContext

fun formatDateTime(timeMillis: Long? = null): String =
    SimpleDateFormat.getDateTimeInstance().format(timeMillis?.let { Date(it) } ?: Date())
