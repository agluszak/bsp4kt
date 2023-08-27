import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.jetbrains.jsonrpc4kt.json.MessageJsonHandler
import org.jetbrains.jsonrpc4kt.json.StreamMessageProducer
import org.jetbrains.jsonrpc4kt.messages.Message
import org.jetbrains.jsonrpc4kt.messages.MessageId
import org.jetbrains.jsonrpc4kt.messages.RequestMessage
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(DelicateCoroutinesApi::class)
class StreamMessageProducerTest {

    val newlines = "\r\n\r\n"

    @Test
    fun simpleTest() = runTest {
        val channel = Channel<Message>()

        val pipedInputStream = PipedInputStream()
        val pipedOutputStream = PipedOutputStream()
        pipedInputStream.connect(pipedOutputStream)

        val jsonHandler = MessageJsonHandler(Json.Default, emptyMap())

        val producer = StreamMessageProducer(pipedInputStream, jsonHandler, channel)
        val producerJob = producer.start(this)

        launch {
            pipedOutputStream.write("""Content-Length: 49$newlines{"jsonrpc": "2.0", "method": "foobar", "id": "1"}""".toByteArray())
            pipedOutputStream.close()
        }

        val message = channel.receive()
        assertEquals(RequestMessage(MessageId.StringId("1"), "foobar", null), message)

        producerJob.join()
        assertTrue { channel.isClosedForReceive }
    }

    @Test
    fun cancelBeforeFullSend() = runTest {
        val channel = Channel<Message>()

        val pipedInputStream = PipedInputStream()
        val pipedOutputStream = PipedOutputStream()
        pipedInputStream.connect(pipedOutputStream)

        val jsonHandler = MessageJsonHandler(Json.Default, emptyMap())

        val producer = StreamMessageProducer(pipedInputStream, jsonHandler, channel)
        val producerJob = producer.start(this)

        launch {
            pipedOutputStream.write("""Content-Length: 49$newlines{"jsonrpc": "2.0", "method": "foobar", """.toByteArray())
            pipedOutputStream.close()
        }

        assertFailsWith<ClosedReceiveChannelException> { channel.receive() }

        producerJob.join()
    }

//    @Test
//    fun closingChannelCancelsProducersJob() = runTest {
//        val channel = Channel<Message>()
//
//        val pipedInputStream = PipedInputStream()
//        val pipedOutputStream = PipedOutputStream()
//        pipedInputStream.connect(pipedOutputStream)
//
//        val jsonHandler = MessageJsonHandler(Json.Default, emptyMap())
//
//        val producer = StreamMessageProducer(pipedInputStream, jsonHandler, channel)
//        val producerJob = producer.start(this)
//
//        channel.close()
//        producerJob.join()
//    }

    @Test
    fun cancellingProducersJobWorks() = runTest {
        val channel = Channel<Message>()

        val pipedInputStream = PipedInputStream()
        val pipedOutputStream = PipedOutputStream()
        pipedInputStream.connect(pipedOutputStream)

        val jsonHandler = MessageJsonHandler(Json.Default, emptyMap())

        val producer = StreamMessageProducer(pipedInputStream, jsonHandler, channel)
        val producerJob = producer.start(this)

        producerJob.cancel()
        producerJob.join()
    }

    @Test
    fun cancellingProducersJobWorksInFlight() = runTest {
        val channel = Channel<Message>()

        val pipedInputStream = PipedInputStream()
        val pipedOutputStream = PipedOutputStream()
        pipedInputStream.connect(pipedOutputStream)

        val jsonHandler = MessageJsonHandler(Json.Default, emptyMap())

        val producer = StreamMessageProducer(pipedInputStream, jsonHandler, channel)
        val producerJob = producer.start(this)

        launch {
            pipedOutputStream.write("""Content-Length: 49$newlines{"jsonrpc": "2.0", "method": "foobar", """.toByteArray())
        }

        producerJob.cancel()
        producerJob.join()
    }
}