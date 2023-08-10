import com.jetbrains.jsonrpc4kt.Endpoint
import com.jetbrains.jsonrpc4kt.JsonRpcException
import com.jetbrains.jsonrpc4kt.MessageConsumer
import com.jetbrains.jsonrpc4kt.RemoteEndpoint
import com.jetbrains.jsonrpc4kt.json.JsonRpcMethod
import com.jetbrains.jsonrpc4kt.json.MessageJsonHandler
import com.jetbrains.jsonrpc4kt.messages.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.SocketException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import kotlin.reflect.typeOf

class RemoteEndpointTest {

    val jsonHandler = MessageJsonHandler(
        Json.Default, mapOf(
            "request" to JsonRpcMethod.request("request", typeOf<String>(), typeOf<String>()),
            "notification" to JsonRpcMethod.notification("notification", typeOf<String>())
        )
    )

    internal open class TestEndpoint(val jsonHandler: MessageJsonHandler) : Endpoint {
        var notifications: MutableList<NotificationMessage> = ArrayList<NotificationMessage>()
        var requests: MutableMap<RequestMessage, CompletableFuture<Any?>> = LinkedHashMap()

        override fun notify(method: String, params: List<Any?>) {
            val serializedParams = jsonHandler.serializeParams(method, params)
            notifications.add(NotificationMessage(method, serializedParams))
        }

        override fun request(method: String, params: List<Any?>): CompletableFuture<Any?> {
            val completableFuture = CompletableFuture<Any?>()
            val serializedParams = jsonHandler.serializeParams(method, params)
            requests[RequestMessage(MessageId.StringId("asd"), method, serializedParams)] = completableFuture
            return completableFuture
        }
    }

    internal class TestMessageConsumer : MessageConsumer {
        var messages: MutableList<Message> = ArrayList<Message>()
        override fun consume(message: Message) {
            messages.add(message)
        }
    }

    @Test
    fun testNotification() {
        val endp = TestEndpoint(jsonHandler)
        val consumer = TestMessageConsumer()
        val endpoint = RemoteEndpoint(consumer, endp, jsonHandler)
        endpoint.consume(NotificationMessage("notification", JsonParams.array(JsonPrimitive("myparam"))))
        val notificationMessage: NotificationMessage = endp.notifications[0]
        assertEquals("notification", notificationMessage.method)
        assertEquals(JsonParams.array(JsonPrimitive("myparam")), notificationMessage.params)
        assertTrue(consumer.messages.isEmpty())
    }

    @Test
    fun testRequest1() {
        val endp = TestEndpoint(jsonHandler)
        val consumer = TestMessageConsumer()
        val endpoint = RemoteEndpoint(consumer, endp, jsonHandler)
        endpoint.consume(RequestMessage(MessageId.StringId("1"), "request", JsonParams.array(JsonPrimitive("myparam"))))
        val (key, value) = endp.requests.entries.iterator().next()
        value.complete("success")
        assertEquals("request", key.method)
        assertEquals(JsonParams.array(JsonPrimitive("myparam")), key.params)
        val responseMessage = consumer.messages[0] as ResponseMessage.Result
        assertEquals(JsonPrimitive("success"), responseMessage.result)
        assertEquals(MessageId.StringId("1"), responseMessage.id)
    }

    @Test
    fun testRequest2() {
        val endp = TestEndpoint(jsonHandler)
        val consumer = TestMessageConsumer()
        val endpoint = RemoteEndpoint(consumer, endp, jsonHandler)
        endpoint.consume(RequestMessage(MessageId.NumberId(1), "request", JsonParams.array(JsonPrimitive("myparam"))))
        val (key, value) = endp.requests.entries.iterator().next()
        value.complete("success")
        assertEquals("request", key.method)
        assertEquals(JsonParams.array(JsonPrimitive("myparam")), key.params)
        val responseMessage = consumer.messages[0] as ResponseMessage.Result
        assertEquals(JsonPrimitive("success"), responseMessage.result)
        assertEquals(MessageId.NumberId(1), responseMessage.id)
    }

