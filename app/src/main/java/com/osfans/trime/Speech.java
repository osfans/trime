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

package com.osfans.trime;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.widget.Toast;
import java.util.ArrayList;

/** {@link RecognitionListener 語音輸入} */
public class Speech implements RecognitionListener {
  private SpeechRecognizer speech = null;
  private Intent recognizerIntent;
  private String TAG = "Speech";
  private Context context;

  public Speech(Context context) {
    this.context = context;
    speech = SpeechRecognizer.createSpeechRecognizer(context);
    speech.setRecognitionListener(this);
    recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
    //recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en");
    //recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
    //recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    //recognizerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "開始語音");
  }

  public void alert(String text) {
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
  }

  public void start() {
    speech.startListening(recognizerIntent);
  }

  public void stop() {
    speech.stopListening();
  }

  public void destory() {
    if (speech != null) {
      speech.destroy();
    }
  }

  @Override
  public void onBeginningOfSpeech() {
    Log.i(TAG, "onBeginningOfSpeech");
  }

  @Override
  public void onBufferReceived(byte[] buffer) {
    Log.i(TAG, "onBufferReceived: " + buffer);
  }

  @Override
  public void onEndOfSpeech() {
    Log.i(TAG, "onEndOfSpeech");
  }

  @Override
  public void onError(int errorCode) {
    speech.stopListening();
    speech.destroy();
    String errorMessage = getErrorText(errorCode);
    alert(errorMessage);
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
    alert("請開始說話：");
  }

  @Override
  public void onResults(Bundle results) {
    stop();
    destory();
    Log.i(TAG, "onResults");
    Trime trime = Trime.getService();
    if (trime == null) return;
    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    String opencc_config = Config.get(context).getString("speech_opencc_config");
    for (String result : matches) trime.commitText(Rime.openccConvert(result, opencc_config));
  }

  @Override
  public void onRmsChanged(float rmsdB) {
    Log.i(TAG, "onRmsChanged: " + rmsdB);
  }

  public static String getErrorText(int errorCode) {
    String message;
    switch (errorCode) {
      case SpeechRecognizer.ERROR_AUDIO:
        message = "錄音錯誤";
        break;
      case SpeechRecognizer.ERROR_CLIENT:
        message = "客戶端錯誤";
        break;
      case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
        message = "權限不足";
        break;
      case SpeechRecognizer.ERROR_NETWORK:
        message = "網絡錯誤";
        break;
      case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
        message = "網絡超時";
        break;
      case SpeechRecognizer.ERROR_NO_MATCH:
        message = "未能識別";
        break;
      case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
        message = "識別服務忙";
        break;
      case SpeechRecognizer.ERROR_SERVER:
        message = "服務器錯誤";
        break;
      case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
        message = "無語音輸入";
        break;
      default:
        message = "未知錯誤";
        break;
    }
    return message;
  }
}
