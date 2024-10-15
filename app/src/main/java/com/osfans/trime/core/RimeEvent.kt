/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core

sealed class RimeEvent<T>(
    open val data: T,
) : RimeCallback {
    abstract val eventType: EventType

    data class IpcResponseEvent(
        override val data: Data,
    ) : RimeEvent<IpcResponseEvent.Data>(data) {
        override val eventType = EventType.IpcResponse

        data class Data(
            val commit: RimeProto.Commit?,
            val context: RimeProto.Context?,
            val status: RimeProto.Status?,
        )
    }

    data class KeyEvent(
        override val data: Data,
    ) : RimeEvent<KeyEvent.Data>(data) {
        override val eventType = EventType.Key

        data class Data(
            val value: KeyValue,
            val modifiers: KeyModifiers,
        )
    }

    enum class EventType {
        IpcResponse,
        Key,
    }

    companion object {
        fun <T> create(
            type: EventType,
            data: T,
        ) = when (type) {
            EventType.IpcResponse -> {
                IpcResponseEvent(data as IpcResponseEvent.Data)
            }
            EventType.Key ->
                KeyEvent(data as KeyEvent.Data)
        }
    }
}
