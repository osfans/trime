package com.osfans.trime.ime.candidates

import androidx.recyclerview.widget.RecyclerView

class CandidateViewHolder(val ui: CandidateItemUi) : RecyclerView.ViewHolder(ui.root) {
    var idx = -1
    var text = ""
    var comment = ""
}
