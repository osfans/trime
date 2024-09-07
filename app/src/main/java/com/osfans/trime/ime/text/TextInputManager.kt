// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.text

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.core.Rime
import com.osfans.trime.core.RimeNotification
import com.osfans.trime.daemon.RimeSession
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.schema.SchemaManager
import com.osfans.trime.data.theme.EventManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.ime.core.InputView
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.Locale

/**
 * TextInputManager is responsible for managing everything which is related to text input. All of
 * the following count as text input: character, numeric (+advanced), phone and symbol layouts.
 *
 * All of the UI for the different keyboard layouts are kept under the same container element and
 * are separated from media-related UI. The core [TrimeInputMethodService] will pass any event defined in
 * [TrimeInputMethodService.EventListener] through to this class.
 *
 * TextInputManager is also the hub in the communication between the system, the active editor
 * instance and the CandidateView.
 */
class TextInputManager(
    private val trime: TrimeInputMethodService,
    private val rime: RimeSession,
) : TrimeInputMethodService.EventListener {
    private val prefs get() = AppPrefs.defaultInstance()
    private var rimeNotificationJob: Job? = null

    private val locales = Array(2) { Locale.getDefault() }

    var isComposable: Boolean = false

    private var shouldUpdateRimeOption
        get() = trime.shouldUpdateRimeOption
        set(value) {
            trime.shouldUpdateRimeOption = value
        }

    companion object {
        /** Delimiter regex to split language/locale tags. */
        private val DELIMITER_SPLITTER = """[-_]""".toRegex()
    }

    init {
        trime.addEventListener(this)
    }

    /**
     * Non-UI-related setup + preloading of all required computed layouts (asynchronous in the
     * background).
     */
    override fun onCreate() {
        super.onCreate()
        rimeNotificationJob =
            rime
                .run { notificationFlow }
                .onEach(::handleRimeNotification)
                .launchIn(trime.lifecycleScope)

        val theme = ThemeManager.activeTheme
        val defaultLocale = theme.generalStyle.locale.split(DELIMITER_SPLITTER)
        locales[0] =
            when (defaultLocale.size) {
                3 -> Locale(defaultLocale[0], defaultLocale[1], defaultLocale[2])
                2 -> Locale(defaultLocale[0], defaultLocale[1])
                else -> Locale.getDefault()
            }

        val latinLocale = theme.generalStyle.latinLocale.split(DELIMITER_SPLITTER)
        locales[1] =
            when (latinLocale.size) {
                3 -> Locale(latinLocale[0], latinLocale[1], latinLocale[2])
                2 -> Locale(latinLocale[0], latinLocale[1])
                else -> Locale.US
            }
    }

    /**
     * Cancels all coroutines and cleans up.
     */
    override fun onDestroy() {
        rimeNotificationJob?.cancel()
        rimeNotificationJob = null
    }

    override fun onStartInputView(
        info: EditorInfo,
        restarting: Boolean,
    ) {
        super.onStartInputView(info, restarting)
        trime.selectLiquidKeyboard(-1)
        isComposable =
            arrayOf(
                InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            ).none { it == info.inputType and InputType.TYPE_MASK_VARIATION }
        isComposable = isComposable && !rime.run { isEmpty() }
        trime.updateComposing()
    }

    private fun handleRimeNotification(notification: RimeNotification<*>) {
        if (notification is RimeNotification.SchemaNotification) {
            SchemaManager.init(notification.value.id)
            Rime.updateStatus()
            trime.recreateInputView()
            trime.inputView?.switchBoard(InputView.Board.Main)
        } else if (notification is RimeNotification.OptionNotification) {
            Rime.updateContext() // 切換中英文、簡繁體時更新候選
            val value = notification.value.value
            when (val option = notification.value.option) {
                "ascii_mode" -> {
                    InputFeedbackManager.ttsLanguage =
                        locales[if (value) 1 else 0]
                }
                "_hide_bar",
                "_hide_candidate",
                -> {
                    trime.setCandidatesViewShown(isComposable && !value)
                }
                "_liquid_keyboard" -> trime.selectLiquidKeyboard(0)
                else ->
                    if (option.startsWith("_key_") && option.length > 5 && value) {
                        shouldUpdateRimeOption = false // 防止在 handleRimeNotification 中 setOption
                        val key = option.substring(5)
                        trime.inputView
                            ?.commonKeyboardActionListener
                            ?.listener
                            ?.onEvent(EventManager.getEvent(key))
                        shouldUpdateRimeOption = true
                    }
            }
        }
    }
}
