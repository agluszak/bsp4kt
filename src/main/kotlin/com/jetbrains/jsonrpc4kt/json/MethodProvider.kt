package com.jetbrains.jsonrpc4kt.json

import com.jetbrains.jsonrpc4kt.messages.MessageId


fun interface MethodProvider {
    /**
     * Returns the method name for a given request id, or null if such request id is unknown.
     *
     * @return method name or `null`
     */
    fun resolveMethod(requestId: MessageId?): String?
}

