package com.osfans.trime.ime.keyboard

import android.content.Context
import android.view.LayoutInflater
import android.widget.ViewAnimator
import com.osfans.trime.core.Rime
import com.osfans.trime.databinding.MainInputLayoutBinding
import com.osfans.trime.databinding.SymbolInputLayoutBinding
import com.osfans.trime.ime.core.TrimeInputMethodService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent

class KeyboardWindow : KoinComponent {
    private val context: Context by inject()
    private val service: TrimeInputMethodService by inject()

    val oldMainInputView by lazy {
        MainInputLayoutBinding.inflate(LayoutInflater.from(context)).apply {
            with(mainKeyboardView) {
                setOnKeyboardActionListener(service.textInputManager)
                setShowHint(!Rime.getOption("_hide_key_hint"))
                setShowSymbol(!Rime.getOption("_hide_key_symbol"))
                reset()
            }
        }
    }

    val oldSymbolInputView by lazy {
        SymbolInputLayoutBinding.inflate(LayoutInflater.from(context))
    }

    enum class State {
        Main,
        Symbol,
    }

    fun switchUiByState(state: State) {
        if (view.displayedChild == state.ordinal) return
        view.displayedChild = state.ordinal
    }

    val view by lazy {
        ViewAnimator(context).apply {
            add(oldMainInputView.root, lParams(matchParent, matchParent))
            add(oldSymbolInputView.root, lParams(matchParent, matchParent))
        }
    }
}
