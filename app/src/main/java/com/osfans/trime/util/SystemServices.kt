package com.osfans.trime.util

import android.app.NotificationManager
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
import android.os.Vibrator
import android.os.storage.StorageManager
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager

@Suppress("UNCHECKED_CAST")
@PublishedApi
internal fun <T> getSystemService(name: String) = appContext.getSystemService(name) as T

inline val clipboardManager: ClipboardManager
    get() = getSystemService(Context.CLIPBOARD_SERVICE)

inline val notificationManager: NotificationManager
    get() = getSystemService(Context.NOTIFICATION_SERVICE)

@Suppress("DEPRECATION")
inline val vibrator: Vibrator
    get() = getSystemService(Context.VIBRATOR_SERVICE)

inline val audioManager: AudioManager
    get() = getSystemService(Context.AUDIO_SERVICE)

inline val inputMethodManager: InputMethodManager
    get() = getSystemService(Context.INPUT_METHOD_SERVICE)

inline val Context.layoutInflater: LayoutInflater
    get() = this.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

inline val View.layoutInflater: LayoutInflater
    get() = context.layoutInflater

inline val storageManager: StorageManager
    get() = getSystemService(Context.STORAGE_SERVICE)
