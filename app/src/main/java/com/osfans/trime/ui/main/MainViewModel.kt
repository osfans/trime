// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.osfans.trime.core.RimeLifecycle
import com.osfans.trime.daemon.RimeDaemon
import com.osfans.trime.daemon.RimeSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MainUiState{
    LOADING,
    READY,
    ERR_DIRECTORY_MISSING
}
class MainViewModel : ViewModel() {
    private val _statusStateFlow = MutableStateFlow(RimeLifecycle.State.STOPPED)
    val statusStateFlow = _statusStateFlow.asStateFlow()

    val toolbarTitle = MutableLiveData<String>()

    val topOptionsMenu = MutableLiveData<Boolean>()

    val rime: RimeSession = RimeDaemon.createSession(javaClass.name)

    fun setToolbarTitle(title: String) {
        toolbarTitle.value = title
    }

    fun enableTopOptionsMenu() {
        topOptionsMenu.value = true
    }

    fun disableTopOptionsMenu() {
        topOptionsMenu.value = false
    }

    override fun onCleared() {
        RimeDaemon.destroySession(javaClass.name)
    }

    fun deploy() {
        _statusStateFlow.value = RimeLifecycle.State.STARTING
    }

    fun deployComplete() {
        _statusStateFlow.value = RimeLifecycle.State.READY
    }
}
