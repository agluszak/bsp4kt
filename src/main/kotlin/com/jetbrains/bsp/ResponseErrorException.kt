package com.jetbrains.bsp

import com.jetbrains.bsp.messages.ResponseError

/**
 * An exception thrown in order to send a response with an attached `error` object.
 */
class ResponseErrorException(val responseError: ResponseError) : RuntimeException() {

    override val message: String
        get() = responseError.message
}

