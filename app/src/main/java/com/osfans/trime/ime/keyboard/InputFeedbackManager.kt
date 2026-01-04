/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.speech.tts.TextToSpeech
import android.util.SparseIntArray
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import androidx.core.util.containsValue
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.data.soundeffect.SoundEffectManager
import splitties.systemservices.audioManager
import splitties.systemservices.vibrator
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Manage the key press effects, such as vibration, sound, speaking and so on.
 */
object InputFeedbackManager {
    private val keyboardPrefs = AppPrefs.defaultInstance().keyboard

    private var tts: TextToSpeech? = null
    private var soundPool: SoundPool? = null

    private var effectPlayProgress = 0
    private val cachedSoundIds = SparseIntArray()

    private val loadedSounds = ConcurrentHashMap<String, Int>()
    private var effectHash = 0

    fun init(context: Context) {
        try {
            tts = TextToSpeech(context, null)
            soundPool =
                SoundPool.Builder()
                    .setMaxStreams(3)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    ).build()
        } catch (e: Exception) {
            Timber.e(e, "Error on initializing InputFeedbackManager")
        }
    }

    private fun cacheSoundId() {
        if (!soundEffectEnabled) return

        if (SoundEffectManager.activeSoundEffect == null) {
            SoundEffectManager.init()
        }

        val paths = SoundEffectManager.activeAudioPaths
        val hash = paths.hashCode()

        if (hash == effectHash) return

        cachedSoundIds.clear()

        paths.forEachIndexed { i, path ->
            val soundId = loadedSounds.getOrPut(path) { soundPool?.load(path, 1) ?: 0 }
            if (soundId != 0 && !cachedSoundIds.containsValue(soundId)) {
                cachedSoundIds.put(i, soundId)
            }
        }

        effectHash = hash
    }

    fun startInput() {
        cacheSoundId()
    }

    fun reloadSoundEffects() {
        effectHash = 0
        cacheSoundId()
    }

    private val hasAmplitudeControl =
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) &&
            vibrator.hasAmplitudeControl()

    private val vibrateOnKeyPress by keyboardPrefs.vibrateOnKeyPress
    private val vibrationDuration by keyboardPrefs.vibrationDuration
    private val vibrationAmplitude by keyboardPrefs.vibrationAmplitude

    /**
     * Makes a key press vibration if the user has this feature enabled in the preferences.
     */
    fun keyPressVibrate(
        view: View,
        longPress: Boolean = false,
    ) {
        if (!vibrateOnKeyPress) return
        val duration: Long = vibrationDuration.toLong()
        val hfc =
            if (longPress) {
                HapticFeedbackConstants.LONG_PRESS
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }

        if (duration != 0L) { // use vibrator
            if (hasAmplitudeControl && vibrationAmplitude != 0) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, vibrationAmplitude))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ve = VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(ve)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } else {
            @Suppress("DEPRECATION")
            val flags =
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            view.performHapticFeedback(hfc, flags)
        }
    }

    private fun querySoundIndex(keyCode: Int): Int {
        val effect = SoundEffectManager.activeSoundEffect ?: return 0
        val sounds = effect.sound
        if (sounds.isEmpty()) return 0
        val melody = effect.melody
        return if (melody.isNotEmpty()) {
            val index = sounds.indexOf(melody[effectPlayProgress])
            effectPlayProgress = (effectPlayProgress + 1) % melody.size
            index
        } else {
            var index = 0
            for (key in effect.keyset) {
                val i = key.querySoundIndex(keyCode)
                if (i >= 0) {
                    index = i
                    break
                }
            }
            index
        }
    }

    private val soundOnKeyPress by keyboardPrefs.soundOnKeyPress
    private val soundEffectEnabled by keyboardPrefs.useCustomSoundEffect
    private val soundVolume by keyboardPrefs.soundVolume

    /**
     * Makes a key press sound if the user has this feature enabled in the preferences.
     */
    fun keyPressSound(keyCode: Int = 0) {
        if (!soundOnKeyPress) return
        if (soundEffectEnabled) {
            if (soundVolume <= 0) return
            val volume = soundVolume / 100f
            val index = querySoundIndex(keyCode)
            val soundId = cachedSoundIds[index]
            soundPool?.play(soundId, volume, volume, 0, 0, 1f)
        } else {
            val effect =
                when (keyCode) {
                    KeyEvent.KEYCODE_SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                    KeyEvent.KEYCODE_DEL -> AudioManager.FX_KEYPRESS_DELETE
                    KeyEvent.KEYCODE_ENTER -> AudioManager.FX_KEYPRESS_RETURN
                    else -> AudioManager.FX_KEYPRESS_STANDARD
                }
            val volume =
                if (soundVolume == 0) {
                    -1f
                } else {
                    soundVolume / 100f
                }
            audioManager.playSoundEffect(
                effect,
                volume,
            )
        }
    }

    private val speakOnKeyPress by keyboardPrefs.speakOnKeyPress
    private val speakOnCommit by keyboardPrefs.speakOnCommit

    fun keyPressSpeak(keyCode: Int) {
        if (!speakOnKeyPress) return
        contentSpeakInternal(keyCode)
    }

    fun textCommitSpeak(text: String) {
        if (!speakOnCommit) return
        contentSpeakInternal(text)
    }

    private inline fun <reified T> contentSpeakInternal(content: T) {
        val text =
            when {
                0 is T -> {
                    KeyEvent
                        .keyCodeToString(content as Int)
                        .replace("KEYCODE_", "")
                        .replace("_", " ")
                        .lowercase()
                }
                "" is T -> content as String
                else -> return
            }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TrimeTTS")
    }

    fun finishInput() {
        effectPlayProgress = 0
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        soundPool?.release()
        soundPool = null

        loadedSounds.clear()
        cachedSoundIds.clear()
        effectHash = 0
    }
}
