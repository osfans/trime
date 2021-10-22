package com.osfans.trime.ime.lifecycle

import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.annotation.CallSuper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.CoroutineScope

open class LifecycleInputMethodService :
    InputMethodService(),
    LifecycleOwner {
    private val lifecycleRegistry by lazy { LifecycleRegistry(this) }

    val uiScope: CoroutineScope
        get() = lifecycle.coroutineScope

    final override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }

    @CallSuper
    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    @CallSuper
    override fun onCreateInputView(): View? {
        val decorView = window!!.window!!.decorView
        ViewTreeLifecycleOwner.set(decorView, this)
        return null
    }

    @CallSuper
    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
