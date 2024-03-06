package com.osfans.trime.util

import android.view.KeyCharacterMap

inline val virtualKeyCharacterMap: KeyCharacterMap get() = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
