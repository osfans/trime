/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Instrumentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.mikepenz.iconics.IconicsDrawable
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyView
import com.osfans.trime.ime.popup.PopupAction
import com.osfans.trime.ime.popup.PopupDelegate
import com.osfans.trime.ime.popup.PopupEntryUi
import com.osfans.trime.ime.popup.PopupKeyboardUi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.kodein.di.direct
import org.kodein.di.instance
import java.util.LinkedList

/** Actual IME popup pool and scheme-change listener chain, including Android 5 drawable pixels. */
object PopupColorRegression {
    private inline fun <reified T> field(target: Any, name: String): T = target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target) as T

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) repeat(view.childCount) { addAll(descendants(view.getChildAt(it))) }
    }

    private fun bounds(view: View): Rect {
        val point = IntArray(2)
        view.getLocationOnScreen(point)
        return Rect(point[0], point[1], point[0] + view.width, point[1] + view.height)
    }

    private fun pixels(context: Context, drawable: Drawable, expected: Int, solid: Boolean, rejectedColor: Int = expected) {
        val originalBounds = Rect(drawable.bounds)
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val controls = mutableListOf<Bitmap>()
        try {
            drawable.setBounds(0, 0, 64, 64)
            // Capture the production drawable before constructing either independent color control.
            // Its colors, filter and state are never changed by this comparison.
            drawable.draw(Canvas(bitmap))
            if (solid) {
                check(bitmap.getPixel(32, 32) == expected) { "Popup background retained the previous scheme" }
            } else {
                check(drawable is IconicsDrawable) { "Unexpected popup icon drawable: ${drawable.javaClass.name}" }
                check(Color.alpha(expected) == 255 && Color.alpha(rejectedColor) == 255 && expected != rejectedColor)
                check(drawable.sizeXPx > 0 && drawable.sizeYPx > 0)
                val icon = checkNotNull(drawable.icon)
                fun reference(color: Int): IntArray {
                    val referenceBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                    controls.add(referenceBitmap)
                    // Production constructs these icons with only their icon, size and explicit filter.
                    // Do not copy the live filter, paint, tint or state into the oracle.
                    val reference = IconicsDrawable(context, icon).apply {
                        sizeXPx = drawable.sizeXPx
                        sizeYPx = drawable.sizeYPx
                        colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                    }
                    reference.setBounds(0, 0, 64, 64)
                    reference.draw(Canvas(referenceBitmap))
                    return IntArray(64 * 64).also { referenceBitmap.getPixels(it, 0, 64, 0, 0, 64, 64) }
                }
                val actualPixels = IntArray(64 * 64).also { bitmap.getPixels(it, 0, 64, 0, 0, 64, 64) }
                val expectedPixels = reference(expected)
                val rejectedPixels = reference(rejectedColor)
                check(!expectedPixels.contentEquals(rejectedPixels)) { "Popup color control cannot reject the other palette" }
                var nontransparentPixels = 0
                var opaquePixels = 0
                for (index in actualPixels.indices) {
                    val pixel = actualPixels[index]
                    check(pixel == expectedPixels[index]) {
                        "Popup icon raster mismatch: expected=${Integer.toHexString(expectedPixels[index])}, actual=${Integer.toHexString(pixel)}, x=${index % 64}, y=${index / 64}, drawable=${drawable.javaClass.name}"
                    }
                    if (Color.alpha(pixel) > 0) nontransparentPixels++
                    if (Color.alpha(pixel) == 255) {
                        opaquePixels++
                        check(pixel == expected && rejectedPixels[index] == rejectedColor) { "Opaque popup icon colors differ from the two explicit palettes" }
                    }
                }
                check(nontransparentPixels > 0 && opaquePixels > 0) { "Popup icon is empty or lacks opaque ink" }
            }
        } finally {
            drawable.bounds = originalBounds
            bitmap.recycle()
            controls.forEach(Bitmap::recycle)
        }
    }

    suspend fun verify(instrumentation: Instrumentation, input: InputView, editor: EditText) {
        fun <T> main(block: () -> T): T {
            var result: Result<T>? = null
            instrumentation.runOnMainSync { result = runCatching(block) }
            return checkNotNull(result).getOrThrow()
        }
        fun phase(message: String) = instrumentation.sendStatus(0, Bundle().apply { putString("stream", "PASS: $message\n") })
        val delegate = main { input.di.direct.instance<PopupDelegate>() }
        val service = main { input.di.direct.instance<TrimeInputMethodService>() }
        val scope = main { checkNotNull(ColorManager.currentScope()) }
        val originalScheme = main { ColorManager.activeColorScheme }
        val originalPreference = ThemeManager.prefs.normalModeColor.getValue()
        val originalEditor = main { Triple(editor.text.toString(), editor.selectionStart, editor.selectionEnd) }
        val token = main { service.editorToken }
        val free = main { field<LinkedList<PopupEntryUi>>(delegate, "freeEntryUi") }
        val originalFree = main { free.toList() }
        val entries = main { field<Map<Int, PopupEntryUi>>(delegate, "showingEntryUi") }
        val containers = main { field<Map<Int, Any>>(delegate, "showingContainerUi") }
        val ids = main { List(4) { View.generateViewId() } }
        val first = originalScheme +
            mapOf(
                "popup_back_color" to "#e4edf6",
                "popup_text_color" to "#173451",
                "hilited_popup_back_color" to "#214365",
                "hilited_popup_text_color" to "#f2e1c0",
            )
        val second = originalScheme +
            mapOf(
                "popup_back_color" to "#253647",
                "popup_text_color" to "#e6d5c4",
                "hilited_popup_back_color" to "#d3b291",
                "hilited_popup_text_color" to "#102f4e",
            )
        fun action(action: PopupAction) = delegate.listener.onPopupAction(action)
        fun unchangedEditor() {
            check(service.isCurrentEditor(token) && service.currentInputEditorInfo?.fieldId == editor.id)
            check(Triple(editor.text.toString(), editor.selectionStart, editor.selectionEnd) == originalEditor) { "Popup recolor sent input or changed the selection" }
            check(input.isShown && input.isAttachedToWindow && input.di.direct.instance<PopupDelegate>() === delegate)
            check(ColorManager.currentScope() === scope)
        }
        main {
            unchangedEditor()
            check(entries.isEmpty() && containers.isEmpty() && field<Map<*, *>>(delegate, "dismissJobs").isEmpty()) { "Probe requires idle popup state" }
        }
        try {
            val trigger = main {
                // Preserve pre-existing cached objects; the controlled pool below uses only probe-created entries.
                free.clear()
                val key = descendants(input).filterIsInstance<KeyView>().first { it.isShown && it.width > 0 && it.height > 0 }
                val point = IntArray(2)
                key.getLocationInWindow(point)
                Rect(point[0], point[1], point[0] + key.width, point[1] + key.height)
            }
            main { ColorManager.setColorSchemeForTesting(first) }
            main { repeat(4) { action(PopupAction.PreviewAction(ids[it], "A", trigger)) } }
            withTimeout(10_000) { while (!main { entries.size == 4 && entries.values.all { it.root.isShown && it.root.width > 0 && it.root.height > 0 } }) delay(50) }
            main {
                delegate.dismissAll()
                check(free.size == 4)
                action(PopupAction.PreviewAction(ids[0], "A", trigger))
                action(PopupAction.PreviewAction(ids[1], "ic@backspace_outline", trigger))
                action(PopupAction.ShowKeyboardAction(ids[2], listOf("T9_backspace", "a", "b"), trigger))
                check(free.size == 1)
            }
            withTimeout(10_000) { while (!main { entries.size == 3 && containers.size == 1 && entries.values.all { it.root.isShown && it.root.width > 0 } && (containers[ids[2]] as PopupKeyboardUi).root.height > 0 }) delay(50) }
            val visible = main { entries.toMap() }
            val pooled = main { free.single() }
            val keyboard = main { containers[ids[2]] as PopupKeyboardUi }
            val keys = main { field<List<PopupKeyboardUi.PopupKeyUi>>(keyboard, "keyUis") }
            val views = main { visible.values.flatMap { descendants(it.root) } + descendants(keyboard.root) }
            val geometry = main { views.map(::bounds) }
            var expectedFocus = "T9_backspace"
            fun verifyColors() {
                unchangedEditor()
                val palette = if (ColorManager.activeColorScheme == first) {
                    first
                } else {
                    check(ColorManager.activeColorScheme == second)
                    second
                }
                val rejectedPalette = if (palette == first) second else first
                check(scope.colors.popupBackColor == Color.parseColor(palette.getValue("popup_back_color")))
                check(scope.colors.popupTextColor == Color.parseColor(palette.getValue("popup_text_color")))
                check(scope.colors.hilitedPopupBackColor == Color.parseColor(palette.getValue("hilited_popup_back_color")))
                check(scope.colors.hilitedPopupTextColor == Color.parseColor(palette.getValue("hilited_popup_text_color")))
                check(entries.keys == visible.keys && entries.all { (id, entry) -> visible[id] === entry })
                check(free.single() === pooled && containers[ids[2]] === keyboard)
                check(views.map(::bounds) == geometry) { "Scheme switch changed popup geometry" }
                for (entry in visible.values + pooled) {
                    pixels(input.context, checkNotNull(entry.root.background), scope.colors.popupBackColor, true)
                    check(entry.textView.currentTextColor == scope.colors.popupTextColor)
                    entry.imageView.drawable?.let { pixels(input.context, it, scope.colors.popupTextColor, false, Color.parseColor(rejectedPalette.getValue("popup_text_color"))) }
                }
                check(visible.getValue(ids[0]).textView.text.toString() == "A")
                check(visible.getValue(ids[1]).imageView.visibility == View.VISIBLE)
                val focused = field<Int>(keyboard, "focusedIndex")
                pixels(input.context, checkNotNull(keyboard.root.background), scope.colors.popupBackColor, true)
                check(keys.size == 3 && keys[0].imageView.visibility == View.VISIBLE)
                keys.forEachIndexed { index, key ->
                    val color = if (index == focused) scope.colors.hilitedPopupTextColor else scope.colors.popupTextColor
                    check(key.textView.currentTextColor == color)
                    key.imageView.drawable?.let { pixels(input.context, it, color, false, Color.parseColor(rejectedPalette.getValue(if (index == focused) "hilited_popup_text_color" else "popup_text_color"))) }
                    if (index == focused) pixels(input.context, checkNotNull(key.root.background), scope.colors.hilitedPopupBackColor, true) else check(key.root.background == null)
                }
                val triggerAction = PopupAction.TriggerAction(ids[2])
                action(triggerAction)
                check(triggerAction.outAction == expectedFocus) { "Scheme switch changed popup focus/action" }
            }
            main { verifyColors() }
            // Each separate runOnMainSync is a queue barrier after the service's posted color listener.
            // Calling refreshColors directly here would miss a broken InputView -> PopupDelegate chain.
            main { ColorManager.setColorSchemeForTesting(second) }
            main { verifyColors() }
            phase("popup scheme A to B recolors visible text/icon, pooled bubble and focused long-press icon in place")
            main {
                val key = keys[1].root
                val target = bounds(key)
                val origin = bounds(keyboard.root)
                action(PopupAction.ChangeFocusAction(ids[2], target.exactCenterX() - origin.left + keyboard.offsetX, target.exactCenterY() - origin.top + keyboard.offsetY))
                expectedFocus = "a"
                verifyColors()
            }
            main { ColorManager.setColorSchemeForTesting(first) }
            main { verifyColors() }
            phase("popup scheme B to A preserves long-press text focus, geometry and editor state")
            main {
                action(PopupAction.PreviewAction(ids[3], "R", trigger))
                check(entries[ids[3]] === pooled && free.isEmpty()) { "Popup did not reuse the recolored pooled entry" }
                check(pooled.textView.text.toString() == "R" && pooled.textView.currentTextColor == scope.colors.popupTextColor)
                pixels(input.context, checkNotNull(pooled.root.background), scope.colors.popupBackColor, true)
                unchangedEditor()
            }
            phase("reused popup retains the current scheme without replacing its pooled object")
        } finally {
            main {
                delegate.dismissAll()
                free.clear()
                free.addAll(originalFree)
                ColorManager.setColorSchemeForTesting(originalScheme)
                ThemeManager.prefs.normalModeColor.setValue(originalPreference)
            }
            main {
                unchangedEditor()
                check(ColorManager.activeColorScheme == originalScheme && ThemeManager.prefs.normalModeColor.getValue() == originalPreference)
                check(entries.isEmpty() && containers.isEmpty() && field<Map<*, *>>(delegate, "dismissJobs").isEmpty())
                check(free.size == originalFree.size && free.zip(originalFree).all { (now, before) -> now === before })
            }
        }
        phase("popup probe restored the original scheme, preference, cached entries and editor")
    }
}
