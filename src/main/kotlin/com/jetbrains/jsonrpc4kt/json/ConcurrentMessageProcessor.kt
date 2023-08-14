package com.jetbrains.jsonrpc4kt.json

import com.jetbrains.jsonrpc4kt.MessageConsumer
import com.jetbrains.jsonrpc4kt.MessageProducer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.*
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.reflect.jvm.jvmName


/**
 * This class connects a message producer with a message consumer by listening for new messages in a dedicated thread.
 */
class ConcurrentMessageProcessor(private val messageProducer: MessageProducer, private val messageConsumer: MessageConsumer) {
    private var isRunning = false

    /**
     * Start a thread that listens for messages in the message producer and forwards them to the message consumer.
     *
     * @param executorService - the thread is started using this service
     * @return a future that is resolved when the started thread is terminated, e.g. by closing a stream
     */
    fun beginProcessing(coroutineScope: CoroutineScope): Job {
        check(!isRunning) { "The message processor is already running." }
        isRunning = true
        return coroutineScope.launch {
            try {
                messageProducer.listen(messageConsumer)
            } catch (e: Exception) {
                LOG.log(Level.SEVERE, e.message, e)
            }
        }
    }
    companion object {
        private val LOG = Logger.getLogger(ConcurrentMessageProcessor::class.jvmName)
    }
}

