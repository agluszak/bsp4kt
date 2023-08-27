import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.jsonrpc4kt.json.MessageJsonHandler
import org.jetbrains.jsonrpc4kt.json.StreamMessageConsumer
import org.jetbrains.jsonrpc4kt.json.StreamMessageProducer
import org.jetbrains.jsonrpc4kt.messages.Message
import org.jetbrains.jsonrpc4kt.messages.MessageId
import org.jetbrains.jsonrpc4kt.messages.RequestMessage
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

class ProducerConsumerPassthroughTest {
    val newlines = "\r\n\r\n"

    @Test
    fun test() = runTest {
        val channel = Channel<Message>()
        val inputStream =
            ByteArrayInputStream("""Content-Length: 49$newlines{"jsonrpc": "2.0", "method": "foobar", "id": "1"}""".toByteArray())
        val outputStream = ByteArrayOutputStream()
        val jsonHandler = MessageJsonHandler(Json, emptyMap())
        val producer = StreamMessageProducer(inputStream, jsonHandler, channel)
        val consumer = StreamMessageConsumer(outputStream, jsonHandler, channel)

        val producerJob = producer.start(this)
        val consumerJob = consumer.start(this)

        producerJob.join()
        consumerJob.join()

        val expectedJson = Json.encodeToJsonElement(RequestMessage(MessageId.StringId("1"), "foobar", null) as Message)

        assertEquals(expectedJson, outputStream.toString().removeContentLengthAndParse(44))

    }
}
