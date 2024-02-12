package com.osfans.trime.ime.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.speech.tts.TextToSpeech
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.sound.SoundEffectManager
import splitties.systemservices.audioManager
import splitties.systemservices.vibrator
import timber.log.Timber
import java.util.Locale

/**
 * Manage the key press effects, such as vibration, sound, speaking and so on.
 */
object InputFeedbackManager {
    private val prefs: AppPrefs get() = AppPrefs.defaultInstance()

    private var tts: TextToSpeech? = null
    private var soundPool: SoundPool? = null

    private var playProgress = -1
    private var lastPressedKeycode = 0
    private val soundIds: MutableList<Int> = mutableListOf()

    fun init(context: Context) {
        runCatching {
            tts = TextToSpeech(context, null)
            soundPool =
                SoundPool.Builder()
                    .setMaxStreams(1)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setLegacyStreamType(AudioManager.STREAM_SYSTEM)
                            .build(),
                    ).build()
            SoundEffectManager.init()
        }.getOrElse {
            Timber.w(it, "Failed to initialize InputFeedbackManager")
        }
    }

    fun loadSoundEffects() {
        SoundEffectManager.getActiveSoundFilePaths().onSuccess { path ->
            soundIds.clear()
            soundIds.addAll(path.map { soundPool?.load(it, 1) ?: 0 })
        }
    }

    private fun releaseSoundPool() {
        SoundEffectManager.getActiveSoundEffect().onSuccess {
            soundPool?.release()
            soundPool = null
        }
    }

    private val hasAmplitudeControl =
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) &&
            vibrator.hasAmplitudeControl()

    /**
     * Makes a key press vibration if the user has this feature enabled in the preferences.
     */
    fun keyPressVibrate(view: View) {
        if (prefs.keyboard.vibrationEnabled) {
            val duration = prefs.keyboard.vibrationDuration.toLong()
            val amplitude = prefs.keyboard.vibrationAmplitude

            if (duration != 0L) { // use vibrator
                if (hasAmplitudeControl && amplitude != 0) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
            } else {
                @Suppress("DEPRECATION")
                val flags =
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, flags)
            }
        }
    }

    /** Text to Speech engine's language getter and setter */
    var ttsLanguage: Locale?
        get() = tts?.voice?.locale
        set(v) {
            tts?.language = v
        }

    fun resetPlayProgress() {
        if (playProgress > 0) playProgress = 0
    }

    private fun playCustomSoundEffect(
        keycode: Int,
        volume: Float,
    ) {
        SoundEffectManager.getActiveSoundEffect().onSuccess { effect ->
            if (effect.sound.isEmpty()) return
            val sounds = effect.sound
            val melody = effect.melody
            var currentSoundId = 0
            if (playProgress > -1) {
                if (melody.isNullOrEmpty()) return
                currentSoundId = sounds.indexOf(melody[playProgress])
                playProgress = (playProgress + 1) % melody.size
            } else if (keycode != lastPressedKeycode) {
                lastPressedKeycode = keycode
                currentSoundId = effect.keyset.find { it.soundId(keycode) >= 0 }?.soundId(keycode) ?: 0
                Timber.d("play without melody: currentSoundId=$currentSoundId, soundIds.size=${soundIds.size}")
            }
            soundPool?.play(soundIds[currentSoundId], volume, volume, 1, 0, 1f)
        }
    }

    /**
     * Makes a key press sound if the user has this feature enabled in the preferences.
     */
    fun keyPressSound(keyCode: Int = 0) {
        if (prefs.keyboard.soundEnabled) {
            val soundVolume = prefs.keyboard.soundVolume / 100f
            if (soundVolume <= 0) return
            if (prefs.keyboard.customSoundEnabled) {
                playCustomSoundEffect(keyCode, soundVolume)
            } else {
                val effect =
                    when (keyCode) {
                        KeyEvent.KEYCODE_SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                        KeyEvent.KEYCODE_DEL -> AudioManager.FX_KEYPRESS_DELETE
                        KeyEvent.KEYCODE_ENTER -> AudioManager.FX_KEYPRESS_RETURN
                        else -> AudioManager.FX_KEYPRESS_STANDARD
                    }
                audioManager.playSoundEffect(
                    effect,
                    soundVolume,
                )
            }
        }
    }

    /**
     * Makes a key press speaking if the user has this feature enabled in the preferences.
     */
    fun keyPressSpeak(content: Any? = null) {
        if (prefs.keyboard.isSpeakKey) contentSpeakInternal(content)
    }

    /**
     * Makes a text commit speaking if the user has this feature enabled in the preferences.
     */
    fun textCommitSpeak(text: CharSequence? = null) {
        if (prefs.keyboard.isSpeakCommit) contentSpeakInternal(text)
    }

    private inline fun <reified T> contentSpeakInternal(content: T) {
        val text =
            when {
                0 is T -> {
                    KeyEvent.keyCodeToString(content as Int)
                        .replace("KEYCODE_", "")
                        .replace("_", " ")
                        .lowercase(Locale.getDefault())
                }
                "" is T -> content as String
                else -> null
            } ?: return

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TrimeTTS")
    }

    fun finishInput() {
        releaseSoundPool()
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        releaseSoundPool()
    }
}
