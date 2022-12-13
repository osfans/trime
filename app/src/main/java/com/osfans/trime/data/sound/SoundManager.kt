package com.osfans.trime.data.sound

import com.osfans.trime.core.Rime
import com.osfans.trime.data.AppPrefs
import com.osfans.trime.data.DataManager
import timber.log.Timber
import java.io.File
import java.io.FileFilter

object SoundManager {

    val userDir = File(Rime.getRimeUserDataDir(), "sound")

    private fun listSounds(path: File): MutableList<String> {
        return path.listFiles { _, name -> name.endsWith("sound.yaml") }
            ?.map { f -> f.nameWithoutExtension }
            ?.toMutableList() ?: mutableListOf()
    }

    private fun getSound(name: String) =
        userSounds.find { it == name } ?: sharedSounds.find { it == name }

    val sharedSounds: MutableList<String> = listSounds(File(DataManager.sharedDataDir, "sound"))

    val userSounds: MutableList<String> = listSounds(File(DataManager.userDataDir, "sound"))

    @JvmStatic
    fun switchSound(name: String) {
        if (getSound(name) == null) {
            Timber.w("Unknown sound package name: $name")
            return
        }
        currentSound = name
        AppPrefs.defaultInstance().keyboard.soundPackage = name
    }

    fun init() {
        userDir.listFiles(FileFilter { it.extension == "sound.yaml" })
            ?.forEach {
                it.inputStream().use { i ->
                    DataManager.buildDir.outputStream().use { o ->
                        i.copyTo(o)
                    }
                }
            }
        currentSound = AppPrefs.defaultInstance().keyboard.soundPackage
    }

    private lateinit var currentSound: String

    fun getAllSounds(): List<String> {
        if (DataManager.sharedDataDir.absolutePath == DataManager.userDataDir.absolutePath) {
            return userSounds
        }
        return sharedSounds + userSounds
    }

    fun getActiveSound() = currentSound
}
