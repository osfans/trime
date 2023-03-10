package com.osfans.trime.ui.components.log

import android.graphics.Typeface
import android.os.Build
import android.os.Build.VERSION_CODES
import android.text.SpannableString
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.textclassifier.TextClassifier
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.util.dp2px

/**
 * The recycler view adapter to contain the log text view.
 *
 * This file is adapted from fcitx5-android project.
 * Source: [fcitx5-android/LogAdapter](https://github.com/fcitx5-android/fcitx5-android/blob/24457e13b7c3f9f59a6f220db7caad3d02f27651/app/src/main/java/org/fcitx/fcitx5/android/ui/main/log/LogAdapter.kt)
 */
class LogAdapter(private val entries: MutableList<SpannableString> = mutableListOf()) :
    RecyclerView.Adapter<LogAdapter.ViewHolder>() {

    inner class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    fun append(line: SpannableString) {
        val size = entries.size
        entries.add(line)
        notifyItemInserted(size)
    }

    fun clear() {
        val size = entries.size
        entries.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun fullLogString() = entries.joinToString("\n")

    override fun getItemCount() = entries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            TextView(parent.context).apply {
                textSize = 12f
                typeface = Typeface.MONOSPACE
                if (Build.VERSION.SDK_INT >= VERSION_CODES.O) {
                    setTextClassifier(TextClassifier.NO_OP)
                }
                layoutParams = MarginLayoutParams(MarginLayoutParams.WRAP_CONTENT, MarginLayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp2px(4)
                    marginEnd = dp2px(4)
                }
            },
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = entries[position]
    }
}
