/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.core

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.res.Resources
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.R
import com.osfans.trime.core.CandidateSnapshot
import com.osfans.trime.core.Candidates
import com.osfans.trime.core.CompositionProto
import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.core.T9StateProto
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.data.theme.ThemePrefs
import com.osfans.trime.data.theme.ThemeScope
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import com.osfans.trime.util.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.dimensions.dp
import splitties.views.dsl.core.withTheme
import kotlin.math.max

abstract class BaseInputView(
    val service: TrimeInputMethodService,
    val rime: RimeSession,
    val scope: ThemeScope,
) : ConstraintLayout(service) {
    /** The theme config; geometry is theme-scoped, colors go through [scope.colors]. */
    val theme: Theme
        get() = scope.theme

    protected abstract fun handleRimeMessage(it: RimeMessage<*>)

    private var messageHandlerJob: Job? = null

    private fun setupRimeMessageHandler() {
        messageHandlerJob =
            service.lifecycleScope.launch {
                rime.run { messageFlow }.collect {
                    if (!it.isEditorResponse || service.isCurrentEditor(it.editorToken)) handleRimeMessage(it)
                }
            }
    }

    var handleMessages = false
        set(value) {
            field = value
            if (field) {
                if (messageHandlerJob == null) {
                    setupRimeMessageHandler()
                }
            } else {
                messageHandlerJob?.cancel()
                messageHandlerJob = null
            }
        }

    val themedContext = context.withTheme(android.R.style.Theme_DeviceDefault_Settings)

    private var candidateActionMenu: PopupMenu? = null
    private var candidateFullTextDialog: AlertDialog? = null
    private var candidateFeedbackDialog: AlertDialog? = null

    fun resetEditorUi() {
        dismissCandidateActionMenu()
        handleRimeMessage(RimeMessage.T9Message(T9StateProto()))
        handleRimeMessage(RimeMessage.CompositionMessage(CompositionProto()))
        handleRimeMessage(RimeMessage.BulkCandidatesMessage(Candidates.Bulk(total = 0)))
        handleRimeMessage(RimeMessage.PagedCandidatesMessage(Candidates.Paged()))
    }

    fun showCandidateActionMenu(idx: Int, text: String, view: View, global: Boolean, snapshot: CandidateSnapshot) {
        val editorToken = service.editorToken
        candidateActionMenu?.dismiss()
        candidateActionMenu = null
        val highlightColor = scope.colors.hilitedCandidateTextColor
        val title = buildSpannedString {
            bold {
                color(highlightColor) { append(text) }
            }
        }
        service.lifecycleScope.launch {
            if (!service.isCurrentEditor(editorToken) || !view.isAttachedToWindow ||
                !snapshot.isValid || snapshot != rime.run { candidateSnapshotCached }
            ) {
                return@launch
            }
            dismissCandidateFeedback()
            InputFeedbackManager.keyPressVibrate(view, longPress = true)
            candidateActionMenu =
                PopupMenu(themedContext, view).apply {
                    menu.add(title).apply {
                        isEnabled = false
                    }
                    if (!snapshot.isPrediction) {
                        menu.add(R.string.forget_this_word).setOnMenuItemClickListener {
                            runCandidateAction(editorToken, R.string.candidate_forgotten, R.string.candidate_forget_failed) {
                                deleteCandidate(idx, global, snapshot)
                            }
                            true
                        }
                        menu.add(R.string.candidate_pin).setOnMenuItemClickListener {
                            runCandidateAction(editorToken, R.string.candidate_pinned, R.string.candidate_action_failed) {
                                pinCandidate(idx, global, snapshot)
                            }
                            true
                        }
                        menu.add(R.string.candidate_restore_ranking).setOnMenuItemClickListener {
                            runCandidateAction(editorToken, R.string.candidate_ranking_restored, R.string.candidate_action_failed) {
                                resetCandidateRanking(snapshot)
                            }
                            true
                        }
                    }
                    menu.add(R.string.candidate_read_full_text).setOnMenuItemClickListener {
                        showCandidateFullText(text, editorToken)
                        true
                    }
                    setOnDismissListener {
                        candidateActionMenu = null
                    }
                    show()
                }
        }
    }

    private fun runCandidateAction(
        editorToken: Long,
        @StringRes success: Int,
        @StringRes failure: Int,
        action: suspend RimeApi.() -> Boolean,
    ) {
        if (!service.isCurrentEditor(editorToken) || !isAttachedToWindow) return
        dismissCandidateFeedback()
        service.postRimeJob {
            if (!service.isCurrentEditor(editorToken)) return@postRimeJob
            val applied = try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Neither the input text nor exception details belong in content logs.
                false
            }
            withContext(Dispatchers.Main.immediate) {
                if (service.isCurrentEditor(editorToken) && isAttachedToWindow) {
                    if (applied) {
                        service.toast(success)
                    } else {
                        showCandidateActionFailure(failure, editorToken)
                    }
                }
            }
        }
    }

    private fun dismissCandidateFeedback() {
        candidateFeedbackDialog?.dismiss()
        candidateFeedbackDialog = null
    }

    private fun showCandidateActionFailure(@StringRes failure: Int, editorToken: Long) {
        if (!service.isCurrentEditor(editorToken) || !isAttachedToWindow || windowToken == null) return
        dismissCandidateFeedback()
        val dialog = AlertDialog.Builder(themedContext)
            .setMessage(failure)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        // Keep the editor focused so its replacement can trigger the normal UI cleanup.
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        try {
            service.showDialog(dialog)
            // The service and AlertDialog add this flag; with NOT_FOCUSABLE it puts
            // the feedback below the keyboard instead of keeping it visible above it.
            dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            candidateFeedbackDialog = dialog
        } catch (_: WindowManager.BadTokenException) {
            dialog.dismiss()
            service.toast(failure)
        }
    }

    private fun showCandidateFullText(text: String, editorToken: Long) {
        if (!service.isCurrentEditor(editorToken) || !isAttachedToWindow || windowToken == null) return
        candidateFullTextDialog?.dismiss()
        val content = TextView(themedContext).apply {
            this.text = text
            textSize = 20f
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val scroll = ScrollView(themedContext).apply { addView(content) }
        val dialog = AlertDialog.Builder(themedContext)
            .setTitle(R.string.candidate_read_full_text)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        try {
            // Trime attaches the dialog to the live IME window, including on Android 5.
            service.showDialog(dialog)
            candidateFullTextDialog = dialog
        } catch (_: WindowManager.BadTokenException) {
            dialog.dismiss()
            service.toast(R.string.candidate_action_failed)
        }
    }

    private val navBarBackground by ThemeManager.prefs.navbarBackground

    private val navBarFrameHeight: Int
        get() {
            @SuppressLint("DiscouragedApi")
            val resId = resources.getIdentifier("navigation_bar_frame_height", "dimen", "android")
            return try {
                resources.getDimensionPixelSize(resId)
            } catch (_: Resources.NotFoundException) {
                dp(FALLBACK_NAVBAR_HEIGHT)
            }
        }

    private val ignoreSystemGestureInsets by AppPrefs.defaultInstance().advanced.ignoreSystemGestureInsets

    protected fun getNavBarBottomInset(windowInsets: WindowInsets): Int {
        if (navBarBackground != ThemePrefs.NavbarBackground.FULL) {
            return 0
        }
        val insets = WindowInsetsCompat.toWindowInsetsCompat(windowInsets)
        val navBars = WindowInsetsCompat.Type.navigationBars()
        val gestures = WindowInsetsCompat.Type.systemGestures() or
            WindowInsetsCompat.Type.mandatorySystemGestures()
        return if (ignoreSystemGestureInsets) {
            // check gestures insets and ignore them when it greater than zero
            val gesturesBottom = insets.getInsets(gestures).bottom
            val navBarsBottom = insets.getInsets(navBars).bottom
            if (gesturesBottom > 0) 0 else navBarsBottom
        } else {
            val insetsBottom = insets.getInsets(navBars or gestures).bottom
            if (insetsBottom > 0) max(insetsBottom, navBarFrameHeight) else insetsBottom
        }
    }

    fun dismissCandidateActionMenu() {
        candidateActionMenu?.dismiss()
        candidateActionMenu = null
        candidateFullTextDialog?.dismiss()
        candidateFullTextDialog = null
        dismissCandidateFeedback()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // on API 35+, we must call requestApplyInsets() manually after replacing views,
        // otherwise View#onApplyWindowInsets won't be called. ¯\_(ツ)_/¯
        requestApplyInsets()
    }

    override fun onDetachedFromWindow() {
        handleMessages = false
        dismissCandidateActionMenu()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val FALLBACK_NAVBAR_HEIGHT = 16
    }
}
