package com.jetbrains.bsp

import com.jetbrains.bsp.messages.ResponseError
import com.jetbrains.bsp.messages.ResponseErrorCode
import kotlinx.serialization.SerializationException

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