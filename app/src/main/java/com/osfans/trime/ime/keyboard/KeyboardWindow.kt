package com.osfans.trime.ime.keyboard

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.osfans.trime.databinding.MainInputLayoutBinding
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.window.BoardWindow
import com.osfans.trime.ime.window.ResidentWindow
import me.tatarka.inject.annotations.Inject

@InputScope
@Inject
class KeyboardWindow(context: Context, private val service: TrimeInputMethodService) : BoardWindow.NoBarBoardWindow(), ResidentWindow {
    val oldMainInputView by lazy {
        MainInputLayoutBinding.inflate(LayoutInflater.from(context))
    }

    companion object : ResidentWindow.Key

    override val key: ResidentWindow.Key
        get() = KeyboardWindow

    override fun onCreateView(): View {
        return oldMainInputView.root
    }

    override fun onAttached() {
        oldMainInputView.mainKeyboardView.onKeyboardActionListener = service.textInputManager
    }

    override fun onDetached() {
        oldMainInputView.mainKeyboardView.onKeyboardActionListener = null
    }
}
