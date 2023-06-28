import arrow.core.left
import arrow.core.right
import com.jetbrains.bsp.Endpoint
import com.jetbrains.bsp.JsonRpcException
import com.jetbrains.bsp.MessageConsumer
import com.jetbrains.bsp.RemoteEndpoint
import com.jetbrains.bsp.messages.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.SocketException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.logging.Level

class RemoteEndpointTest {
    internal open class TestEndpoint : Endpoint {
        var notifications: MutableList<NotificationMessage> = ArrayList<NotificationMessage>()
        var requests: MutableMap<RequestMessage, CompletableFuture<Any?>> = LinkedHashMap()

        override fun notify(method: String, parameter: Any?) {
            notifications.add(NotificationMessage(method, parameter))
        }

        override fun request(method: String, parameter: Any?): CompletableFuture<Any?> {
            val completableFuture = CompletableFuture<Any?>()
            requests[RequestMessage("asd".right(), method, parameter)] = completableFuture
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
        val endp = TestEndpoint()
        val consumer = TestMessageConsumer()
        val endpoint = RemoteEndpoint(consumer, endp)
        endpoint.consume(NotificationMessage("foo", "myparam"))
        val notificationMessage: NotificationMessage = endp.notifications[0]
        assertEquals("foo", notificationMessage.method)
        assertEquals("myparam", notificationMessage.params)
        assertTrue(consumer.messages.isEmpty())
    }

    @Test
    fun testRequest1() {
        val endp = TestEndpoint()
        val consumer = TestMessageConsumer()
        val endpoint = RemoteEndpoint(consumer, endp)
        endpoint.consume(RequestMessage("1".right(), "foo", "myparam"))
        val (key, value) = endp.requests.entries.iterator().next()
        value.complete("success")
        assertEquals("foo", key.method)
        assertEquals("myparam", key.params)
        val responseMessage: ResponseMessage = consumer.messages[0] as ResponseMessage
        assertEquals("success", responseMessage.result)
        assertEquals("1".right(), responseMessage.id)
    }

    @Test
    fun testRequest2() {
        val endp = TestEndpoint()
        val consumer = TestMessageConsumer()
        val endpoint = RemoteEndpoint(consumer, endp)
        endpoint.consume(RequestMessage(1.left(), "foo", "myparam"))
        val (key, value) = endp.requests.entries.iterator().next()
        value.complete("success")
        assertEquals("foo", key.method)
        assertEquals("myparam", key.params)
        val responseMessage: ResponseMessage = consumer.messages[0] as ResponseMessage
        assertEquals("success", responseMessage.result)
        assertEquals(1.left(), responseMessage.id)
    }

    @Test
    fun testHandleRequestIssues() {
        val endp = TestEndpoint()
        val consumer = TestMessageConsumer()
        val endpoint = RemoteEndpoint(consumer, endp)
        endpoint.handle(RequestMessage("1".right(), "foo", "myparam"), listOf(MessageIssue("bar")))
        val responseMessage: ResponseMessage = consumer.messages[0] as ResponseMessage
        assertNotNull(responseMessage.error)
        assertEquals("bar", responseMessage.error!!.message)
    }

    @Test
    fun testCancellation() {
        val endp = TestEndpoint()
        val consumer = TestMessageConsumer()
        val endpoint = RemoteEndpoint(consumer, endp)
        endpoint.consume(RequestMessage("1".right(), "foo", "myparam"))
        val (_, value) = endp.requests.entries.iterator().next()
        value.cancel(true)
        val message: ResponseMessage = consumer.messages[0] as ResponseMessage
        assertNotNull(message)
        val error: ResponseError = message.error!!
        assertEquals(error.code, ResponseErrorCode.RequestCancelled.value)
        assertEquals(error.message, "The request (id: Either.Right(1), method: 'foo') has been cancelled")
    }

