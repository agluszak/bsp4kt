package com.jetbrains.bsp.json

import com.jetbrains.bsp.MessageConsumer
import com.jetbrains.bsp.MessageProducer
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.*
import java.util.logging.Level
import java.util.logging.Logger


/**
 * This class connects a message producer with a message consumer by listening for new messages in a dedicated thread.
 */
open class ConcurrentMessageProcessor(messageProducer: MessageProducer, messageConsumer: MessageConsumer) : Runnable {
    private var isRunning = false
    private val messageProducer: MessageProducer
    private val messageConsumer: MessageConsumer

    init {
        this.messageProducer = messageProducer
        this.messageConsumer = messageConsumer
    }

    /**
     * Start a thread that listens for messages in the message producer and forwards them to the message consumer.
     *
     * @param executorService - the thread is started using this service
     * @return a future that is resolved when the started thread is terminated, e.g. by closing a stream
     */
    fun beginProcessing(executorService: ExecutorService): Future<Unit> {
        val result = executorService.submit(this)
        return wrapFuture(result, messageProducer)
    }

    override fun run() {
        processingStarted()
        try {
            messageProducer.listen(messageConsumer)
        } catch (e: Exception) {
            LOG.log(Level.SEVERE, e.message, e)
        } finally {
            processingEnded()
        }
    }

    protected open fun processingStarted() {
        check(!isRunning) { "The message processor is already running." }
        isRunning = true
    }

    protected open fun processingEnded() {
        isRunning = false
    }

    companion object {
        fun wrapFuture(result: Future<*>, messageProducer: MessageProducer): Future<Unit> {
            return object : Future<Unit> {
                @Throws(InterruptedException::class, ExecutionException::class)
                override fun get(): Unit {
                    result.get()
                }

                @Throws(
                    InterruptedException::class, ExecutionException::class, TimeoutException::class
                )
                override fun get(timeout: Long, unit: TimeUnit): Unit {
                    result.get(timeout, unit)
                }

                override fun isDone(): Boolean {
                    return result.isDone
                }

                override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
                    if (mayInterruptIfRunning && messageProducer is Closeable) {
                        try {
                            (messageProducer as Closeable).close()
                        } catch (e: IOException) {
                            throw RuntimeException(e)
                        }
                    }
                    return result.cancel(mayInterruptIfRunning)
                }

                override fun isCancelled(): Boolean {
                    return result.isCancelled
                }
            }
        }

        private val LOG = Logger.getLogger(ConcurrentMessageProcessor::class.java.name)
    }
}

