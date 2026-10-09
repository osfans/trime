/*
 * SPDX-FileCopyrightText: 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core

import com.osfans.trime.TrimeApplication
import com.osfans.trime.util.appContext
import timber.log.Timber
import java.io.File

object NativePlugins {
    private val modulePrefix = Regex("^(?:librime-|rime-)")
    private var loaded: Array<String>? = null

    /** Loads external Rime modules once per process. */
    @Synchronized
    fun modules(): Array<String> {
        if (TrimeApplication.getInstance().isDirectBootMode) return emptyArray()
        loaded?.let { return it.copyOf() }
        System.loadLibrary("rime_jni")
        val modules = mutableListOf<String>()
        runCatching {
            val external = checkNotNull(appContext.getExternalFilesDir(null)) { "External app files unavailable" }
            val source = File(external, "rime-plugins")
            check(source.isDirectory || source.mkdirs()) { "Cannot create $source" }
            val cache = File(appContext.noBackupFilesDir, "rime-plugins")
            check(cache.deleteRecursively() && cache.mkdirs()) { "Cannot prepare $cache" }
            for (library in source.listFiles().orEmpty().sortedBy { it.name }) {
                if (!library.isFile || library.extension != "so") continue
                val module = library.nameWithoutExtension.replaceFirst(modulePrefix, "").replace('-', '_')
                if (module in modules) continue
                runCatching {
                    val local = File(cache, library.name)
                    library.inputStream().use { input ->
                        local.outputStream().use { output ->
                            // Android 14+ requires opening the file before marking it read-only.
                            check(local.setReadOnly()) { "Cannot make $local read-only" }
                            input.copyTo(output)
                        }
                    }
                    val error = Rime.loadRimePlugin(local.absolutePath, module)
                    check(error.isEmpty()) { error }
                    modules.add(module)
                    Timber.i("Loaded Rime plugin %s", library.name)
                }.onFailure { Timber.e(it, "Cannot load Rime plugin %s", library.name) }
            }
        }.onFailure { Timber.e(it, "Cannot prepare Rime plugins") }
        loaded = modules.toTypedArray()
        return checkNotNull(loaded).copyOf()
    }
}
