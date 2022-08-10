package com.osfans.trime.data.sound

import com.osfans.trime.data.DataManager
import java.io.File

object SoundManager {

    private fun listSounds(path: File): MutableList<String> {
        return path.listFiles { _, name -> name.endsWith("sound.yaml") }
            ?.map { f -> f.nameWithoutExtension }
            ?.toMutableList() ?: mutableListOf()
    }

    val sharedSounds: MutableList<String> = listSounds(File(DataManager.sharedDataDir, "sound"))

    val userSounds: MutableList<String> = listSounds(File(DataManager.userDataDir, "sound"))

    fun getAllSounds(): List<String> {
        if (DataManager.sharedDataDir.absolutePath == DataManager.userDataDir.absolutePath) {
            return userSounds
        }
        return sharedSounds + userSounds
    }
}
