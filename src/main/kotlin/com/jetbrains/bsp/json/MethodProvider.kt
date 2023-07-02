package com.jetbrains.bsp.json

import com.jetbrains.bsp.messages.MessageId


fun interface MethodProvider {
    /**
     * Returns the method name for a given request id, or null if such request id is unknown.
     *
     * @return method name or `null`
     */
    fun resolveMethod(requestId: MessageId?): String?
}

