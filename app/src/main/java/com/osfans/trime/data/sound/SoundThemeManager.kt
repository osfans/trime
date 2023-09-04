package com.osfans.trime.data.sound

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.DataDirectoryChangeListener
import com.osfans.trime.data.DataManager
import timber.log.Timber
import java.io.File

object SoundThemeManager : DataDirectoryChangeListener.Listener {

    var userDir = File(DataManager.userDataDir, "sound")

    init {
        // register listener
        DataDirectoryChangeListener.addDirectoryChangeListener(this)
    }

    /**
     * Update userDir.
     */
    override fun onDataDirectoryChange() {
        userDir = File(DataManager.userDataDir, "sound")
    }

    private val yaml = Yaml(
        configuration = YamlConfiguration(
            strictMode = false,
        ),
    )

    private fun listSounds(): MutableList<SoundTheme> {
        val files = userDir.listFiles { f -> f.name.endsWith("sound.yaml") }
        return files
            ?.mapNotNull decode@{ f ->
                val theme = runCatching {
                    yaml.decodeFromString(SoundTheme.serializer(), f.readText()).also {
                        it.name = f.name.substringBefore('.')
                    }
                }.getOrElse { e ->
                    Timber.w("Failed to decode sound theme file ${f.absolutePath}: ${e.message}")
                    return@decode null
                }
                return@decode theme
            }
            ?.toMutableList() ?: mutableListOf()
    }

    private fun getSound(name: String) = userSounds.find { it.name == name }

    val userSounds: MutableList<SoundTheme> = listSounds()

    @JvmStatic
    fun switchSound(name: String) {
        if (getSound(name) == null) {
            Timber.w("Unknown sound package name: $name")
            return
        }
        AppPrefs.defaultInstance().keyboard.customSoundPackage = name
        currentSoundTheme = getSound(name)!!
    }

    fun init() {
        currentSoundTheme = getSound(AppPrefs.defaultInstance().keyboard.customSoundPackage) ?: return
    }

    private lateinit var currentSoundTheme: SoundTheme

    fun getAllSoundThemes(): List<SoundTheme> = userSounds

    fun getActiveSoundTheme() = runCatching { currentSoundTheme }

    fun getActiveSoundFilePaths() = runCatching {
        currentSoundTheme.let { t -> t.sound.map { "${userDir.path}/${t.folder}/$it" } }
    }
}
