package com.osfans.trime.util

import com.blankj.utilcode.util.ToastUtils
import com.osfans.trime.R
import com.osfans.trime.core.Rime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

/**
 * Wrapper of often-used native methods in [Rime].
 **/
object RimeUtils {
    /** Check Rime backend health. **/
    fun check() {
        Rime.check(true)
        exitProcess(0) // Clear the memory
    }

    /** Deploy means reset Rime instance. **/
    suspend fun deploy() = withContext(Dispatchers.IO) {
        Rime.destroy()
        Rime.get(true)
        ToastUtils.showLong(R.string.deploy_finish)
    }

    /** Sync the user data.
     * @return `true` if successfully **/
    suspend fun sync(): Boolean = withContext(Dispatchers.IO) {
        Rime.sync_user_data().also {
            Rime.destroy()
            Rime.get(true)
        }
    }
}
