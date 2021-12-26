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

        // 备份 /data/data/APPLICATION_ID/shared_prefs/APPLICATION_ID_preferences.xml 到同步目录
        DataUtils.backupPref()
        // 从同步目录恢复 revocer.xml 到data目录，并且重命名recover.xml为recovered.xml
        DataUtils.recoverPref()
        // TODO 自动刷新同文的设置（目前需要手动结束进程才能生效

        Rime.sync_user_data().also {
            Rime.destroy()
            Rime.get(context, true)
        }
    }
}
