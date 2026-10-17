/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Instrumentation
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import com.osfans.trime.ime.core.EngineUnavailableView
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.TrimeInputMethodService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/** Real fallback widget pixels and touch in the IME window; engine-failure/retry is tested separately. */
object EngineUnavailableGlyphProbe {
    private val labels = setOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "@", "-")
    private val caseNames = listOf("preparing-font1", "failed-font1", "preparing-font2", "failed-font2")

    private fun <T> main(instrumentation: Instrumentation, block: () -> T): T {
        var outcome: Result<T>? = null
        instrumentation.runOnMainSync { outcome = runCatching(block) }
        return checkNotNull(outcome).getOrThrow()
    }

    private fun rect(value: Rect) = JSONArray(listOf(value.left, value.top, value.right, value.bottom))

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun glyph(button: Button): JSONObject {
        val label = button.text.toString()
        val problems = mutableListOf<String>()
        val actual = Bitmap.createBitmap(button.width, button.height, Bitmap.Config.ARGB_8888)
        val background = Bitmap.createBitmap(button.width, button.height, Bitmap.Config.ARGB_8888)
        var reference: Bitmap? = null
        val colors = button.textColors
        val scrollX = button.scrollX
        val scrollY = button.scrollY
        fun drawViewport(bitmap: Bitmap) {
            // View's parent draw path applies this offset before invoking draw(Canvas).
            // Single-line centered TextViews can have a large internal horizontal scroll.
            val canvas = Canvas(bitmap)
            canvas.translate(-scrollX.toFloat(), -scrollY.toFloat())
            button.draw(canvas)
        }
        try {
            button.jumpDrawablesToCurrentState()
            drawViewport(actual)
            val paint = Paint(button.paint)
            val natural = Rect()
            paint.getTextBounds(label, 0, label.length, natural)
            button.setTextColor(Color.TRANSPARENT)
            drawViewport(background)
            button.setTextColor(colors)
            // An unclipped single-glyph reference uses the same actual font/size, independent of Button padding.
            val margin = 8
            reference = Bitmap.createBitmap(natural.width().coerceAtLeast(1) + margin * 2, natural.height().coerceAtLeast(1) + margin * 2, Bitmap.Config.ARGB_8888)
            paint.textAlign = Paint.Align.LEFT
            paint.color = Color.BLACK
            Canvas(reference).drawText(label, (margin - natural.left).toFloat(), (margin - natural.top).toFloat(), paint)
            fun ink(bitmap: Bitmap, baseline: Bitmap? = null): Pair<Int, Rect> {
                var count = 0
                val bounds = Rect()
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val otherPixels = baseline?.let {
                    IntArray(pixels.size).also { pixels -> it.getPixels(pixels, 0, it.width, 0, 0, it.width, it.height) }
                }
                for (y in 0 until bitmap.height) {
                    for (x in 0 until bitmap.width) {
                        val index = y * bitmap.width + x
                        val color = pixels[index]
                        val visible = if (otherPixels == null) {
                            Color.alpha(color) > 8
                        } else {
                            val other = otherPixels[index]
                            abs(Color.red(color) - Color.red(other)) + abs(Color.green(color) - Color.green(other)) +
                                abs(Color.blue(color) - Color.blue(other)) + abs(Color.alpha(color) - Color.alpha(other)) > 12
                        }
                        if (visible) {
                            if (count == 0) bounds.set(x, y, x + 1, y + 1) else bounds.union(x, y, x + 1, y + 1)
                            count++
                        }
                    }
                }
                return count to bounds
            }
            val (actualInk, actualBounds) = ink(actual, background)
            val (referenceInk, referenceBounds) = ink(reference)
            val location = IntArray(2)
            button.getLocationOnScreen(location)
            val screen = Rect(location[0], location[1], location[0] + button.width, location[1] + button.height)
            val visible = Rect()
            val hasVisibleRect = button.getLocalVisibleRect(visible)
            val contentVisible = Rect(visible)
            // getLocalVisibleRect returns scrolled content coordinates, not viewport coordinates.
            visible.offset(-scrollX, -scrollY)
            val fullyVisible = hasVisibleRect && visible == Rect(0, 0, button.width, button.height)
            visible.offset(location[0], location[1])
            val minimum = (48 * button.resources.displayMetrics.density).roundToInt()
            val hitArea = button.width >= minimum && abs(button.height - minimum) <= 1
            // TextView.getBaseline does not include the parent's scroll translation.
            val drawnBaseline = button.baseline - scrollY
            val glyphTop = drawnBaseline + natural.top
            val glyphBottom = drawnBaseline + natural.bottom
            val verticalFit = glyphTop >= button.compoundPaddingTop && glyphBottom <= button.height - button.compoundPaddingBottom
            if (!fullyVisible) problems.add("key not completely visible")
            if (!hitArea) problems.add("key does not retain its 48dp touch area")
            if (!verticalFit) problems.add("natural glyph is outside the text content height")
            if (actualInk == 0 || referenceInk == 0) problems.add("glyph has no drawn ink")
            // One raster pixel allows subpixel positioning differences, not missing lower glyph strokes.
            if (actualBounds.width() < referenceBounds.width() - 1 || actualBounds.height() < referenceBounds.height() - 1) {
                problems.add("drawn glyph is smaller than its unclipped reference")
            }
            return JSONObject().apply {
                put("label", label)
                put("text_size_px", paint.textSize)
                put("baseline_px", button.baseline)
                put("drawn_baseline_px", drawnBaseline)
                put("scroll_x_px", scrollX)
                put("scroll_y_px", scrollY)
                put("font_ascent_px", paint.fontMetrics.ascent)
                put("font_descent_px", paint.fontMetrics.descent)
                put("include_font_padding", button.includeFontPadding)
                put("padding", JSONArray(listOf(button.paddingLeft, button.paddingTop, button.paddingRight, button.paddingBottom)))
                put("screen_bounds", rect(screen))
                put("local_visible_content_bounds", rect(contentVisible))
                put("visible_bounds", rect(visible))
                put("natural_glyph_bounds", rect(natural))
                put("natural_ink_bounds", rect(referenceBounds))
                put("drawn_ink_bounds", rect(actualBounds))
                put("natural_ink_pixels", referenceInk)
                put("drawn_ink_pixels", actualInk)
                put("fully_visible", fullyVisible)
                put("touch_area_48dp", hitArea)
                put("vertical_fit", verticalFit)
                put("glyph_passed", problems.isEmpty())
                put("errors", JSONArray(problems))
            }
        } finally {
            button.setTextColor(colors)
            actual.recycle()
            background.recycle()
            reference?.recycle()
        }
    }

    suspend fun verify(instrumentation: Instrumentation, service: TrimeInputMethodService, editor: EditText) {
        val directory = File(instrumentation.targetContext.filesDir, "runtime-audit/fallback-glyph")
        check(directory.isDirectory || directory.mkdirs())
        val ownedNames = caseNames.flatMap { listOf("$it.png", "$it.json") } + "report.json"
        ownedNames.forEach { name ->
            val file = File(directory, name)
            check(!file.exists() || (file.isFile && file.delete())) { "Cannot clear owned fallback evidence: $name" }
        }
        val runId = UUID.randomUUID().toString()
        instrumentation.sendStatus(0, Bundle().apply { putString("fallback_glyph_run_id", runId) })
        val inputField = TrimeInputMethodService::class.java.getDeclaredField("inputView").apply { isAccessible = true }
        val panelField = TrimeInputMethodService::class.java.getDeclaredField("loadingPanel").apply { isAccessible = true }
        val original = main(instrumentation) { checkNotNull(inputField.get(service) as InputView?) }
        val originalPanel = main(instrumentation) { panelField.get(service) }
        val parent = main(instrumentation) { original.parent as FrameLayout }
        val originalVisibility = main(instrumentation) { original.visibility }
        val originalText = main(instrumentation) { editor.text.toString() }
        val selection = main(instrumentation) { editor.selectionStart to editor.selectionEnd }
        val originalFontScale = service.resources.configuration.fontScale
        val originalContext = original.context
        val token = main(instrumentation) { service.editorToken }
        val cases = JSONArray()
        val attachments = JSONObject()
        val failures = mutableListOf<String>()
        var restored = false
        var overlay: FrameLayout? = null
        fun restoreAndWriteReport() {
            try {
                restored = main(instrumentation) {
                    overlay?.let(parent::removeView)
                    inputField.set(service, original)
                    panelField.set(service, originalPanel)
                    original.visibility = originalVisibility
                    original.requestApplyInsets()
                    editor.setText(originalText)
                    editor.setSelection(selection.first.coerceIn(0, originalText.length), selection.second.coerceIn(0, originalText.length))
                    original.parent === parent && original.isAttachedToWindow && original.isShown &&
                        inputField.get(service) === original && panelField.get(service) === originalPanel &&
                        original.context === originalContext && service.resources.configuration.fontScale == originalFontScale &&
                        editor.text.toString() == originalText && editor.selectionStart == selection.first &&
                        editor.selectionEnd == selection.second && overlay?.parent == null
                }
            } finally {
                if (!restored) failures.add("original_view_or_editor_not_restored")
                ownedNames.filter { it != "report.json" }.forEach { name ->
                    File(directory, name).takeIf { it.isFile }?.let { attachments.put(name, sha256(it)) }
                }
                val report = JSONObject().apply {
                    put("format_version", 1)
                    put("run_id", runId)
                    put("api", Build.VERSION.SDK_INT)
                    put("scope", "Actual production fallback widget in the IME window with per-widget font contexts; native failure/retry is tested separately")
                    put("original_view_restored", restored)
                    put("original_font_scale", originalFontScale)
                    put("cases", cases)
                    put("attachments", attachments)
                    put("passed", failures.isEmpty() && restored && cases.length() == 4 && attachments.length() == 8)
                    put("failures", JSONArray(failures))
                }
                File(directory, "report.json").writeText(report.toString(2) + "\n")
            }
        }

        try {
            val panel = main(instrumentation) {
                check(service.isCurrentEditor(token) && service.currentInputEditorInfo?.fieldId == editor.id)
                val panel = FrameLayout(service)
                val layer = FrameLayout(service).apply {
                    addView(panel, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
                }
                overlay = layer
                ViewCompat.setOnApplyWindowInsetsListener(panel) { view, insets ->
                    val safe = insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.mandatorySystemGestures())
                    view.setPadding(safe.left, 0, safe.right, safe.bottom)
                    insets
                }
                // InputView intentionally cannot be reattached after detach: keep its receivers and jobs alive.
                original.visibility = View.INVISIBLE
                inputField.set(service, null)
                panelField.set(service, panel)
                parent.addView(layer, FrameLayout.LayoutParams(-1, -1))
                panel.requestApplyInsets()
                panel
            }
            for (fontScale in listOf(1f, 2f)) {
                for (failed in listOf(false, true)) {
                    val name = "${if (failed) "failed" else "preparing"}-font${fontScale.toInt()}"
                    val record = JSONObject().apply {
                        put("run_id", runId)
                        put("name", name)
                        put("failed_state", failed)
                        put("requested_font_scale", fontScale)
                    }
                    val errors = mutableListOf<String>()
                    var glyphsPassed = false
                    var committed = false
                    var keysChecked = 0
                    try {
                        suspend fun verifyGlyphRendering(): List<Button> {
                            val widget = main(instrumentation) {
                                val configuration = Configuration(service.resources.configuration).apply { this.fontScale = fontScale }
                                val context = service.createConfigurationContext(configuration)
                                context.theme.setTo(service.theme)
                                EngineUnavailableView(
                                    context,
                                    failed,
                                    retry = {},
                                    commit = { if (service.isCurrentEditor(token)) service.commitText(it) },
                                    delete = { service.sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL) },
                                    enter = { service.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER) },
                                ).also {
                                    panel.removeAllViews()
                                    panel.addView(it, FrameLayout.LayoutParams(-1, -2))
                                }
                            }
                            withTimeout(10_000) {
                                while (!main(instrumentation) { widget.isShown && widget.width > 0 && widget.height > 0 }) delay(50)
                            }
                            val drawn = CompletableDeferred<Unit>()
                            main(instrumentation) { widget.postOnAnimation { widget.postOnAnimation { drawn.complete(Unit) } } }
                            withTimeout(10_000) { drawn.await() }
                            val buttons = main(instrumentation) {
                                widget.children.drop(1).flatMap { (it as android.view.ViewGroup).children }.filterIsInstance<Button>().toList()
                            }
                            val keyRecords = main(instrumentation) {
                                check(buttons.map { it.text.toString() }.toSet() == labels && buttons.size == 13)
                                check(widget.resources.configuration.fontScale == fontScale)
                                val header = widget.getChildAt(0) as android.view.ViewGroup
                                val heading = header.children.filterIsInstance<TextView>().single().text.toString()
                                val retryEnabled = header.children.filterIsInstance<ImageButton>().first().isEnabled
                                val stateVerified = heading == widget.context.getString(if (failed) R.string.ime_unavailable else R.string.ime_preparing) && retryEnabled == failed
                                record.put("header_text", heading)
                                record.put("retry_enabled", retryEnabled)
                                record.put("state_verified", stateVerified)
                                if (!stateVerified) errors.add("fallback widget state does not match the requested case")
                                record.put("actual_font_scale", widget.resources.configuration.fontScale)
                                record.put("density", widget.resources.displayMetrics.density)
                                buttons.map { glyph(it) }
                            }
                            keysChecked = keyRecords.size
                            glyphsPassed = keyRecords.all { it.getBoolean("glyph_passed") }
                            record.put("keys", JSONArray(keyRecords))
                            if (!glyphsPassed) errors.add("one or more fallback glyphs are clipped or invisible")
                            val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                            try {
                                File(directory, "$name.png").outputStream().use { check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                            } finally {
                                screenshot.recycle()
                            }
                            return buttons
                        }
                        val buttons = verifyGlyphRendering()
                        suspend fun verifyRealDotTouch() {
                            val before = main(instrumentation) {
                                check(service.isCurrentEditor(token) && service.currentInputEditorInfo?.fieldId == editor.id)
                                checkNotNull(service.currentInputConnection)
                                editor.setSelection(editor.text.length)
                                editor.text.toString()
                            }
                            val point = main(instrumentation) {
                                val dot = buttons.single { it.text.toString() == "." }
                                val position = IntArray(2)
                                dot.getLocationOnScreen(position)
                                (position[0] + dot.width / 2f) to (position[1] + dot.height / 2f)
                            }
                            val down = SystemClock.uptimeMillis()
                            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point.first, point.second, 0)
                                event.source = InputDevice.SOURCE_TOUCHSCREEN
                                try {
                                    instrumentation.sendPointerSync(event)
                                } finally {
                                    event.recycle()
                                }
                            }
                            withTimeout(10_000) {
                                while (!main(instrumentation) { editor.text.toString() == "$before." }) delay(50)
                            }
                            committed = main(instrumentation) { service.isCurrentEditor(token) && service.currentInputEditorInfo?.fieldId == editor.id && editor.text.toString() == "$before." }
                            record.put(
                                "dot_touch",
                                JSONObject().apply {
                                    put("x", point.first)
                                    put("y", point.second)
                                    put("field_id", editor.id)
                                    put("before", before)
                                    put("after", main(instrumentation) { editor.text.toString() })
                                    put("committed", committed)
                                },
                            )
                        }
                        verifyRealDotTouch()
                    } catch (failure: Exception) {
                        errors.add(failure.stackTraceToString())
                    } finally {
                        record.put("glyphs_passed", glyphsPassed)
                        record.put("dot_committed", committed)
                        record.put("keys_checked", keysChecked)
                        record.put("passed", errors.isEmpty() && glyphsPassed && committed && keysChecked == 13)
                        record.put("errors", JSONArray(errors))
                        File(directory, "$name.json").writeText(record.toString(2) + "\n")
                        cases.put(record)
                        if (!record.getBoolean("passed")) failures.add(name)
                    }
                }
            }
        } finally {
            restoreAndWriteReport()
        }
        check(failures.isEmpty() && cases.length() == 4 && attachments.length() == 8) { "Fallback glyph/real-touch checks failed: $failures; run_id=$runId" }
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS: fallback Preparing/Failed at font1/font2: 52 rendered glyphs and four real dot commits; original view restored\n") })
    }
}