    @Test
    fun testExceptionInEndpoint() {
        val logMessages = LogMessageAccumulator()
        try {
            // Don't show the exception in the test execution log
            logMessages.registerTo(RemoteEndpoint::class.java)
            val endp: TestEndpoint = object : TestEndpoint() {
                override fun request(method: String, parameter: Any?): CompletableFuture<Any?> {
                    throw RuntimeException("BAAZ")
                }
            }
            val consumer = TestMessageConsumer()
            val endpoint = RemoteEndpoint(consumer, endp)
            endpoint.consume(RequestMessage("1".right(), "foo", "myparam"))
            val response: ResponseMessage = consumer.messages[0] as ResponseMessage
            val error = response.error!!
            assertEquals("Internal error.", error.message)
            assertEquals(ResponseErrorCode.InternalError.value, error.code)
            val exception = error.data as String
            assertTrue(exception.contains("java.lang.RuntimeException: BAAZ"))
        } finally {
            logMessages.unregister()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testExceptionInConsumer() {
        val endp = TestEndpoint()
        val consumer = MessageConsumer { message -> throw RuntimeException("BAAZ") }
        val endpoint = RemoteEndpoint(consumer, endp)
        val future: CompletableFuture<Any?> = endpoint.request("foo", "myparam")
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
        val endp = TestEndpoint()
        val consumer = TestMessageConsumer()
        val endpoint = RemoteEndpoint(consumer, endp)
        val future: CompletableFuture<Any?> = endpoint.request("foo", "myparam")
        val chained = future.thenAccept { _ ->
            throw RuntimeException(
                "BAAZ"
            )
        }
        endpoint.consume(ResponseMessage(1.left(), "Bar"))
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
        val logMessages = LogMessageAccumulator()
        try {
            logMessages.registerTo(RemoteEndpoint::class.java)
            val endp = TestEndpoint()
            val consumer: MessageConsumer =
                MessageConsumer { throw JsonRpcException(SocketException("Permission denied: connect")) }
            val endpoint = RemoteEndpoint(consumer, endp)
            endpoint.notify("foo", null)
            logMessages.await(Level.WARNING, "Failed to send notification message.")
        } finally {
            logMessages.unregister()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testOutputStreamClosed() {
        val logMessages = LogMessageAccumulator()
        try {
            logMessages.registerTo(RemoteEndpoint::class.java)
            val endp = TestEndpoint()
            val consumer: MessageConsumer = MessageConsumer { throw JsonRpcException(SocketException("Socket closed")) }
            val endpoint = RemoteEndpoint(consumer, endp)
            endpoint.notify("foo", null)
            logMessages.await(Level.INFO, "Failed to send notification message.")
        } finally {
            logMessages.unregister()
        }
    }


    // TODO: exception handler cannot return null

//    @Test
//    fun testExceptionHandlerMisbehaving1() {
//        val logMessages = LogMessageAccumulator()
//        try {
//            // Don't show the exception in the test execution log
//            logMessages.registerTo(RemoteEndpoint::class.java)
//            val endp: TestEndpoint = object : TestEndpoint() {
//                override fun request(method: String, parameter: Any?): CompletableFuture<Any?> {
//                    throw RuntimeException("BAAZ")
//                }
//            }
//            val consumer = TestMessageConsumer()
//            // Misbehaving exception handler that returns null
//            val endpoint = RemoteEndpoint(consumer, endp) { e -> null }
//            endpoint.consume(RequestMessage("1".right(), "foo", "myparam"))
//            assertEquals(1, consumer.messages.size, "Check some response received")
//            val response: ResponseMessage = consumer.messages[0] as ResponseMessage
//            assertEquals(ResponseErrorCode.InternalError.value, response.error!!.code)
//        } finally {
//            logMessages.unregister()
//        }
//    }
//
//    internal class TestMessageConsumer2 : MessageConsumer {
//        var sentException = false
//        var messages: MutableList<Message> = ArrayList<Message>()
//        override fun consume(message: Message) {
//            if (sentException) {
//                messages.add(message)
//            } else {
//                // throw an exception only for the first message
//                sentException = true
//                throw RuntimeException("Exception in consumer")
//            }
//        }
//    }


//    @Test
//    @Throws(Exception::class)
//    fun testExceptionHandlerMisbehaving2() {
//        val logMessages = LogMessageAccumulator()
//        try {
//            // Don't show the exception in the test execution log
//            logMessages.registerTo(RemoteEndpoint::class.java)
//            val endp: TestEndpoint = object : TestEndpoint() {
//                override fun request(method: String, parameter: Any?): CompletableFuture<Any?> {
//                    return CompletableFuture.supplyAsync { "baz" }
//                }
//            }
//            val consumer = TestMessageConsumer2()
//            // Misbehaving exception handler that returns null
//            val endpoint = RemoteEndpoint(consumer, endp) { e -> null }
//            endpoint.consume(RequestMessage("1".right(), "foo", "myparam"))
//            val timeout = System.currentTimeMillis() + TIMEOUT
//            while (consumer.messages.isEmpty()) {
//                Thread.sleep(20)
//                if (System.currentTimeMillis() > timeout) {
//                    fail("Timedout waiting for messages") as Any
//                }
//            }
//            assertEquals(1, consumer.messages.size, "Check some response received")
//            val response: ResponseMessage = consumer.messages[0] as ResponseMessage
//            assertNotNull(response.error, "Check response has error")
//            assertEquals(ResponseErrorCode.InternalError.value, response.error!!.code)
//        } finally {
//            logMessages.unregister()
//        }
//    }

    companion object {
        private const val TIMEOUT: Long = 2000
        fun <T> init(value: T, initializer: Consumer<T>): T {
            initializer.accept(value)
            return value
        }
    }
}