package com.jetbrains.bsp.messages

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class NotificationMessage(val method: String, val params: @Contextual Any?): Message()