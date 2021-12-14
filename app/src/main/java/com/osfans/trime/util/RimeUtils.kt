package com.osfans.trime.util

import android.content.Context
import com.blankj.utilcode.util.ToastUtils
import com.osfans.trime.R
import com.osfans.trime.Rime
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
    suspend fun deploy(context: Context) = withContext(Dispatchers.IO) {
        Rime.destroy()
        Rime.get(context, true)
        ToastUtils.showLong(R.string.deploy_finish)
    }

    /** Sync the user data.
     * @return `true` if successfully **/
    suspend fun sync(context: Context): Boolean = withContext(Dispatchers.IO) {
        Rime.sync_user_data().also {
            Rime.destroy()
            Rime.get(context, true)
        }
    }
}
