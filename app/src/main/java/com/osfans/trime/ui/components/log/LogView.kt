package com.osfans.trime.ui.components.log

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import androidx.core.content.ContextCompat
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.R
import com.osfans.trime.util.Logcat
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * A scroll view to look up the app log.
 *
 * This file is adapted from fcitx5-android project.
 * Source:
 * [fcitx5-android/LogView](https://github.com/fcitx5-android/fcitx5-android/blob/24457e13b7c3f9f59a6f220db7caad3d02f27651/app/src/main/java/org/fcitx/fcitx5/android/ui/main/log/LogView.kt)
 */
class LogView @JvmOverloads constructor(context: Context, attributeSet: AttributeSet? = null) :
    HorizontalScrollView(context, attributeSet) {

    private var logcat: Logcat? = null

    private val logAdapter = LogAdapter()

    private val recyclerView = RecyclerView(context).apply {
        adapter = logAdapter
        layoutManager = LinearLayoutManager(context).apply {
            orientation = LinearLayoutManager.VERTICAL
        }
    }

    init {
        addView(
            recyclerView,
            LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    override fun onDetachedFromWindow() {
        logcat?.shutdownLogFlow()
        super.onDetachedFromWindow()
    }

    fun fromCustomLogLines(lines: List<String>) {
        lines.onEach {
            dyeAndAppendString(it)
        }
    }

    fun setLogcat(logcat: Logcat) {
        this.logcat = logcat
        logcat.initLogFlow()
        logcat.logFlow.onEach {
            dyeAndAppendString(it)
        }.launchIn(findViewTreeLifecycleOwner()!!.lifecycleScope)
    }

    private fun dyeAndAppendString(str: String) {
        val color = ContextCompat.getColor(
            context,
            when (str.first()) {
                'V' -> R.color.grey_700
                'D' -> R.color.grey_700
                'I' -> R.color.blue_500
                'W' -> R.color.yellow_800
                'E' -> R.color.red_400
                'F' -> R.color.red_A700
                else -> R.color.colorPrimary
            },
        )
        logAdapter.append(
            SpannableString(str).apply {
                setSpan(
                    ForegroundColorSpan(color),
                    0,
                    str.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            },
        )
    }

    val currentLog: String
        get() = logAdapter.fullLogString()

    fun clear() {
        logAdapter.clear()
    }
}
