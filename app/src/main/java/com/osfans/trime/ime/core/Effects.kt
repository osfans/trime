package com.osfans.trime.ime.core

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import java.util.*
import kotlin.math.ln

/** Manages the effects like sound, vibration, speech and so on. */
class Effects(private val context: Context) {
    private val prefs = Preferences.defaultInstance()

    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var tts: TextToSpeech? = null

    init {
        vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        onCreateTTS()
    }

    /** Creates the TTS **/
    fun onCreateTTS() {
        if (prefs.keyboard.isSpeakKey
            || prefs.keyboard.isSpeakCommit) {
            tts = TextToSpeech(context) { } // 初始化結果
        }
    }

    /**
     * Shutdowns the TTS.
     * Remember to call this when not use it.
     */
    fun onDestroyTTS() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    /**
     * Makes a key press vibration if the user has this feature enabled in the preferences.
     * TODO("Haptic Feedback")
     */
    fun keyPressVibrate() {
        if (prefs.keyboard.vibrationEnabled) {
            val vibrationDuration = prefs.keyboard.vibrationDuration.toLong()
            var vibrationAmplitude = prefs.keyboard.vibrationAmplitude

            if (vibrationAmplitude == -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrationAmplitude = VibrationEffect.DEFAULT_AMPLITUDE
            } else if (vibrationAmplitude == -1) {
                vibrationAmplitude = 36
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

    /**
     * Makes a key press sound if the user has this feature enabled in the preferences.
     */
    fun keyPressSound(keyCode: Int) {
        if (prefs.keyboard.soundEnabled) {
            val soundVolume = prefs.keyboard.soundVolume
            val effect = when (keyCode) {
                KeyEvent.KEYCODE_DEL -> AudioManager.FX_KEYPRESS_DELETE
                KeyEvent.KEYCODE_ENTER -> AudioManager.FX_KEYPRESS_RETURN
                KeyEvent.KEYCODE_SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            if (soundVolume == -1) {
                audioManager!!.playSoundEffect(effect)
            } else {
                audioManager!!.playSoundEffect(
                    effect,
                    1 - (ln((101 - soundVolume).toDouble()) / ln(101.0)).toFloat()
                )
            }
        }
    }

    var ttsLanguage: Locale?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.voice?.locale
        } else {
            @Suppress("DEPRECATION")
            tts?.language
        }
        set(v) { tts?.language = v }

    /**
     * Makes [speakInternal] public since a public-API inline function
     * cannot access non-public-API ([tts]).
     *
     * @param content accepts [String] or [Int].
     */
    fun speak(content: Any) = speakInternal(content)

    /**
     * Speaks the [content].
     */
    private inline fun <reified T> speakInternal(content: T) {
        val text = when {
            0 is T && prefs.keyboard.isSpeakKey -> {
                if (content as Int <= 0) return
                KeyEvent.keyCodeToString(content as Int)
                    .replace("KEYCODE_","")
                    .replace("_"," ")
                    .lowercase(Locale.getDefault())
            }
            "" is T && (prefs.keyboard.isSpeakKey
                    || prefs.keyboard.isSpeakCommit) -> {
                content as String
            }
            else -> null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "Effects.speak")
        } else {
            @Suppress("DEPRECATION")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }
}