// Copyright (C) 2015-present, osfans
// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.StringRes
import com.osfans.trime.R
import com.osfans.trime.data.opencc.OpenCCDictManager
import com.osfans.trime.data.theme.ThemeManager
import com.osfans.trime.util.toast
import timber.log.Timber
import java.util.Arrays

/** [語音輸入][RecognitionListener]  */
class Speech(
    private val context: Context,
) : RecognitionListener {
    private val speechRecognizer =
        SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(this@Speech)
        }
    private val recognizerIntent: Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

    fun startListening() {
        speechRecognizer?.startListening(recognizerIntent)
    }

    override fun onBeginningOfSpeech() {
        Timber.i("onBeginningOfSpeech")
    }

    override fun onBufferReceived(buffer: ByteArray) {
        Timber.i("onBufferReceived: %s", Arrays.toString(buffer))
    }

    override fun onEndOfSpeech() {
        Timber.i("onEndOfSpeech")
    }

    override fun onError(errorCode: Int) {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening()
            speechRecognizer.destroy()
        }
        context.toast(getErrorText(errorCode))
    }

    override fun onEvent(
        arg0: Int,
        arg1: Bundle,
    ) {
        Timber.i("onEvent")
    }

    override fun onPartialResults(arg0: Bundle) {
        Timber.i("onPartialResults")
    }

    override fun onReadyForSpeech(arg0: Bundle) {
        Timber.i("onReadyForSpeech")
        context.toast("請開始說話：")
    }

    override fun onResults(results: Bundle) {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening()
            speechRecognizer.destroy()
        }
        Timber.i("onResults")
        val trime = TrimeInputMethodService.getServiceOrNull()
        if (trime != null) {
            val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val openccConfig = ThemeManager.activeTheme.generalStyle.speechOpenccConfig
            for (result in matches!!) trime.commitText(OpenCCDictManager.convertLine(result!!, openccConfig))
        }
    }

    override fun onRmsChanged(rmsdB: Float) {
        Timber.i("onRmsChanged: %s", rmsdB)
    }

    companion object {
        @StringRes
        private fun getErrorText(errorCode: Int): Int {
            val message: Int =
                when (errorCode) {
                    SpeechRecognizer.ERROR_AUDIO -> R.string.speech__error_audio
                    SpeechRecognizer.ERROR_CLIENT -> R.string.speech__error_client
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.speech__error_insufficient_permissions
                    SpeechRecognizer.ERROR_NETWORK -> R.string.speech__error_network
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> R.string.speech__error_network_timeout
                    SpeechRecognizer.ERROR_NO_MATCH -> R.string.speech__error_no_match
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.speech__error_recognizer_busy
                    SpeechRecognizer.ERROR_SERVER -> R.string.speech__error_server
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.speech__error_speech_timeout
                    else -> R.string.speech__error_unknown
                }
            return message
        }
    }
}
