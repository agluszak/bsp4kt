package com.jetbrains.jsonrpc4kt.messages

import kotlinx.serialization.Serializable

@Serializable
data class CancelParams(val id: MessageId)