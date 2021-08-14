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

package com.osfans.trime.ime.core;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.blankj.utilcode.util.ToastUtils;
import com.osfans.trime.R;
import com.osfans.trime.Rime;
import com.osfans.trime.setup.Config;
import java.util.ArrayList;
import java.util.Arrays;
import timber.log.Timber;

/** {@link RecognitionListener 語音輸入} */
public class Speech implements RecognitionListener {
  private final @NonNull Context context;
  private final @Nullable SpeechRecognizer speechRecognizer;
  private final Intent recognizerIntent;

  public Speech(@NonNull Context context) {
    this.context = context;
    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
    speechRecognizer.setRecognitionListener(this);
    recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
    // recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en");
    // recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
    // recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
    // RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    // recognizerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "開始語音");
  }

  public void startListening() {
    if (speechRecognizer != null) speechRecognizer.startListening(recognizerIntent);
  }

  @Override
  public void onBeginningOfSpeech() {
    Timber.i("onBeginningOfSpeech");
  }

  @Override
  public void onBufferReceived(byte[] buffer) {
    Timber.i("onBufferReceived: %s", Arrays.toString(buffer));
  }

  @Override
  public void onEndOfSpeech() {
    Timber.i("onEndOfSpeech");
  }

  @Override
  public void onError(int errorCode) {
    if (speechRecognizer != null) {
      speechRecognizer.stopListening();
      speechRecognizer.destroy();
    }

    ToastUtils.showShort(getErrorText(errorCode));
  }

  @Override
  public void onEvent(int arg0, Bundle arg1) {
    Timber.i("onEvent");
  }

  @Override
  public void onPartialResults(Bundle arg0) {
    Timber.i("onPartialResults");
  }

  @Override
  public void onReadyForSpeech(Bundle arg0) {
    Timber.i("onReadyForSpeech");
    ToastUtils.showShort("請開始說話：");
  }

  @Override
  public void onResults(Bundle results) {
    if (speechRecognizer != null) {
      speechRecognizer.stopListening();
      speechRecognizer.destroy();
    }
    Timber.i("onResults");
    @Nullable Trime trime = Trime.getService();
    if (trime == null) return;
    final ArrayList<String> matches =
        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    final String openccConfig = Config.get(context).getString("speech_opencc_config");
    for (String result : matches) trime.commitText(Rime.openccConvert(result, openccConfig));
  }

  @Override
  public void onRmsChanged(float rmsdB) {
    Timber.i("onRmsChanged: %s", rmsdB);
  }

  private static @StringRes int getErrorText(int errorCode) {
    final int message;
    switch (errorCode) {
      case SpeechRecognizer.ERROR_AUDIO:
        message = R.string.speech__error_audio;
        break;
      case SpeechRecognizer.ERROR_CLIENT:
        message = R.string.speech__error_client;
        break;
      case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
        message = R.string.speech__error_insufficient_permissions;
        break;
      case SpeechRecognizer.ERROR_NETWORK:
        message = R.string.speech__error_network;
        break;
      case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
        message = R.string.speech__error_network_timeout;
        break;
      case SpeechRecognizer.ERROR_NO_MATCH:
        message = R.string.speech__error_no_match;
        break;
      case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
        message = R.string.speech__error_recognizer_busy;
        break;
      case SpeechRecognizer.ERROR_SERVER:
        message = R.string.speech__error_server;
        break;
      case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
        message = R.string.speech__error_speech_timeout;
        break;
      default:
        message = R.string.speech__error_unknown;
        break;
    }
    return message;
  }
}
