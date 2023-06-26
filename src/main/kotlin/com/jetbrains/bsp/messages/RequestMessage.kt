package com.jetbrains.bsp.messages

data class RequestMessage(override val id: MessageId, val method: String, val params: Any?): IdentifiableMessage()