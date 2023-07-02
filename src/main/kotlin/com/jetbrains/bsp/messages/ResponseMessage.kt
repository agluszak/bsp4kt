package com.jetbrains.bsp.messages

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Response message sent as a result of a request. If a request doesn't provide
 * a result value the receiver of a request still needs to return a response
 * message to conform to the JSON RPC specification. The result property of the
 * ResponseMessage should be set to null in this case to signal a successful
 * request. A response message is linked to a request via their `id` properties.
 */
// TODO: Use either
@Serializable
data class ResponseMessage(override val id: MessageId?, val result: @Contextual Any? = null, val error: ResponseError? = null) : IdentifiableMessage()

