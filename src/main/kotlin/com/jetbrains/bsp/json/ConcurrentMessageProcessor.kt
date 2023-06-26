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
class ConcurrentMessageProcessor(messageProducer: MessageProducer, messageConsumer: MessageConsumer) : Runnable {
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
    fun beginProcessing(executorService: ExecutorService): Future<Void> {
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

    protected fun processingStarted() {
        check(!isRunning) { "The message processor is already running." }
        isRunning = true
    }

    protected fun processingEnded() {
        isRunning = false
    }

    companion object {
        fun wrapFuture(result: Future<*>, messageProducer: MessageProducer): Future<Void> {
            return object : Future<Void> {
                @Throws(InterruptedException::class, ExecutionException::class)
                override fun get(): Void {
                    return result.get() as Void
                }

                @Throws(
                    InterruptedException::class, ExecutionException::class, TimeoutException::class
                )
                override fun get(timeout: Long, unit: TimeUnit): Void {
                    return result[timeout, unit] as Void
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

