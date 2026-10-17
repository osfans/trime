// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.osfans.trime.ui.main.ClipEditActivity
import java.io.File

object ClipEditorProbe {
    fun run(instrumentation: Instrumentation) {
        val context = instrumentation.targetContext
        val result = Bundle()
        var activity: Activity? = null
        var passed = false
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "Emulator only" }
            check(Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).startsWith("${context.packageName}/")) {
                "Select this build of Trime as the default input method first"
            }
            val editor = instrumentation.startActivitySync(Intent(context, ClipEditActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            activity = editor
            val automation = instrumentation.uiAutomation
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            }
            var shown = false
            var keyboardBounds: Rect? = null
            var stableSince = 0L
            val deadline = SystemClock.uptimeMillis() + 60_000
            while (!shown && SystemClock.uptimeMillis() < deadline) {
                var focused = false
                instrumentation.runOnMainSync {
                    val field = editor.findViewById<EditText>(R.id.clip_edit_text)
                    focused = field.hasFocus() && field.hasWindowFocus() &&
                        ViewCompat.getRootWindowInsets(field)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                }
                val bounds = if (focused) {
                    automation.windows.asSequence()
                        .filter { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                        .mapNotNull { window ->
                            window.root?.findAccessibilityNodeInfosByViewId(context.resources.getResourceName(R.id.keyboard_view))
                                ?.firstOrNull { it.isVisibleToUser }
                                ?.let { node -> Rect().also { node.getBoundsInScreen(it) } }
                        }.firstOrNull { it.width() > 100 && it.height() > 100 }
                } else {
                    null
                }
                val now = SystemClock.uptimeMillis()
                if (bounds == null || bounds != keyboardBounds) stableSince = now
                keyboardBounds = bounds
                // Insets can report visible before the asynchronous input view is drawn.
                shown = bounds != null && now - stableSince >= 1000
                if (!shown) SystemClock.sleep(100)
            }
            check(shown) { "A stable Trime keyboard view was not visible in the clipboard editor" }
            val screenshot = checkNotNull(automation.takeScreenshot())
            try {
                val bounds = Rect(checkNotNull(keyboardBounds))
                check(bounds.intersect(0, 0, screenshot.width, screenshot.height))
                val colors = mutableSetOf<Int>()
                for (y in bounds.top until bounds.bottom step 6) {
                    for (x in bounds.left until bounds.right step 6) colors.add(screenshot.getPixel(x, y) and 0x00F0F0F0)
                }
                check(colors.size >= 3) { "Keyboard screenshot is blank" }
                File(context.cacheDir, "clip-editor-probe.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                result.putString("keyboard_bounds", bounds.toShortString())
            } finally {
                screenshot.recycle()
            }
            result.putString("stream", "PASS: focused clipboard editor, stable real keyboard view and nonblank screenshot; no clipboard entry changed\n")
            passed = true
        } catch (failure: Throwable) {
            result.putString("stream", "FAIL: ${failure.stackTraceToString()}\n")
        } finally {
            activity?.let { instrumentation.runOnMainSync { it.finish() } }
        }
        result.putBoolean("passed", passed)
        instrumentation.finish(if (passed) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }
}
