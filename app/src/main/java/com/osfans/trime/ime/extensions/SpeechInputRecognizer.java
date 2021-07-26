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

package com.osfans.trime.ime.extensions;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.annotation.StringRes;

import com.blankj.utilcode.util.ToastUtils;
import com.osfans.trime.setup.Config;
import com.osfans.trime.R;
import com.osfans.trime.Rime;
import com.osfans.trime.ime.core.Trime;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;

/** {@link RecognitionListener 語音輸入} */
public class SpeechInputRecognizer implements RecognitionListener {
    private final @NotNull SpeechRecognizer speechRecognizer;
    private final @NotNull Intent recognizerIntent;
    private final String TAG = "SpeechInputRecognizer";
    private final @NotNull Context context;

    public SpeechInputRecognizer(@NotNull Context context) {
        this.context = context;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        //recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en");
        //recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        //recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        //recognizerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "開始語音");
    }

    public void startListening() {
        speechRecognizer.startListening(recognizerIntent);
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.i(TAG, "onBeginningOfSpeech");
        ToastUtils.showShort(R.string.voice_input__on_beginning_of_speech_note);
    }

    @Override
    public void onBufferReceived(@Nullable byte[] buffer) {
        Log.i(TAG, "onBufferReceived: " + Arrays.toString(buffer));
    }

    @Override
    public void onEndOfSpeech() {
        Log.i(TAG, "onEndOfSpeech");
    }

    @Override
    public void onError(int errorCode) {
        speechRecognizer.stopListening();
        speechRecognizer.destroy();
        ToastUtils.showShort(getErrorText(errorCode));
    }

    @Override
    public void onEvent(int arg0, Bundle arg1) {
        Log.i(TAG, "onEvent");
    }

    @Override
    public void onPartialResults(Bundle arg0) {
        Log.i(TAG, "onPartialResults");
    }

    @Override
    public void onReadyForSpeech(Bundle arg0) {
        Log.i(TAG, "onReadyForSpeech");
        ToastUtils.showShort(R.string.voice_input__on_ready_for_speech_note);
    }

    @Override
    public void onResults(@Nullable Bundle results) {
        speechRecognizer.stopListening();
        speechRecognizer.destroy();
        Log.i(TAG, "onResults");
        if (results == null) return;
        Trime trime = Trime.getService();
        if (trime == null) return;
        final ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        final String openccConfig = Config.get(context).getString("speech_opencc_config");
        for (String result : matches) trime.commitText(Rime.openccConvert(result, openccConfig));
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        Log.i(TAG, "onRmsChanged: " + rmsdB);
    }

    @StringRes
    private static int getErrorText(int errorCode) {
        @StringRes int resId;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                resId = R.string.voice_input__error_audio;
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                resId = R.string.voice_input__error_client;
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                resId = R.string.voice_input__error_insuff_permissions;
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                resId = R.string.voice_input__error_network;
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                resId = R.string.voice_input__error_network_timeout;
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                resId = R.string.voice_input__error_no_match;
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                resId = R.string.voice_input__error_recognizer_busy;
                break;
            case SpeechRecognizer.ERROR_SERVER:
                resId = R.string.voice_input__error_server;
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                resId = R.string.voice_input__error_speech_timeout;
                break;
            default:
                resId = R.string.voice_input__error_unknow;
                break;
        }
        return resId;
    }
}
