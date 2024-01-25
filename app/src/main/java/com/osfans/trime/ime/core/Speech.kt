/*
 * Copyright (C) 2015-present, osfans
 * waxaca@163.com https://github.com/osfans
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.osfans.trime.ime.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.StringRes
import com.blankj.utilcode.util.ToastUtils
import com.osfans.trime.R
import com.osfans.trime.data.opencc.OpenCCDictManager.convertLine
import com.osfans.trime.data.theme.Theme.Companion.get
import timber.log.Timber
import java.util.Arrays

/** [語音輸入][RecognitionListener]  */
class Speech(context: Context) : RecognitionListener {
    private val speechRecognizer: SpeechRecognizer?
    private val recognizerIntent: Intent

    init {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer.setRecognitionListener(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en");
        // recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        // recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
        // RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        // recognizerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "開始語音");
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
        ToastUtils.showShort(getErrorText(errorCode))
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
        ToastUtils.showShort("請開始說話：")
    }

    override fun onResults(results: Bundle) {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening()
            speechRecognizer.destroy()
        }
        Timber.i("onResults")
        val trime = Trime.getServiceOrNull()
        if (trime != null) {
            val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val openccConfig = get().style.getString("speech_opencc_config")
            for (result in matches!!) trime.commitText(convertLine(result!!, openccConfig))
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
