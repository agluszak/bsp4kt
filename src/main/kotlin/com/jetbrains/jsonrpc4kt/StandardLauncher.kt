package com.jetbrains.jsonrpc4kt

import com.jetbrains.jsonrpc4kt.json.ConcurrentMessageProcessor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future


class StandardLauncher<Local : Any, Remote : Any>(
    private val execService: ExecutorService,
    override val remoteProxy: Remote,
    private val msgProcessor: ConcurrentMessageProcessor
) : Launcher<Local, Remote> {

    override fun startListening(): Future<Unit> {
        return msgProcessor.beginProcessing(execService)
    }
}