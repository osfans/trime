package com.osfans.trime.ime.keyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import com.osfans.trime.ime.core.Preferences
import java.util.Locale
import kotlin.math.ln

/**
 * Manage the key press effects, such as vibration, sound, speaking and so on.
 */
class InputFeedbackManager(
    private val ims: InputMethodService
) {
    private val prefs: Preferences = Preferences.defaultInstance()

    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var tts: TextToSpeech? = null

    init {
        try {
            vibrator = ims.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            audioManager = ims.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            tts = TextToSpeech(ims.applicationContext) { }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Makes a key press vibration if the user has this feature enabled in the preferences.
     */
    fun keyPressVibrate() {
        if (prefs.keyboard.vibrationEnabled) {
            val vibrationDuration = prefs.keyboard.vibrationDuration.toLong()
            var vibrationAmplitude = prefs.keyboard.vibrationAmplitude

            val hapticsPerformed = if (vibrationDuration < 0) {
                ims.window?.window?.decorView?.performHapticFeedback(
                    HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
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
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(
                        vibrationDuration, vibrationAmplitude
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(vibrationDuration)
            }
        }
    }

    /** Text to Speech engine's language getter and setter */
    var ttsLanguage: Locale?
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts?.voice?.locale
            } else {
                @Suppress("DEPRECATION")
                tts?.language
            }
        }
        set(v) { tts?.language = v }

    /**
     * Makes a key press sound if the user has this feature enabled in the preferences.
     */
    fun keyPressSound(keyCode: Int? = null) {
        if (prefs.keyboard.soundEnabled) {
            val soundVolume = prefs.keyboard.soundVolume
            if (Sound.isEnable())
                Sound.get().play(keyCode, soundVolume)
            else {
                if (soundVolume > 0) {
                    val effect = when (keyCode) {
                        KeyEvent.KEYCODE_SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                        KeyEvent.KEYCODE_DEL -> AudioManager.FX_KEYPRESS_DELETE
                        KeyEvent.KEYCODE_ENTER -> AudioManager.FX_KEYPRESS_RETURN
                        else -> AudioManager.FX_KEYPRESS_STANDARD
                    }
                    audioManager!!.playSoundEffect(
                        effect,
                        (1 - (ln((101.0 - soundVolume)) / ln(101.0))).toFloat()
                    )
                }
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
        val text = when {
            0 is T -> {
                KeyEvent.keyCodeToString(content as Int)
                    .replace("KEYCODE_", "")
                    .replace("_", " ")
                    .lowercase(Locale.getDefault())
            }
            "" is T -> content as String
            else -> null
        } ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "TrimeTTS")
        } else {
            @Suppress("DEPRECATION")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    fun destroy() {
        vibrator = null
        audioManager = null
        if (tts != null) {
            tts?.stop().also { tts = null }
        }
    }
}
