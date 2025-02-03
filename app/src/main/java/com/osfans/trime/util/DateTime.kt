/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.icu.text.DateFormat
import android.icu.util.Calendar
import android.icu.util.ULocale
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun formatDateTime(timeMillis: Long? = null): String = SimpleDateFormat.getDateTimeInstance().format(timeMillis?.let { Date(it) } ?: Date())

private val iso8601DateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

fun iso8601UTCDateTime(timeMillis: Long? = null): String = iso8601DateFormat.format(timeMillis?.let { Date(it) } ?: Date())

fun customFormatTimeInDefault(
    pattern: String,
    timeMillis: Long? = null,
): String = SimpleDateFormat(pattern, Locale.getDefault()).format(timeMillis?.let { Date(it) } ?: Date())

fun customFormatDateTime(
    pattern: String,
    timeMillis: Long? = null,
): String {
    val (locale, option) =
        if ("@" in pattern) {
            val parts = pattern.split(" ", limit = 2)
            when (parts.size) {
                2 -> if ("@" in parts[0]) parts[0] to parts[1] else parts[0] to ""
                1 -> parts[0] to ""
                else -> "" to ""
            }
        } else {
            "" to pattern
        }
    val date = timeMillis?.let { Date(it) } ?: Date()
    val loc = Locale(locale)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        if (option.isEmpty()) {
            DateFormat.getDateInstance(DateFormat.LONG, loc).format(date)
        } else {
            val cal = Calendar.getInstance(ULocale(locale))
            android.icu.text
                .SimpleDateFormat(option, loc)
                .format(cal)
        }
    } else {
        SimpleDateFormat(option, loc).format(date)
    }
}
