/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util

import android.content.res.ColorStateList
import android.os.Build
import android.view.Menu
import android.view.MenuItem
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import splitties.resources.drawable

fun MenuItem.setup(
    @DrawableRes icon: Int,
    @ColorInt iconTint: Int,
    showAsAction: Boolean,
    onClick: Function0<Any?>?,
): MenuItem {
    if (icon != 0) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (iconTint != 0) {
                iconTintList = ColorStateList.valueOf(iconTint)
            }
            setIcon(icon)
        } else {
            val drawable = appContext.drawable(icon)
            if (iconTint != 0) drawable?.setTint(iconTint)
            setIcon(drawable)
        }
    }
    if (showAsAction) {
        setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
    }
    if (onClick != null) {
        setOnMenuItemClickListener {
            // return false only when the actual callback returns false
            onClick.invoke() != false
        }
    }
    return this
}

fun Menu.item(
    @StringRes title: Int,
    @DrawableRes icon: Int = 0,
    @ColorInt iconTint: Int = 0,
    showAsAction: Boolean = false,
    onClick: Function0<Any?>? = null,
): MenuItem {
    val item = add(title).setup(icon, iconTint, showAsAction, onClick)
    return item
}

fun Menu.item(
    title: CharSequence,
    @DrawableRes icon: Int = 0,
    @ColorInt iconTint: Int = 0,
    showAsAction: Boolean = false,
    onClick: Function0<Any?>? = null,
): MenuItem {
    val item = add(title).setup(icon, iconTint, showAsAction, onClick)
    return item
}
