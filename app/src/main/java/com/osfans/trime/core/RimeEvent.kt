package com.osfans.trime.core

sealed class RimeEvent {

    abstract val messageType: MessageType

    data class SchemaEvent(val messageValue: String) :
        RimeEvent() {
        override val messageType: MessageType
            get() = MessageType.Schema

        private val vararg = messageValue.split('/')

        override fun toString() = "SchemaEvent(schemaId=${vararg[0]}, schemaName=${vararg[1]}"
    }

    data class OptionEvent(val messageValue: String) :
        RimeEvent() {
        override val messageType: MessageType
            get() = MessageType.Option

        val option = messageValue.substringAfter('!')
        val value = !messageValue.startsWith('!')

        override fun toString() = "OptionEvent(option=$option, value=$value)"
    }

    data class DeployEvent(val messageValue: String) :
        RimeEvent() {
        override val messageType: MessageType
            get() = MessageType.Deploy

        override fun toString() = "DeployEvent(state=$messageValue)"
    }

    data class UnknownEvent(val messageValue: String) :
        RimeEvent() {
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
        fun create(type: String, value: String) =
            when (type) {
                "schema" -> SchemaEvent(value)
                "option" -> OptionEvent(value)
                "deploy" -> DeployEvent(value)
                else -> UnknownEvent(value)
            }
    }
}
