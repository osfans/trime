// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates

import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.core.CandidateProto
import com.osfans.trime.core.CandidateSnapshot

/** A paged row retains the engine state and global index that produced it. */
data class CandidateRow(
    val candidate: CandidateProto,
    val index: Int,
    val snapshot: CandidateSnapshot,
)

class CandidateViewHolder(
    val ui: CandidateItemUi,
) : RecyclerView.ViewHolder(ui.root) {
    var idx = -1
    var text = ""
    var comment = ""
    var snapshot = CandidateSnapshot()
}