    @Test
    fun testCancellation() {
        val endp = TestEndpoint(jsonHandler)
        val consumer = TestMessageConsumer()
        val endpoint = RemoteEndpoint(consumer, endp, jsonHandler)
        endpoint.consume(RequestMessage(MessageId.StringId("1"), "request", JsonParams.array(JsonPrimitive("myparam"))))
        val (_, value) = endp.requests.entries.iterator().next()
        value.cancel(true)
        val message = consumer.messages[0] as ResponseMessage.Error
        val error = message.error
        assertEquals(error.code, ResponseErrorCode.RequestCancelled.code)
        assertEquals(error.message, "The request (id: \"1\", method: 'request') has been cancelled")
    }

    // TODO: test unknown method handling

    @Test
    fun testExceptionInEndpoint() {
        LogMessageAccumulator(RemoteEndpoint::class).use { logMessages ->
            val endp: TestEndpoint = object : TestEndpoint(jsonHandler) {
                override fun request(method: String, params: List<Any?>): CompletableFuture<Any?> {
                    throw RuntimeException("BAAZ")
                }
            }
            val consumer = TestMessageConsumer()
            val endpoint = RemoteEndpoint(consumer, endp, jsonHandler)
            endpoint.consume(
                RequestMessage(
                    MessageId.StringId("1"),
                    "request",
                    JsonParams.array(JsonPrimitive("myparam"))
                )
            )
            val response = consumer.messages[0] as ResponseMessage.Error
            val error = response.error
            assertEquals("Internal error.", error.message)
            assertEquals(ResponseErrorCode.InternalError.code, error.code)
            val exception = error.data
            assertTrue(exception.toString().contains("java.lang.RuntimeException: BAAZ"))
        }
    }

    @Test
    @Throws(Exception::class)
    fun testExceptionInConsumer() {
        val endp = TestEndpoint(jsonHandler)
        val consumer = MessageConsumer { message -> throw RuntimeException("BAAZ") }
        val endpoint = RemoteEndpoint(consumer, endp, jsonHandler)
        val future: CompletableFuture<Any?> = endpoint.request("request", listOf("myparam"))
        future.whenComplete { result: Any?, exception: Throwable ->
            assertNull(result)
            assertNotNull(exception)
            assertEquals("BAAZ", exception.message)
        }
        try {
            future[TIMEOUT, TimeUnit.MILLISECONDS]
            fail("Expected an ExecutionException.") as Any
        } catch (exception: ExecutionException) {
            assertEquals("java.lang.RuntimeException: BAAZ", exception.message)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testExceptionInCompletableFuture() {
        val endp = TestEndpoint(jsonHandler)
        val consumer = TestMessageConsumer()
        val endpoint = RemoteEndpoint(consumer, endp, jsonHandler)
        val future: CompletableFuture<Any?> = endpoint.request("request", listOf("myparam"))
        val chained = future.thenAccept { _ ->
            throw RuntimeException("BAAZ")
        }
        endpoint.consume(ResponseMessage.Result(MessageId.NumberId(1), JsonPrimitive("success")))
        try {
            chained.get(TIMEOUT, TimeUnit.MILLISECONDS)
            fail("Expected an ExecutionException.")
        } catch (exception: ExecutionException) {
            assertEquals("java.lang.RuntimeException: BAAZ", exception.message)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testExceptionInOutputStream() {
        LogMessageAccumulator(RemoteEndpoint::class).use { logMessages ->
            val endp = TestEndpoint(jsonHandler)
            val consumer: MessageConsumer =
                MessageConsumer { throw JsonRpcException(SocketException("Permission denied: connect")) }
            val endpoint = RemoteEndpoint(consumer, endp, jsonHandler)
            endpoint.notify("foo", listOf(null))
            logMessages.await(Level.WARNING, "Error while processing the message")
        }
    }

    @Test
    @Throws(Exception::class)
    fun testOutputStreamClosed() {
        LogMessageAccumulator(RemoteEndpoint::class).use { logMessages ->
            val endp = TestEndpoint(jsonHandler)
            val consumer: MessageConsumer = MessageConsumer { throw JsonRpcException(SocketException("Socket closed")) }
            val endpoint = RemoteEndpoint(consumer, endp, jsonHandler)
            endpoint.notify("foo", listOf(null))
            logMessages.await(Level.WARNING, "Error while processing the message")
        }
    }

    companion object {
        private const val TIMEOUT: Long = 2000
    }
}