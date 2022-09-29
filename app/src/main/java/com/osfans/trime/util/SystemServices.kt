package com.osfans.trime.util

import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context

inline val clipboardManager: ClipboardManager
    get() = appContext.getSystemService(Context.CLIPBOARD_SERVICE)
        as ClipboardManager

inline val notificationManager: NotificationManager
    get() = appContext.getSystemService(Context.NOTIFICATION_SERVICE)
        as NotificationManager
