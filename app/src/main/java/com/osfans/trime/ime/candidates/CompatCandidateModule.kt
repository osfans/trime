package com.osfans.trime.ime.candidates

import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import com.osfans.trime.core.RimeProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.daemon.launchOnReady
import com.osfans.trime.databinding.CandidateBarBinding
import com.osfans.trime.ime.broadcast.InputBroadcastReceiver
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import com.osfans.trime.ime.symbol.SymbolBoardType
import me.tatarka.inject.annotations.Inject

@InputScope
@Inject
class CompatCandidateModule(
    val context: Context,
    val service: TrimeInputMethodService,
    val rime: RimeSession,
) : InputBroadcastReceiver {
    val binding by lazy {
        CandidateBarBinding.inflate(LayoutInflater.from(context)).apply {
            root.setPageStr(
                { service.handleKey(KeyEvent.KEYCODE_PAGE_DOWN, 0) },
                { service.handleKey(KeyEvent.KEYCODE_PAGE_UP, 0) },
                { service.selectLiquidKeyboard(SymbolBoardType.CANDIDATE) },
            )
            candidates.run {
                setCandidateListener(service.textInputManager)
                rime.launchOnReady { shouldShowComment = !it.getRuntimeOption("_hide_comment") }
            }
        }
    }

    override fun onInputContextUpdate(ctx: RimeProto.Context) {
        binding.candidates.updateCandidatesFromMenu(ctx.menu)
    }
}
