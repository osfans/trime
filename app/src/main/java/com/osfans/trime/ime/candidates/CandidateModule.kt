/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.candidates

import android.content.Context
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.ime.bar.QuickBar
import com.osfans.trime.ime.candidates.compact.CompactCandidateModule
import com.osfans.trime.ime.candidates.suggestion.SuggestionCandidateModule
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.dependency.InputScope
import me.tatarka.inject.annotations.Inject

@InputScope
@Inject
class CandidateModule(
    val context: Context,
    val service: TrimeInputMethodService,
    val rime: RimeSession,
    val theme: Theme,
    val bar: QuickBar,
) {
    val compactCandidateModule = CompactCandidateModule(context, service, rime, theme, bar)
    val suggestionCandidateModule = SuggestionCandidateModule(context, service, rime, theme, bar)
}
