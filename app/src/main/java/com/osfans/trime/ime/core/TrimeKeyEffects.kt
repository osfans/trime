package com.osfans.trime.ime.core

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import com.osfans.trime.databinding.InputRootBinding
import java.util.Locale
import kotlin.math.ln

/**
 * Manage the key press effects, such as vibration, sound, speaking and so on.
 */
class TrimeKeyEffects(
    context: Context
) {
    private val prefs: Preferences = Preferences.defaultInstance()
    private var inputRootBinding: InputRootBinding? = null

    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var tts: TextToSpeech? = null

    init {
        try {
            inputRootBinding = InputRootBinding.inflate(LayoutInflater.from(context))
            vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            tts = TextToSpeech(context) { }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Makes a key press vibration if the user has this feature enabled in the preferences.
     */
    fun keyPressVibrate() {
        if (prefs.keyboard.vibrationEnabled) {
            var vibrationDuration = prefs.keyboard.vibrationDuration.toLong()
            var vibrationAmplitude = prefs.keyboard.vibrationAmplitude

            val hapticsPerformed = if (vibrationDuration <0 && vibrationAmplitude < 0) {
                inputRootBinding?.keyboard?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            } else {
                false
            }

            if (hapticsPerformed == true) {
                return
            }

            if (vibrationDuration == -1L) {
                vibrationDuration = 36
            }

            if (vibrationAmplitude == -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrationAmplitude = VibrationEffect.DEFAULT_AMPLITUDE
            } else if (vibrationAmplitude == -1) {
                vibrationAmplitude = 36
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
            val effect = when (keyCode) {
                KeyEvent.KEYCODE_SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                KeyEvent.KEYCODE_DEL -> AudioManager.FX_KEYPRESS_DELETE
                KeyEvent.KEYCODE_ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }

            if (soundVolume > 0) {
                audioManager!!.playSoundEffect(
                    effect,
                    (1 - (ln((101.0 - soundVolume)) / ln(101.0))).toFloat()
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
        inputRootBinding = null
        vibrator = null
        audioManager = null
        tts?.let {
            it.stop()
            destroy()
        }.also { tts = null }
    }
}
