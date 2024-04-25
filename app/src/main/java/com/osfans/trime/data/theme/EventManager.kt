// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import android.view.KeyEvent
import com.osfans.trime.ime.keyboard.Event

object EventManager {
    private val eventCache = mutableMapOf<String, Event>()

    fun getEvent(eventId: String): Event {
        if (eventCache.containsKey(eventId)) {
            return eventCache[eventId]!!
        }
        val event = Event(eventId)
        // 空格的 label 需要根据方案动态显示，所以不加入缓存
        if (event.code != KeyEvent.KEYCODE_SPACE) eventCache[eventId] = event
        return event
    }

    fun refresh() = eventCache.clear()
}
