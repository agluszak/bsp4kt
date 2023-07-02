package json

import com.jetbrains.bsp.JsonRpcException
import com.jetbrains.bsp.json.MessageJsonHandler
import com.jetbrains.bsp.json.StreamMessageProducer
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.SocketException
import java.nio.channels.ClosedChannelException
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger


class MessageProducerTest {
    private var executorService: ExecutorService? = null
    private var logLevel: Level? = null
    @Before
    fun setup() {
        executorService = Executors.newCachedThreadPool()
        val logger = Logger.getLogger(StreamMessageProducer::class.java.getName())
        logLevel = logger.level
        logger.level = Level.SEVERE
    }

    @After
    fun teardown() {
        executorService!!.shutdown()
        val logger = Logger.getLogger(StreamMessageProducer::class.java.getName())
        logger.level = logLevel
    }

    @Test
    @Throws(Exception::class)
    fun testStopOnInterrrupt() {
        executorService!!.submit {
            val input: InputStream = object : InputStream() {
                @Throws(IOException::class)
                override fun read(): Int {
                    throw InterruptedIOException()
                }
            }
            val jsonHandler = MessageJsonHandler(emptyMap())
            val messageProducer = StreamMessageProducer(input, jsonHandler)
            messageProducer.listen { message -> }
            messageProducer.close()
        }[TIMEOUT, TimeUnit.MILLISECONDS]
    }

    @Test
    @Throws(Exception::class)
    fun testStopOnChannelClosed() {
        executorService!!.submit {
            val input: InputStream = object : InputStream() {
                @Throws(IOException::class)
                override fun read(): Int {
                    throw ClosedChannelException()
                }
            }
            val jsonHandler = MessageJsonHandler(emptyMap())
            val messageProducer = StreamMessageProducer(input, jsonHandler)
            messageProducer.listen { message -> }
            messageProducer.close()
        }[TIMEOUT, TimeUnit.MILLISECONDS]
    }

    @Test
    @Throws(Throwable::class)
    fun testStopOnSocketClosed() {
        executorService!!.submit {
            val input: InputStream = object : InputStream() {
                @Throws(IOException::class)
                override fun read(): Int {
                    throw SocketException("Socket closed")
                }
            }
            val jsonHandler = MessageJsonHandler(emptyMap())
            val messageProducer = StreamMessageProducer(input, jsonHandler)
            messageProducer.listen { message -> }
            messageProducer.close()
        }[TIMEOUT, TimeUnit.MILLISECONDS]
    }

    @Test(expected = JsonRpcException::class)
    @Throws(Throwable::class)
    fun testIOException() {
        try {
            executorService!!.submit {
                val input: InputStream = object : InputStream() {
                    @Throws(IOException::class)
                    override fun read(): Int {
                        throw SocketException("Permission denied: connect")
                    }
                }
                val jsonHandler = MessageJsonHandler(emptyMap())
                val messageProducer = StreamMessageProducer(input, jsonHandler)
                messageProducer.listen { message -> }
                messageProducer.close()
            }[TIMEOUT, TimeUnit.MILLISECONDS]
        } catch (e: ExecutionException) {
            throw e.cause!!
        }
    }

    companion object {
        private const val TIMEOUT: Long = 2000
    }
}
