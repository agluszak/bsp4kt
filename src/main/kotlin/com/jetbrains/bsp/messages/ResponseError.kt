package com.jetbrains.bsp.messages

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class ResponseError(val code: Int, val message: String, val data: @Contextual Any?)