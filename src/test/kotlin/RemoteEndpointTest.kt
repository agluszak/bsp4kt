import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.jsonrpc4kt.RemoteEndpoint
import org.jetbrains.jsonrpc4kt.json.JsonRpcMethod
import org.jetbrains.jsonrpc4kt.json.MessageJsonHandler
import org.jetbrains.jsonrpc4kt.messages.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.typeOf

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteEndpointTest {

    val jsonHandler = MessageJsonHandler(
        Json.Default, mapOf(
            "request" to JsonRpcMethod.request("request", typeOf<String>(), typeOf<String>()),
            "notification" to JsonRpcMethod.notification("notification", typeOf<String>())
        )
    )

    internal open class TestEndpoint(val jsonHandler: MessageJsonHandler) : org.jetbrains.jsonrpc4kt.Endpoint {
        val notifications: Channel<NotificationMessage> = Channel()
        val requests: Channel<Pair<RequestMessage, CompletableDeferred<Any?>>> = Channel()

        override fun notify(method: String, params: List<Any?>) {
            CoroutineScope(EmptyCoroutineContext).launch {
                val serializedParams = jsonHandler.serializeParams(method, params)
                notifications.send(NotificationMessage(method, serializedParams))
            }
        }

        override suspend fun request(method: String, params: List<Any?>): Any? {
            val completableDeferred = CompletableDeferred<Any?>(
                currentCoroutineContext().job
            )
            val serializedParams = jsonHandler.serializeParams(method, params)
            requests.send(RequestMessage(MessageId.StringId("asd"), method, serializedParams) to completableDeferred)

            return completableDeferred.await()
        }
    }

    @Test
    fun testNotification() = runTest {
        val endp = TestEndpoint(jsonHandler)
        val inputChannel = Channel<Message>()
        val outputChannel = Channel<Message>()
        val endpoint = RemoteEndpoint(inputChannel, outputChannel, endp, jsonHandler, this)

        endpoint.start(this)

        inputChannel.send(NotificationMessage("notification", JsonParams.array(JsonPrimitive("myparam"))))

        val notificationMessage: NotificationMessage = endp.notifications.receive()
        assertEquals("notification", notificationMessage.method)
        assertEquals(JsonParams.array(JsonPrimitive("myparam")), notificationMessage.params)
        assertTrue(outputChannel.isEmpty)

        inputChannel.close()
    }

    @Test
    fun testRequest1() = runTest {
        val endp = TestEndpoint(jsonHandler)
        val inputChannel = Channel<Message>()
        val outputChannel = Channel<Message>()
        val endpoint = RemoteEndpoint(inputChannel, outputChannel, endp, jsonHandler, this)

        endpoint.start(this)

        inputChannel.send(
            RequestMessage(
                MessageId.StringId("1"),
                "request",
                JsonParams.array(JsonPrimitive("myparam"))
            )
        )

        val (key, value) = endp.requests.receive()
        value.complete("success")
        assertEquals("request", key.method)
        assertEquals(JsonParams.array(JsonPrimitive("myparam")), key.params)

        val responseMessage = outputChannel.receive() as ResponseMessage.Result
        assertEquals(JsonPrimitive("success"), responseMessage.result)
        assertEquals(MessageId.StringId("1"), responseMessage.id)

        inputChannel.close()

        delay(1000)

        println(this.coroutineContext.job.children.toList())
    }

    @Test
    fun testRequest2() = runTest {
        val endp = TestEndpoint(jsonHandler)
        val inputChannel = Channel<Message>()
        val outputChannel = Channel<Message>()
        val endpoint = RemoteEndpoint(inputChannel, outputChannel, endp, jsonHandler, this)

        endpoint.start(this)

        inputChannel.send(RequestMessage(MessageId.NumberId(1), "request", JsonParams.array(JsonPrimitive("myparam"))))

        val (key, value) = endp.requests.receive()
        value.complete("success")
        assertEquals("request", key.method)
        assertEquals(JsonParams.array(JsonPrimitive("myparam")), key.params)

        val responseMessage = outputChannel.receive() as ResponseMessage.Result
        assertEquals(JsonPrimitive("success"), responseMessage.result)
        assertEquals(MessageId.NumberId(1), responseMessage.id)

        inputChannel.close()
    }

    @Test
    fun testCancellation() = runTest {
        val endp = TestEndpoint(jsonHandler)
        val inputChannel = Channel<Message>()
        val outputChannel = Channel<Message>()
        val endpoint = RemoteEndpoint(inputChannel, outputChannel, endp, jsonHandler, this)

        endpoint.start(this)
        inputChannel.send(
            RequestMessage(
                MessageId.StringId("1"),
                "request",
                JsonParams.array(JsonPrimitive("myparam"))
            )
        )

        val (_, value) = endp.requests.receive()
        value.cancel()
        val message = outputChannel.receive() as ResponseMessage.Error
        val error = message.error
        assertEquals(error.code, ResponseErrorCode.RequestCancelled.code)
        assertEquals(error.message, "The request (id: \"1\", method: 'request') has been cancelled")

        inputChannel.close()
    }

    // TODO: test unknown method handling

    @Test
    fun testExceptionInEndpoint() = runTest {
        LogMessageAccumulator(RemoteEndpoint::class).use { logMessages ->
            val endp: TestEndpoint = object : TestEndpoint(jsonHandler) {
                override suspend fun request(method: String, params: List<Any?>): Any? {
                    throw RuntimeException("BAAZ")
                }
            }
            val inputChannel = Channel<Message>()
            val outputChannel = Channel<Message>()
            val endpoint = RemoteEndpoint(inputChannel, outputChannel, endp, jsonHandler, this)

            endpoint.start(this)
            inputChannel.send(
                RequestMessage(
                    MessageId.StringId("1"),
                    "request",
                    JsonParams.array(JsonPrimitive("myparam"))
                )
            )
            println("TEST request sent ${currentCoroutineContext().job}")
            val response = outputChannel.receive() as ResponseMessage.Error
            println("TEST response received")
            val error = response.error
            assertEquals("Internal error.", error.message)
            assertEquals(ResponseErrorCode.InternalError.code, error.code)
            val exception = error.data
            assertTrue(exception.toString().contains("java.lang.RuntimeException: BAAZ"))

            inputChannel.close()

            println("co by tu jeszcze zamknąć")
            outputChannel.close()
        }
    }

//    @Test
//    fun testExceptionInConsumer() = runTest {
//        val endp = TestEndpoint(jsonHandler)
//        val consumer = MessageConsumer { _ -> throw RuntimeException("BAAZ") }
//        val endpoint = RemoteEndpoint(consumer, endp, jsonHandler)
//        assertFailsWith<RuntimeException>("BAAZ") {
//            endpoint.request("request", listOf("myparam"))
//        }
//    }
//
//    @Test
//    fun testExceptionInOutputStream() {
//        LogMessageAccumulator(RemoteEndpoint::class).use { logMessages ->
//            val endp = TestEndpoint(jsonHandler)
//            val consumer: MessageConsumer =
//                MessageConsumer { throw JsonRpcException(SocketException("Permission denied: connect")) }
//            val endpoint = RemoteEndpoint(consumer, endp, jsonHandler)
//            endpoint.notify("foo", listOf(null))
//            logMessages.await(Level.WARNING, "Error while processing the message")
//        }
//    }
//
//    @Test
//    fun testOutputStreamClosed() {
//        LogMessageAccumulator(RemoteEndpoint::class).use { logMessages ->
//            val endp = TestEndpoint(jsonHandler)
//            val consumer: MessageConsumer = MessageConsumer { throw JsonRpcException(SocketException("Socket closed")) }
//            val endpoint = RemoteEndpoint(consumer, endp, jsonHandler)
//            endpoint.notify("foo", listOf(null))
//            logMessages.await(Level.WARNING, "Error while processing the message")
//        }
//    }
}