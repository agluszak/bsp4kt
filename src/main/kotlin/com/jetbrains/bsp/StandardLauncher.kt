package com.jetbrains.bsp

import com.jetbrains.bsp.json.ConcurrentMessageProcessor
import com.jetbrains.bsp.json.StreamMessageProducer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future


class StandardLauncher<Local: Any, Remote: Any>(
    private val execService: ExecutorService,
    override val remoteProxy: Remote,
    private val msgProcessor: ConcurrentMessageProcessor
) : Launcher<Local, Remote> {

    override fun startListening(): Future<Unit> {
        return msgProcessor.beginProcessing(execService)
    }
}