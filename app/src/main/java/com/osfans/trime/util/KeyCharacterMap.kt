// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import android.view.KeyCharacterMap

inline val virtualKeyCharacterMap: KeyCharacterMap get() = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
