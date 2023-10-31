package com.osfans.trime.core

sealed class RimeNotification {
    abstract val messageType: MessageType

    data class SchemaNotification(val messageValue: String) :
        RimeNotification() {
        override val messageType: MessageType
            get() = MessageType.Schema

        val schemaId get() = messageValue.substringBefore('/')
        val schemaName get() = messageValue.substringAfter('/')

        override fun toString() = "SchemaEvent(schemaId=$schemaId, schemaName=$schemaName"
    }

    data class OptionNotification(val messageValue: String) :
        RimeNotification() {
        override val messageType: MessageType
            get() = MessageType.Option

        val option = messageValue.substringAfter('!')
        val value = !messageValue.startsWith('!')

        override fun toString() = "OptionNotification(option=$option, value=$value)"
    }

    data class DeployNotification(val messageValue: String) :
        RimeNotification() {
        override val messageType: MessageType
            get() = MessageType.Deploy

        val state = messageValue

        override fun toString() = "DeployNotification(state=$state)"
    }

    data class UnknownNotification(val messageValue: String) :
        RimeNotification() {
        override val messageType: MessageType
            get() = MessageType.Unknown
    }

    enum class MessageType {
        Schema,
        Option,
        Deploy,
        Unknown,
    }

    companion object RimeNotificationHandler {
        @JvmStatic
        fun create(
            type: String,
            value: String,
        ) = when (type) {
            "schema" -> SchemaNotification(value)
            "option" -> OptionNotification(value)
            "deploy" -> DeployNotification(value)
            else -> UnknownNotification(value)
        }
    }
}
