package com.osfans.trime.ime.keyboard

import android.inputmethodservice.InputMethodService
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.speech.tts.TextToSpeech
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.sound.SoundThemeManager
import splitties.systemservices.audioManager
import splitties.systemservices.vibrator
import timber.log.Timber
import java.util.Locale

/**
 * Manage the key press effects, such as vibration, sound, speaking and so on.
 */
class InputFeedbackManager(
    private val ims: InputMethodService,
) {
    private val prefs: AppPrefs get() = AppPrefs.defaultInstance()

    private var tts: TextToSpeech? = null
    private var soundPool: SoundPool? = null

    private var playProgress = -1
    private var lastPressedKeycode = 0
    private val soundIds: MutableList<Int> = mutableListOf()

    init {
        try {
            tts = TextToSpeech(ims) { }
            SoundThemeManager.init()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resumeSoundPool() {
        SoundThemeManager.getActiveSoundFilePaths().onSuccess { path ->
            soundPool =
                SoundPool.Builder()
                    .setMaxStreams(1)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setLegacyStreamType(AudioManager.STREAM_SYSTEM)
                            .build(),
                    ).build()
            soundIds.clear()
            soundIds.addAll(path.map { soundPool!!.load(it, 1) })
        }
    }

    fun releaseSoundPool() {
        SoundThemeManager.getActiveSoundTheme().onSuccess {
            soundPool?.release()
            soundPool = null
        }
    }

    /**
     * Makes a key press vibration if the user has this feature enabled in the preferences.
     */
    fun keyPressVibrate() {
        if (prefs.keyboard.vibrationEnabled) {
            val vibrationDuration = prefs.keyboard.vibrationDuration.toLong()
            var vibrationAmplitude = prefs.keyboard.vibrationAmplitude

            val hapticsPerformed =
                if (vibrationDuration < 0) {
                    ims.window?.window?.decorView?.performHapticFeedback(
                        HapticFeedbackConstants.KEYBOARD_TAP,
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                        } else {
                            @Suppress("DEPRECATION")
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING or HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                        },
                    )
                } else {
                    false
                }

            if (hapticsPerformed == true) {
                return
            }

            if (vibrationAmplitude > 0) {
                vibrationAmplitude = (vibrationAmplitude / 2.0).toInt().coerceAtLeast(1)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        vibrationDuration,
                        vibrationAmplitude,
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(vibrationDuration)
            }
        }
    }

    /** Text to Speech engine's language getter and setter */
    var ttsLanguage: Locale?
        get() {
            return tts?.voice?.locale
        }
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
        SoundThemeManager.getActiveSoundTheme().onSuccess { theme ->
            if (theme.sound.isEmpty()) return
            val sounds = theme.sound
            val melody = theme.melody
            var currentSoundId = 0
            if (playProgress > -1) {
                if (melody.isNullOrEmpty()) return
                currentSoundId = sounds.indexOf(melody[playProgress])
                playProgress = (playProgress + 1) % melody.size
            } else if (keycode != lastPressedKeycode) {
                lastPressedKeycode = keycode
                currentSoundId = theme.keyset.find { it.soundId(keycode) >= 0 }?.soundId(keycode) ?: 0
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

    fun destroy() {
        tts?.stop()
        tts = null
        releaseSoundPool()
    }
}
