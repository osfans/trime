package com.osfans.trime.data.theme

import com.osfans.trime.ime.keyboard.Event

object EventManager {
    private var eventCache = mutableMapOf<String, Event>()

    @JvmStatic
    fun getEvent(eventId: String): Event {
        if (eventCache.containsKey(eventId)) {
            return eventCache[eventId]!!
        }
        val event = Event(eventId)
        eventCache[eventId] = event
        return event
    }

    fun refresh() = eventCache.clear()
}
