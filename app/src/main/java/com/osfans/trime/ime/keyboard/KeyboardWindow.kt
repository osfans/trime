package com.osfans.trime.ime.keyboard

import android.content.Context
import android.view.LayoutInflater
import android.widget.ViewAnimator
import com.osfans.trime.core.Rime
import com.osfans.trime.databinding.MainInputLayoutBinding
import com.osfans.trime.databinding.SymbolInputLayoutBinding
import com.osfans.trime.ime.core.Trime
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent

class KeyboardWindow(private val context: Context, private val service: Trime) {
    val oldMainInputView by lazy {
        MainInputLayoutBinding.inflate(LayoutInflater.from(context)).apply {
            with(mainKeyboardView) {
                onKeyboardActionListener = service.textInputManager
                setShowHint(!Rime.getOption("_hide_key_hint"))
                setShowSymbol(!Rime.getOption("_hide_key_symbol"))
                reset()
            }
        }
    }

    val oldSymbolInputView by lazy {
        SymbolInputLayoutBinding.inflate(LayoutInflater.from(context))
    }

    fun switchUiByIndex(index: Int) {
        if (view.displayedChild == index) return
        view.displayedChild = index
    }

    val view by lazy {
        ViewAnimator(context).apply {
            add(oldMainInputView.root, lParams(matchParent, matchParent))
            add(oldSymbolInputView.root, lParams(matchParent, matchParent))
        }
    }
}
