package com.jetbrains.jsonrpc4kt


interface MessageProducer {
    /**
     * Listen to a message source and forward all messages to the given consumer. Typically this method
     * blocks until the message source is unable to deliver more messages.
     *
     * @throws JsonRpcException when accessing the JSON-RPC communication channel fails
     */
    fun listen(messageConsumer: MessageConsumer)
}
