package com.osfans.trime.core

sealed class RimeEvent(messageValue: String) {

    abstract val messageType: MessageType

    data class SchemaEvent(val messageValue: String) :
        RimeEvent(messageValue) {
        override val messageType: MessageType
            get() = MessageType.Schema
    }

    data class OptionEvent(val messageValue: String) :
        RimeEvent(messageValue) {
        override val messageType: MessageType
            get() = MessageType.Option
    }

    data class DeployEvent(val messageValue: String) :
        RimeEvent(messageValue) {
        override val messageType: MessageType
            get() = MessageType.Deploy
    }

    data class UnknownEvent(val messageValue: String) :
        RimeEvent(messageValue) {
        override val messageType: MessageType
            get() = MessageType.Unknown
    }

    enum class MessageType {
        Schema,
        Option,
        Deploy,
        Unknown
    }

    companion object RimeNotificationHandler {
        @JvmStatic
        fun create(type: String, value: String) =
            when (type) {
                "schema" -> SchemaEvent(value)
                "option" -> OptionEvent(value)
                "deploy" -> DeployEvent(value)
                else -> UnknownEvent(value)
            }
    }
}
