package com.jetbrains.bsp.messages

import kotlinx.serialization.Serializable

@Serializable
data class CancelParams(val id: MessageId)