// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.window

/**
 * An empty interface marks that the instance and view of the window will be kept in the window manager,
 * not removing from the scope. This is useful when we want to initialize a window's view only once, e.g. for the keyboard window.
 * Usually, resident windows should be initialized and added to the inputComponent in [com.osfans.trime.ime.core.InputView].
 *
 * The idea of these window stuffs is adapted from Fcitx5 for Android.
 * See the [window management of Fcitx5 for Android](https://github.com/fcitx5-android/fcitx5-android/tree/7827e0d69dbb40dfe15d95ab774dbefcd6fa7bf9/app/src/main/java/org/fcitx/fcitx5/android/input/wm).
 */

interface ResidentWindow {
    interface Key

    val key: Key

    fun beforeAttached() {}
}
