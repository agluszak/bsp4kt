package com.jetbrains.bsp

import com.jetbrains.bsp.messages.Message


fun interface MessageConsumer {
    /**
     * Consume a single message.
     *
     * @throws MessageIssueException when an issue is found that prevents further processing of the message
     * @throws JsonRpcException when accessing the JSON-RPC communication channel fails
     */
    fun consume(message: Message)
}
