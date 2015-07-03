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
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Vibrator;
import android.preference.PreferenceManager;

/**
 * Plays sound and motion effect.
 */
public class Effect {
  private static final int VIBRATE_DURATION = 30;
  private static final float FX_VOLUME = -1.0f;

  private final Context context;
  private final SharedPreferences preferences;
  private final String vibrateKey = "pref_vibrate";
  private final String soundKey = "pref_sound";

  private boolean vibrateOn;
  private Vibrator vibrator;
  private boolean soundOn;
  private AudioManager audioManager;

  public Effect(Context context) {
    this.context = context;
    preferences = PreferenceManager.getDefaultSharedPreferences(context);
  }

  public void reset() {
    vibrateOn = preferences.getBoolean(vibrateKey, false);
    if (vibrateOn && (vibrator == null)) {
      vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    soundOn = preferences.getBoolean(soundKey, false);
    if (soundOn && (audioManager == null)) {
      audioManager = 
        (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }
  }

  public void vibrate() {
    if (vibrateOn && (vibrator != null)) {
      vibrator.vibrate(VIBRATE_DURATION);
    }
  }

  public void playSound(final int code) {
    if (soundOn && (audioManager != null)) {
            final int sound;
            switch (code) {
            case -5:
                sound = AudioManager.FX_KEYPRESS_DELETE;
                break;
            case '\n':
                sound = AudioManager.FX_KEYPRESS_RETURN;
                break;
            case ' ':
                sound = AudioManager.FX_KEYPRESS_SPACEBAR;
                break;
            default:
                sound = AudioManager.FX_KEYPRESS_STANDARD;
                break;
            }
      audioManager.playSoundEffect(sound, FX_VOLUME);
    }
  }
}
