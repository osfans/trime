// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.soundeffect

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.util.FileUtils
import timber.log.Timber
import java.io.File

object SoundEffectManager {
    private val yaml =
        Yaml(
            configuration =
                YamlConfiguration(
                    strictMode = false,
                ),
        )

    private val userDir: File
        get() {
            val dest = File(DataManager.userDataDir, "soundeffect")
            val old = File(DataManager.userDataDir, "sound")
            return FileUtils.rename(old, dest.name).getOrDefault(dest.also { it.mkdirs() })
        }

    private fun listSounds(): MutableList<SoundEffect> {
        val files = userDir.listFiles { f -> f.name.endsWith("sound.yaml") }
        return files
            ?.mapNotNull decode@{ f ->
                val effect =
                    runCatching {
                        val origin = yaml.decodeFromString(SoundEffect.serializer(), f.readText())
                        if (origin.name.isEmpty()) {
                            origin.copy(name = f.name.substringBefore('.'))
                        } else {
                            origin
                        }
                    }.getOrElse { e ->
                        Timber.w("Failed to decode sound effect file ${f.absolutePath}: ${e.message}")
                        return@decode null
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
        activeSoundEffect = effect
        soundEffectPref = name
    }

    fun init() {
        activeSoundEffect = getEffect(soundEffectPref) ?: return
    }

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
