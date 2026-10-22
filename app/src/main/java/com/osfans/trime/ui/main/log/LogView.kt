/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.log

import android.content.Context
import android.util.AttributeSet
import android.widget.HorizontalScrollView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.util.Logcat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.wrapContent
import splitties.views.dsl.recyclerview.recyclerView
import splitties.views.recyclerview.verticalLayoutManager

/**
 * A scroll view to look up the app log.
 *
 * This file is adapted from fcitx5-android project.
 * Source:
 * [fcitx5-android/LogView](https://github.com/fcitx5-android/fcitx5-android/blob/5ac719c3547165a3e77fe265c471a5a211580320/app/src/main/java/org/fcitx/fcitx5/android/ui/main/log/LogView.kt)
 */
class LogView
@JvmOverloads
constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
) : HorizontalScrollView(context, attributeSet) {
    private var logcat: Logcat? = null
    private var collectionJob: Job? = null

    private val logAdapter = LogAdapter()

    private val recyclerView =
        recyclerView {
            adapter = logAdapter
            layoutManager = verticalLayoutManager()
            itemAnimator = null
        }

    init {
        add(
            recyclerView,
            lParams(wrapContent, matchParent),
        )
    }

    override fun onDetachedFromWindow() {
        stopLogCollection()
        logAdapter.flushPending()
        super.onDetachedFromWindow()
    }

    private fun stopLogCollection() {
        logcat?.shutdownLogFlow()
        collectionJob?.cancel()
        collectionJob = null
    }

    private fun startLogCollection() {
        if (collectionJob?.isActive == true) return
        val source = logcat ?: return
        val scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: return
        if (!scope.isActive) return
        collectionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            source.collectLines(::appendLogcatLine)
        }
    }

    fun append(content: String) {
        logAdapter.append(content)
    }

    fun setLogcat(logcat: Logcat) {
        stopLogCollection()
        this.logcat = logcat
        startLogCollection()
    }

    private fun appendLogcatLine(line: String) {
        logAdapter.append(line, isLogcat = true, priority = Logcat.priorityOf(line))
    }

    val currentLog: String
        get() = logAdapter.logString(context.getString(R.string.log_content_omitted))

    fun clear() {
        logAdapter.clear()
    }

    fun scrollToBottom() {
        logAdapter.flushPending()
        if (logAdapter.itemCount != 0) recyclerView.scrollToPosition(logAdapter.itemCount - 1)
    }
}
