import com.jetbrains.jsonrpc4kt.json.MessageJsonHandler
import com.jetbrains.jsonrpc4kt.json.StreamMessageConsumer
import com.jetbrains.jsonrpc4kt.messages.Message
import com.jetbrains.jsonrpc4kt.messages.MessageId
import com.jetbrains.jsonrpc4kt.messages.RequestMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamMessageConsumerTest {
    @Test
    fun simpleTest() = runTest {
        val channel = Channel<Message>()
        val outputStream = ByteArrayOutputStream()
        val jsonHandler = MessageJsonHandler(Json, emptyMap())

        val consumer = StreamMessageConsumer(outputStream, jsonHandler, channel)

        consumer.start(this)

        channel.send(RequestMessage(MessageId.StringId("1"), "foobar", null))
        channel.close()

        val expected = Json.encodeToJsonElement(RequestMessage(MessageId.StringId("1"), "foobar", null) as Message)
        assertEquals(expected, outputStream.toString().removeContentLengthAndParse(44))
    }
}