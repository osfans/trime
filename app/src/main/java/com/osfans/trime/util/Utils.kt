package com.osfans.trime.util

import android.content.Context
import com.osfans.trime.TrimeApplication
import java.text.SimpleDateFormat
import java.util.Date

val appContext: Context get() = TrimeApplication.getInstance().applicationContext

fun formatDateTime(timeMillis: Long? = null): String =
    SimpleDateFormat.getDateTimeInstance().format(timeMillis?.let { Date(it) } ?: Date())

@Suppress("NOTHING_TO_INLINE")
inline fun CharSequence.startsWithAsciiChar(): Boolean {
    val firstCodePoint = this.toString().codePointAt(0)
    return firstCodePoint in 0x20 until 0x80
}
