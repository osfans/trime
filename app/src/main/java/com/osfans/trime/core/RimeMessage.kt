/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
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
        override val data: CommitProto,
    ) : RimeMessage<CommitProto>(data) {
        override val messageType = MessageType.Commit
    }

    data class InlinePreeditMessage(
        override val data: String,
    ) : RimeMessage<String>(data) {
        override val messageType = MessageType.InlinePreedit
    }

    data class CompositionMessage(
        override val data: CompositionProto,
    ) : RimeMessage<CompositionProto>(data) {
        override val messageType = MessageType.Composition
    }

    data class PagedCandidatesMessage(
        override val data: Candidates.Paged,
    ) : RimeMessage<Candidates.Paged>(data) {
        override val messageType = MessageType.Menu
    }

    data class StatusMessage(
        override val data: StatusProto,
    ) : RimeMessage<StatusProto>(data) {
        override val messageType = MessageType.Status
    }

    data class BulkCandidatesMessage(
        override val data: Candidates.Bulk,
    ) : RimeMessage<Candidates.Bulk>(data) {
        override val messageType = MessageType.Candidate
    }

    data class KeyMessage(
        override val data: Data,
    ) : RimeMessage<KeyMessage.Data>(data) {
        override val messageType = MessageType.Key

        data class Data(
            val value: KeyValue,
            val modifiers: KeyModifiers,
            val isVirtual: Boolean,
        )
    }

    enum class MessageType {
        Unknown,
        Schema,
        Option,
        Deploy,
        Commit,
        InlinePreedit,
        Composition,
        Menu,
        Status,
        Candidate,
        Key,
    }

    companion object {
        private val types = MessageType.entries.toTypedArray()

        @Suppress("UNCHECKED_CAST")
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
                CommitTextMessage(params[0] as CommitProto)
            MessageType.InlinePreedit ->
                InlinePreeditMessage(params[0] as String)
            MessageType.Composition ->
                CompositionMessage(params[0] as CompositionProto)
            MessageType.Menu ->
                PagedCandidatesMessage(params[0] as Candidates.Paged)
            MessageType.Status ->
                StatusMessage(params[0] as StatusProto)
            MessageType.Candidate ->
                BulkCandidatesMessage(params[0] as Candidates.Bulk)
            MessageType.Key ->
                KeyMessage(
                    KeyMessage.Data(
                        KeyValue(params[0] as Int),
                        KeyModifiers.of(params[1] as Int),
                        params[2] as Boolean,
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
