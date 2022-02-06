package com.osfans.trime.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object ClipboardUtils {
    fun getClipboardManager(context: Context): ClipboardManager {
        return context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // 获取主剪贴板
    fun getPrimaryClipboard(context: Context): ClipData {
        return getClipboardManager(context).primaryClip as ClipData
    }

    // 获取复制文字
    fun getCopyText(context: Context): String {
        return getPrimaryClipboard(context).getItemAt(0)?.coerceToText(context).toString()
    }
}