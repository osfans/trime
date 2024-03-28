package com.osfans.trime.util

import java.io.File

inline fun <T> withTempDir(block: (File) -> T): T {
    val dir =
        appContext.cacheDir.resolve(System.currentTimeMillis().toString()).also {
            it.mkdirs()
        }
    try {
        return block(dir)
    } finally {
        dir.deleteRecursively()
    }
}
