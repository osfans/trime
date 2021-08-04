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
import android.media.AudioManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.view.KeyEvent;

import com.osfans.trime.ime.core.Preferences;

import java.util.Locale;

/** 處理按鍵聲音、震動、朗讀等效果 */
public class Effect {
  private static final int MAX_VOLUME = 101; //100%音量時只響一下，暫從100改成101
  private int duration = 10;
  private long durationLong;
  private VibrationEffect vibrationeffect;
  private int amplitude = -1;
  private int volume = 100;
  private float volumeFloat;

  private final Context context;

  private boolean vibrateOn;
  private Vibrator vibrator;
  private boolean soundOn;
  private AudioManager audioManager;
  private boolean isSpeakCommit, isSpeakKey;
  private TextToSpeech mTTS;

  private Preferences getPrefs() { return Preferences.Companion.defaultInstance(); };

  public Effect(Context context) {
    this.context = context;
  }

  public void reset() {
    duration = getPrefs().getKeyboard().getVibrationDuration();
    durationLong = duration * 1L;
    amplitude = getPrefs().getKeyboard().getVibrationAmplitude();
    vibrateOn = getPrefs().getKeyboard().getVibrationEnabled() && (duration > 0);
    if (vibrateOn) {
      if (vibrator == null) {
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrationeffect = VibrationEffect.createOneShot(durationLong, (amplitude == 0) ? VibrationEffect.DEFAULT_AMPLITUDE : amplitude);
      }
    }

    volume = getPrefs().getKeyboard().getSoundVolume();
    volumeFloat = (float) (1 - (Math.log(MAX_VOLUME - volume) / Math.log(MAX_VOLUME)));
    soundOn = getPrefs().getKeyboard().getSoundEnabled();
    if (soundOn && (audioManager == null)) {
      audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    isSpeakCommit = getPrefs().getKeyboard().isSpeakCommit();
    isSpeakKey = getPrefs().getKeyboard().isSpeakKey();
    if (mTTS == null && (isSpeakCommit || isSpeakKey)) {
      mTTS =
          new TextToSpeech(
              context,
              new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                  //初始化結果
                }
              });
    }
  }

  public void vibrate() {
    if (vibrateOn && (vibrator != null)) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && (vibrationeffect != null))
        vibrator.vibrate(vibrationeffect);
      else
        vibrator.vibrate(durationLong);// deprecated in api level 26
    }
  }

  public void playSound(final int code) {
    if (soundOn && (audioManager != null)) {
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
      audioManager.playSoundEffect(sound, volumeFloat);
    }
  }

  public void setLanguage(Locale loc) {
    if (mTTS != null) mTTS.setLanguage(loc);
  }

  private void speak(CharSequence text) {
    if (text != null && mTTS != null) mTTS.speak(text.toString(), TextToSpeech.QUEUE_FLUSH, null);
  }

  public void speakCommit(CharSequence text) {
    if (isSpeakCommit) speak(text);
  }

  public void speakKey(CharSequence text) {
    if (isSpeakKey) speak(text);
  }

  public void speakKey(int code) {
    if (code <= 0) return;
    String text =
        KeyEvent.keyCodeToString(code)
            .replace("KEYCODE_", "")
            .replace("_", " ")
            .toLowerCase(Locale.getDefault());
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
