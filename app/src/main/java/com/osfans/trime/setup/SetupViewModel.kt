package com.osfans.trime.setup

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SetupViewModel : ViewModel() {
    val isAllDone = MutableLiveData(false)
}
