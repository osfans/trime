package com.osfans.trime.ime.dependency

import android.content.Context
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.broadcast.InputBroadcaster
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.KeyboardWindow
import com.osfans.trime.ime.symbol.LiquidKeyboard
import com.osfans.trime.ime.window.BoardWindowManager
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides

@InputScope
@Component
abstract class InputComponent(
    @get:InputScope @get:Provides val themedContext: Context,
    @get:InputScope @get:Provides val theme: Theme,
    @get:InputScope @get:Provides val service: TrimeInputMethodService,
) {
    abstract val broadcaster: InputBroadcaster
    abstract val quickBar: QuickBar
    abstract val windowManager: BoardWindowManager
    abstract val keyboardWindow: KeyboardWindow
    abstract val liquidKeyboard: LiquidKeyboard
}
