/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.soundeffect

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.osfans.trime.core.data.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.keyboard.InputFeedbackManager
import com.osfans.trime.util.FileUtils
import kotlinx.serialization.decodeFromString
import timber.log.Timber
import java.io.File

object SoundEffectManager {
    private val selectionLock = Any()

    private val userDir: File
        get() {
            val dest = File(DataManager.userDataDir, "soundeffect")
            val old = File(DataManager.userDataDir, "sound")
            return FileUtils.rename(old, dest.name).getOrDefault(dest.also { it.mkdirs() })
        }

    /**
     * Descriptors are standalone files with their own key spelling (`inOrder`,
     * `keyset`), so unlike [com.osfans.trime.data.theme.ThemeYaml] no naming
     * strategy applies and unknown keys are ignored.
     */
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false,
        ),
    )

    private fun listSounds(): MutableList<SoundEffect> {
        val files = userDir.listFiles { f -> f.name.endsWith("sound.yaml") }
        return files
            ?.mapNotNull decode@{ f ->
                val effect = try {
                    val result = yaml.decodeFromString<SoundEffect>(f.bufferedReader().use { it.readText() })
                    if (result.name.isEmpty()) {
                        result.copy(name = f.name.substringBefore("."))
                    } else {
                        result
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to decode sound effect descriptor '${f.absolutePath}'")
                    null
                }
                return@decode effect
            }?.toMutableList() ?: mutableListOf()
    }

    private fun getEffect(name: String) = userEffects.find { it.name == name }

    private val userEffects: MutableList<SoundEffect> get() = listSounds()

    private var soundEffectPref by AppPrefs.defaultInstance().keyboard.customSoundEffect

    fun switchEffect(name: String) {
        val effect = getEffect(name)
        if (effect == null) {
            Timber.w("Unknown sound effect '$name'")
            return
        }
        switchEffect(effect)
    }

    fun switchEffect(effect: SoundEffect) {
        synchronized(selectionLock) {
            activeSoundEffect = effect
            soundEffectPref = effect.name
        }
        InputFeedbackManager.reloadSoundEffects()
    }

    fun init() {
        val selected = synchronized(selectionLock) { soundEffectPref }
        val loaded = if (selected.isEmpty()) null else getEffect(selected)
        synchronized(selectionLock) {
            if (selected == soundEffectPref) activeSoundEffect = loaded
        }
    }

    @Volatile
    var activeSoundEffect: SoundEffect? = null
        private set

    val activeAudioPaths: List<String>
        get() {
            return activeSoundEffect?.let { e ->
                val subPath = e.folder
                e.sound.map { userDir.resolve(subPath).resolve(it).path }
            } ?: listOf()
        }

    fun getAllSoundEffects(): List<SoundEffect> = userEffects
}
