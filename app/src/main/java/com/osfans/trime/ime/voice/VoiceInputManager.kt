/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.voice

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.os.Bundle
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.util.Locale

/**
 * Manager for voice input functionality in Trime input method
 */
class VoiceInputManager private constructor(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    companion object {
        fun create(
            context: Context,
            onResult: (String) -> Unit,
            onError: (String) -> Unit = { Timber.w("Voice input error: $it") }
        ): VoiceInputManager {
            return VoiceInputManager(context, onResult, onError)
        }

        fun isVoiceInputAvailable(context: Context): Boolean {
            return SpeechRecognizer.isRecognitionAvailable(context)
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Timber.d("Voice input ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Timber.d("Voice input speech started")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Optional: could be used for voice level visualization
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Not typically used
        }

        override fun onEndOfSpeech() {
            Timber.d("Voice input speech ended")
            isListening = false
        }

        override fun onError(error: Int) {
            isListening = false
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                else -> "Unknown error: $error"
            }
            Timber.e("Voice input error: $errorMessage")
            onError(errorMessage)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.let { matches ->
                if (matches.isNotEmpty()) {
                    val recognizedText = matches[0]
                    Timber.d("Voice input recognized: $recognizedText")
                    onResult(recognizedText)
                } else {
                    onError("No speech recognized")
                }
            } ?: run {
                onError("No recognition results")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            // Optional: could be used for real-time partial results
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Not typically used
        }
    }

    fun startVoiceInput() {
        if (isListening) {
            Timber.w("Voice input already in progress")
            return
        }

        if (!isVoiceInputAvailable(context)) {
            onError("Speech recognition not available")
            return
        }

        try {
            // Clean up any existing recognizer
            speechRecognizer?.destroy()
            
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            isListening = true
            speechRecognizer?.startListening(intent)
            Timber.d("Voice input started")
        } catch (e: Exception) {
            isListening = false
            Timber.e(e, "Failed to start voice input")
            onError("Failed to start voice recognition: ${e.message}")
        }
    }

    fun stopVoiceInput() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            Timber.d("Voice input stopped")
        }
    }

    fun destroy() {
        stopVoiceInput()
        speechRecognizer?.destroy()
        speechRecognizer = null
        Timber.d("Voice input manager destroyed")
    }

    fun isListening(): Boolean = isListening
}