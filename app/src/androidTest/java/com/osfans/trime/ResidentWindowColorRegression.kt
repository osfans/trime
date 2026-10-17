/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime

import android.app.Instrumentation
import android.graphics.Color
import com.osfans.trime.data.theme.ColorManager
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.symbol.LiquidItemUi
import com.osfans.trime.ime.symbol.LiquidLayout
import com.osfans.trime.ime.symbol.LiquidWindow
import com.osfans.trime.ime.window.BoardWindowManager
import org.kodein.di.direct
import org.kodein.di.instance

/** Verifies that an off-screen resident view is restyled before its cached instance is reused. */
object ResidentWindowColorRegression {
    private inline fun <reified T> field(target: Any, name: String): T = target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target) as T

    suspend fun verify(
        instrumentation: Instrumentation,
        input: InputView,
    ) {
        fun <T> main(block: () -> T): T {
            var result: Result<T>? = null
            instrumentation.runOnMainSync { result = runCatching(block) }
            return checkNotNull(result).getOrThrow()
        }

        val manager = main { input.di.direct.instance<BoardWindowManager>() }
        val liquid = main { input.di.direct.instance<LiquidWindow>() }
        val original = main { ColorManager.activeColorScheme }
        val alternate = original + mapOf("key_text_color" to "#2468ac")
        val keyColor = Color.parseColor(alternate.getValue("key_text_color"))

        try {
            val cachedLayout =
                main {
                    manager.attachWindow(LiquidWindow)
                    field<LiquidLayout>(liquid, "liquidLayout")
                }
            val fixedItems = main { field<List<LiquidItemUi>>(cachedLayout, "fixedKeyItems") }
            check(fixedItems.isNotEmpty()) { "Probe theme has no fixed liquid keys" }

            main {
                manager.attachWindow(KeyboardWindow)
                ColorManager.setColorSchemeForTesting(alternate)
            }
            // Queue barrier for the service's posted color listener while LiquidWindow is inactive.
            main { }
            main { manager.attachWindow(LiquidWindow) }

            main {
                val reused = field<LiquidLayout>(liquid, "liquidLayout")
                check(reused === cachedLayout) { "LiquidWindow rebuilt its resident view" }
                check(fixedItems.all { it.mainText.currentTextColor == keyColor }) {
                    "Reattached LiquidWindow retained the previous scheme"
                }
            }
        } finally {
            main {
                ColorManager.setColorSchemeForTesting(original)
                manager.attachWindow(KeyboardWindow)
            }
            main { }
        }
    }
}
