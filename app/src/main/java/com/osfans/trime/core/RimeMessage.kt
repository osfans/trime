/*
 * SPDX-FileCopyrightText: 2015 - 2024 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core

sealed class RimeMessage<T>(
    open val data: T,
) {
    abstract val messageType: MessageType

    data class UnknownMessage(
        override val data: Array<Any>,
    ) : RimeMessage<Array<Any>>(data) {
        override val messageType = MessageType.Unknown

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as UnknownMessage

            if (!data.contentEquals(other.data)) return false

            return true
        }

        override fun hashCode(): Int = data.contentHashCode()
    }

    data class SchemaMessage(
        override val data: SchemaItem,
    ) : RimeMessage<SchemaItem>(data) {
        override val messageType: MessageType
            get() = MessageType.Schema

        override fun toString() = "SchemaMessage(id=${data.id}, name=${data.name})"
    }

    data class OptionMessage(
        override val data: Data,
    ) : RimeMessage<OptionMessage.Data>(data) {
        override val messageType: MessageType
            get() = MessageType.Option

        data class Data(
            val option: String,
            val value: Boolean,
        )

        override fun toString() = "OptionMessage(option=${data.option}, value=${data.value})"
    }

    data class DeployMessage(
        override val data: State,
    ) : RimeMessage<DeployMessage.State>(data) {
        override val messageType: MessageType
            get() = MessageType.Deploy

        enum class State {
            Start,
            Success,
            Failure,
        }

        override fun toString() = "DeployMessage(state=$data)"
    }

    data class ResponseMessage(
        override val data: Data,
    ) : RimeMessage<ResponseMessage.Data>(data) {
        override val messageType = MessageType.Response

        data class Data(
            val commit: RimeProto.Commit,
            val context: RimeProto.Context,
            val status: RimeProto.Status,
        )

        override fun toString(): String = "ResponseMessage(candidates=[${data.context.menu.candidates.joinToString(limit = 5) }], ...)"
    }

    data class KeyMessage(
        override val data: Data,
    ) : RimeMessage<KeyMessage.Data>(data) {
        override val messageType = MessageType.Key

        data class Data(
            val value: KeyValue,
            val modifiers: KeyModifiers,
        )
    }

    enum class MessageType {
        Unknown,
        Schema,
        Option,
        Deploy,
        Response,
        Key,
    }

    companion object {
        private val types = MessageType.entries.toTypedArray()

        fun nativeCreate(
            type: Int,
            params: Array<Any>,
        ) = when (types[type]) {
            MessageType.Schema -> {
                val (id, name) = (params[0] as String).split('/', limit = 2)
                SchemaMessage(SchemaItem(id, name))
            }
            MessageType.Option -> {
                val value = params[0] as String
                OptionMessage(
                    OptionMessage.Data(
                        value.substringAfter('!'),
                        !value.startsWith('!'),
                    ),
                )
            }
            MessageType.Deploy ->
                DeployMessage(
                    DeployMessage.State.valueOf((params[0] as String).replaceFirstChar { it.titlecase() }),
                )
            MessageType.Response ->
                ResponseMessage(
                    ResponseMessage.Data(
                        params[0] as RimeProto.Commit,
                        params[1] as RimeProto.Context,
                        params[2] as RimeProto.Status,
                    ),
                )
            MessageType.Key ->
                KeyMessage(
                    KeyMessage.Data(
                        KeyValue(params[0] as Int),
                        KeyModifiers.of(params[1] as Int),
                    ),
                )
            else -> UnknownMessage(params)
        }

        fun create(
            type: MessageType,
            params: Array<Any>,
        ) = nativeCreate(type.ordinal, params)
    }
}
