package com.osfans.trime.util

import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference

/**
 * This file has been taken from the Android LatinIME project. Following modifications were done by
 * TRIME to the original source code:
 * - Convert the code from Java to Kotlin
 * - Change package name to match the current project's one
 * - Change method name [getOwnerInstance] to [getOwnerInstanceOrNull]. Invoke the former can get
 *   non-null [ownerInstanceRef] while invoke the latter can get nullable [ownerInstanceRef].
 *
 * The original source code can be found at the following location:
 *  https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/refs/heads/master/java/src/com/android/inputmethod/latin/utils/LeakGuardHandlerHelper.java
 */
open class LeakGuardHandlerWrapper<T>(
    ownerInstance: T,
    looper: Looper?
) : Handler(looper!!) {
    private val ownerInstanceRef: WeakReference<T> = WeakReference(ownerInstance)

    @Suppress("unused")
    constructor(ownerInstance: T) : this(ownerInstance, Looper.myLooper())

    @Synchronized
    fun getOwnerInstance(): T {
        return ownerInstanceRef.get()!!
    }

    @Synchronized
    fun getOwnerInstanceOrNull(): T? {
        return ownerInstanceRef.get()
    }
}
