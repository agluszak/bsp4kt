package org.jetbrains.jsonrpc4kt

import kotlinx.serialization.SerializationException
import org.jetbrains.jsonrpc4kt.messages.ResponseError
import org.jetbrains.jsonrpc4kt.messages.ResponseErrorCode

sealed class MessageIssue(val message: String) {
    fun toErrorResponse(): ResponseError = when (this) {
        is NoSuchMethod -> ResponseError(ResponseErrorCode.MethodNotFound.code, message)
        is SerializationIssue -> ResponseError(ResponseErrorCode.ParseError.code, message)
        is WrongNumberOfParamsIssue -> ResponseError(ResponseErrorCode.InvalidParams.code, message)
    }
}

class NoSuchMethod(val methodName: String) : MessageIssue("Unsupported method: $methodName")
class SerializationIssue(cause: SerializationException) : MessageIssue("Error during serialization: ${cause.message}")
class WrongNumberOfParamsIssue(val method: String, val expected: Int, val actual: Int) :
    MessageIssue("Wrong number of parameters for method $method: expected $expected, got $actual")