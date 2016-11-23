/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.osfans.trime;

import android.content.Context;
import android.media.AudioManager;
import android.os.Vibrator;
import android.view.KeyEvent;
import android.speech.tts.TextToSpeech;
import android.os.Build.VERSION_CODES;
import android.os.Build.VERSION;

import java.util.Locale;

/** 處理按鍵聲音、震動、朗讀等效果 */
public class Effect {
  private int duration = 30;
  private float volume = -1.0f;

  private final Context context;

  private Vibrator vibrator;
  private AudioManager audioManager;
  private boolean isSpeakCommit, isSpeakKey;
  private TextToSpeech mTTS;

  public Effect(Context context) {
    this.context = context;
  }

  public void reset() {
    Config config = Config.get();
    duration = config.getInt("key_vibrate_duration");
    if (duration > 0 && (vibrator == null)) {
      vibrator =
        (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    volume = config.getFloat("key_sound_volume");
    if (volume > 0) {
      if (audioManager == null) {
        audioManager =
          (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
      }
      int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
      volume = max * volume;
      audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM , (int)(volume), 0);
    }

    isSpeakCommit = config.getBoolean("speak_commit");
    isSpeakKey = config.getBoolean("speak_key");
    if (mTTS == null && (isSpeakCommit || isSpeakKey)) {
      mTTS = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
        public void onInit(int status) {
          //初始化結果
        }
      });
    }
  }

  public void vibrate() {
    if (duration > 0 && (vibrator != null)) vibrator.vibrate(duration);
  }

  public void playSound(final int code) {
    if (volume > 0 && (audioManager != null)) {
      final int sound;
      switch (code) {
        case KeyEvent.KEYCODE_DEL:
            sound = AudioManager.FX_KEYPRESS_DELETE;
            break;
        case KeyEvent.KEYCODE_ENTER:
            sound = AudioManager.FX_KEYPRESS_RETURN;
            break;
        case KeyEvent.KEYCODE_SPACE:
            sound = AudioManager.FX_KEYPRESS_SPACEBAR;
            break;
        default:
            sound = AudioManager.FX_KEYPRESS_STANDARD;
            break;
      }
      audioManager.playSoundEffect(sound, -1);
    }
  }

  public void setLanguage(Locale loc) {
    if (mTTS != null) mTTS.setLanguage(loc);
  }

  public void speak(CharSequence text) {
    if (text!= null && mTTS != null) mTTS.speak(text.toString(), TextToSpeech.QUEUE_FLUSH, null);
  }

  public void speakCommit(CharSequence text) {
    if (isSpeakCommit) speak(text);
  }

  public void speakKey(CharSequence text) {
    if (isSpeakKey) speak(text);
  }

  public void speakKey(int code) {
    if (code <= 0) return;
    if (VERSION.SDK_INT < VERSION_CODES.HONEYCOMB_MR1) return;
    String text = KeyEvent.keyCodeToString(code).replace("KEYCODE_","").replace("_", " ").toLowerCase(Locale.getDefault());
    speakKey(text);
  }

  public void destory() {
     if (mTTS != null) {
       mTTS.stop();
       mTTS.shutdown();
       mTTS = null;
     }
  }
}
