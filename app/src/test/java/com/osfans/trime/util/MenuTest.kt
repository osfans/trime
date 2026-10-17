// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.util

import android.view.Menu
import android.view.MenuItem
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Proxy

class MenuTest :
    StringSpec({
        "resource titles retain explicit action IDs" {
            val recording = RecordingMenu()
            recording.menu.item(0x7f020001, id = 0x7f010001)
            recording.added shouldBe listOf(Menu.NONE, 0x7f010001, Menu.NONE, 0x7f020001)
        }

        "text titles retain explicit action IDs" {
            val recording = RecordingMenu()
            recording.menu.item("action", id = 0x7f010002, showAsAction = true)
            recording.added shouldBe listOf(Menu.NONE, 0x7f010002, Menu.NONE, "action")
            recording.showAsAction shouldBe MenuItem.SHOW_AS_ACTION_IF_ROOM
        }

        "popup items preserve the optional ID contract" {
            val recording = RecordingMenu()
            recording.menu.item("popup")
            recording.added shouldBe listOf(Menu.NONE, Menu.NONE, Menu.NONE, "popup")
        }
    })

private class RecordingMenu {
    var added: List<Any?> = emptyList()
    var showAsAction: Int? = null

    private val item = Proxy.newProxyInstance(MenuItem::class.java.classLoader, arrayOf(MenuItem::class.java)) { _, method, args ->
        check(method.name == "setShowAsAction")
        showAsAction = args[0] as Int
        null
    } as MenuItem

    val menu = Proxy.newProxyInstance(Menu::class.java.classLoader, arrayOf(Menu::class.java)) { _, method, args ->
        check(method.name == "add")
        added = args.toList()
        item
    } as Menu
}
