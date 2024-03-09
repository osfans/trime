package com.osfans.trime.ime.dependency

import android.content.Context
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.symbol.LiquidKeyboard
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides

@InputScope
@Component
abstract class InputComponent(
    @get:InputScope @get:Provides protected val themedContext: Context,
    @get:InputScope @get:Provides protected val theme: Theme,
    @get:InputScope @get:Provides protected val service: TrimeInputMethodService,
) {
    abstract val quickBar: QuickBar
    abstract val keyboardWindow: KeyboardWindow
    abstract val liquidKeyboard: LiquidKeyboard
}
