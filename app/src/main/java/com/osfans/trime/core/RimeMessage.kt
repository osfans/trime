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

    data class CommitTextMessage(
        override val data: RimeProto.Commit,
    ) : RimeMessage<RimeProto.Commit>(data) {
        override val messageType = MessageType.Commit
    }

    data class CompositionMessage(
        override val data: RimeProto.Context.Composition,
    ) : RimeMessage<RimeProto.Context.Composition>(data) {
        override val messageType = MessageType.Composition
    }

    data class CandidateMenuMessage(
        override val data: RimeProto.Context.Menu,
    ) : RimeMessage<RimeProto.Context.Menu>(data) {
        override val messageType = MessageType.Menu
    }

    data class StatusMessage(
        override val data: RimeProto.Status,
    ) : RimeMessage<RimeProto.Status>(data) {
        override val messageType = MessageType.Status
    }

    data class CandidateListMessage(
        override val data: Data,
    ) : RimeMessage<CandidateListMessage.Data>(data) {

        override val messageType = MessageType.Candidate

        data class Data(val total: Int = -1, val candidates: Array<CandidateItem> = emptyArray()) {

            override fun toString(): String = "total=$total, candidates=[${candidates.joinToString(limit = 5)}]"

            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Data

                if (total != other.total) return false
                if (!candidates.contentEquals(other.candidates)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = total
                result = 31 * result + candidates.contentHashCode()
                return result
            }
        }
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
        Commit,
        Composition,
        Menu,
        Status,
        Candidate,
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
            MessageType.Commit ->
                CommitTextMessage(params[0] as RimeProto.Commit)
            MessageType.Composition ->
                CompositionMessage(params[0] as RimeProto.Context.Composition)
            MessageType.Menu ->
                CandidateMenuMessage(params[0] as RimeProto.Context.Menu)
            MessageType.Status ->
                StatusMessage(params[0] as RimeProto.Status)
            MessageType.Candidate ->
                CandidateListMessage(
                    CandidateListMessage.Data(
                        params[0] as Int,
                        params[1] as Array<CandidateItem>,
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
