package com.jetbrains.bsp

import com.jetbrains.bsp.json.ConcurrentMessageProcessor
import com.jetbrains.bsp.json.StreamMessageProducer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future


class StandardLauncher<Local: Any, Remote: Any>(
    private val execService: ExecutorService,
    override val remoteProxy: Remote,
    override val remoteEndpoint: RemoteEndpoint,
    msgProcessor: ConcurrentMessageProcessor
) : Launcher<Local, Remote> {
    private val msgProcessor: ConcurrentMessageProcessor

    constructor(
        reader: StreamMessageProducer, messageConsumer: MessageConsumer,
        execService: ExecutorService, remoteProxy: Remote, remoteEndpoint: RemoteEndpoint
    ) : this(
        execService, remoteProxy, remoteEndpoint,
        ConcurrentMessageProcessor(reader, messageConsumer)
    )

    init {
        this.msgProcessor = msgProcessor
    }

    override fun startListening(): Future<Unit> {
        return msgProcessor.beginProcessing(execService)
    }
}