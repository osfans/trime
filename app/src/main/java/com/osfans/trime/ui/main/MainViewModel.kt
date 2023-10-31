package com.osfans.trime.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    val toolbarTitle = MutableLiveData<String>()

    val topOptionsMenu = MutableLiveData<Boolean>()

    fun setToolbarTitle(title: String) {
        toolbarTitle.value = title
    }

    fun enableTopOptionsMenu() {
        topOptionsMenu.value = true
    }

    fun disableTopOptionsMenu() {
        topOptionsMenu.value = false
    }
}
