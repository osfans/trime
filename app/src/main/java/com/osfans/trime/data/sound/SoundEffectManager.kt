// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.sound

import androidx.annotation.Keep
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.osfans.trime.data.base.DataManager
import com.osfans.trime.data.prefs.AppPrefs
import timber.log.Timber
import java.io.File

object SoundEffectManager {
    private var userDir = File(DataManager.userDataDir, "sound")

    /**
     * Update userDir.
     */
    @Keep
    private val onDataDirChange =
        DataManager.OnDataDirChangeListener {
            userDir = File(DataManager.userDataDir, "sound")
        }

    private val yaml =
        Yaml(
            configuration =
                YamlConfiguration(
                    strictMode = false,
                ),
        )

    private fun listSounds(): MutableList<SoundEffect> {
        val files = userDir.listFiles { f -> f.name.endsWith("sound.yaml") }
        return files
            ?.mapNotNull decode@{ f ->
                val theme =
                    runCatching {
                        yaml.decodeFromString(SoundEffect.serializer(), f.readText()).also {
                            if (it.name.isNullOrEmpty()) it.name = f.name.substringBefore('.')
                        }
                    }.getOrElse { e ->
                        Timber.w("Failed to decode sound theme file ${f.absolutePath}: ${e.message}")
                        return@decode null
                    }
                return@decode theme
            }?.toMutableList() ?: mutableListOf()
    }

    private fun getSound(name: String) = userSounds.find { it.name == name }

    private val userSounds: MutableList<SoundEffect> get() = listSounds()

    @JvmStatic
    fun switchSound(name: String) {
        getSound(name).let {
            if (it == null) {
                Timber.w("Unknown sound package name: $name")
                return
            }
            currentSoundEffect = it
            AppPrefs.defaultInstance().keyboard.customSoundPackage = name
        }
    }

    fun init() {
        // register listener
        DataManager.addOnChangedListener(onDataDirChange)
        currentSoundEffect = getSound(AppPrefs.defaultInstance().keyboard.customSoundPackage) ?: return
    }

    private lateinit var currentSoundEffect: SoundEffect

    fun getAllSoundEffects(): List<SoundEffect> = userSounds

    fun getActiveSoundEffect() = runCatching { currentSoundEffect }

    fun getActiveSoundFilePaths() =
        runCatching {
            currentSoundEffect.let { t -> t.sound.map { "${userDir.path}/${t.folder}/$it" } }
        }
}
