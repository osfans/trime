package com.osfans.trime.ime.keyboard

import android.content.Context
import android.view.LayoutInflater
import android.widget.ViewAnimator
import com.osfans.trime.databinding.MainInputLayoutBinding
import com.osfans.trime.databinding.SymbolInputLayoutBinding
import com.osfans.trime.ime.core.TrimeInputMethodService
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent

class KeyboardWindow(context: Context, service: TrimeInputMethodService) {
    val oldMainInputView by lazy {
        MainInputLayoutBinding.inflate(LayoutInflater.from(context)).apply {
            mainKeyboardView.onKeyboardActionListener = service.textInputManager
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
