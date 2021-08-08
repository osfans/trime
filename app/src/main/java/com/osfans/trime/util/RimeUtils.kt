package com.osfans.trime.util

import android.content.Context
import android.widget.Toast
import com.osfans.trime.R
import com.osfans.trime.Rime
import kotlin.system.exitProcess

/**
 *  This object is a collection of common methods
 *  related to Rime JNI.
 */
object RimeUtils {
    fun check() {
        Rime.check(true)
        exitProcess(0) // Clear the memory
    }

    fun deploy(context: Context) {
        Rime.destroy()
        Rime.get(context, true)
        Toast.makeText(context,context.getString(R.string.deploy_finish), Toast.LENGTH_LONG).show();
    }

    fun sync(context: Context) = Rime.syncUserData(context)
}