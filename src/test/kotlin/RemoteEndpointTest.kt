//import com.jetbrains.jsonrpc4kt.Endpoint
//import com.jetbrains.jsonrpc4kt.JsonRpcException
//import com.jetbrains.jsonrpc4kt.MessageConsumer
//import com.jetbrains.jsonrpc4kt.RemoteEndpoint
//import com.jetbrains.jsonrpc4kt.json.MessageJsonHandler
//import com.jetbrains.jsonrpc4kt.messages.*
//import kotlinx.serialization.json.Json
//import org.junit.jupiter.api.Assertions.*
//import org.junit.jupiter.api.Test
//import java.net.SocketException
//import java.util.concurrent.CompletableFuture
//import java.util.concurrent.ExecutionException
//import java.util.concurrent.TimeUnit
//import java.util.function.Consumer
//import java.util.logging.Level
//
//class RemoteEndpointTest {
//
//    val jsonHandler = MessageJsonHandler(Json.Default, emptyMap())
//    internal open class TestEndpoint : Endpoint {
//        var notifications: MutableList<NotificationMessage> = ArrayList<NotificationMessage>()
//        var requests: MutableMap<RequestMessage, CompletableFuture<Any?>> = LinkedHashMap()
//
//        override fun notify(method: String, params: List<Any?>) {
//            notifications.add(NotificationMessage(method, params))
//        }
//
//        override fun request(method: String, params: List<Any?>): CompletableFuture<Any?> {
//            val completableFuture = CompletableFuture<Any?>()
//            requests[RequestMessage(MessageId.StringId("asd"), method, params)] = completableFuture
//            return completableFuture
//        }
//    }
//
//    internal class TestMessageConsumer : MessageConsumer {
//        var messages: MutableList<Message> = ArrayList<Message>()
//        override fun consume(message: Message) {
//            messages.add(message)
//        }
//    }
//
//    @Test
//    fun testNotification() {
//        val endp = TestEndpoint()
//        val consumer = TestMessageConsumer()
//        val endpoint = RemoteEndpoint(consumer, endp)
//        endpoint.consume(NotificationMessage("foo", listOf("myparam")))
//        val notificationMessage: NotificationMessage = endp.notifications[0]
//        assertEquals("foo", notificationMessage.method)
//        assertEquals("myparam", notificationMessage.params[0])
//        assertTrue(consumer.messages.isEmpty())
//    }
//
//    @Test
//    fun testRequest1() {
//        val endp = TestEndpoint()
//        val consumer = TestMessageConsumer()
//        val endpoint = RemoteEndpoint(consumer, endp)
//        endpoint.consume(RequestMessage(MessageId.StringId("1"), "foo", listOf("myparam")))
//        val (key, value) = endp.requests.entries.iterator().next()
//        value.complete("success")
//        assertEquals("foo", key.method)
//        assertEquals("myparam", key.params[0])
//        val responseMessage: ResponseMessage = consumer.messages[0] as ResponseMessage
//        assertEquals("success", responseMessage.result)
//        assertEquals(MessageId.StringId("1"), responseMessage.id)
//    }
//
//    @Test
//    fun testRequest2() {
//        val endp = TestEndpoint()
//        val consumer = TestMessageConsumer()
//        val endpoint = RemoteEndpoint(consumer, endp)
//        endpoint.consume(RequestMessage(MessageId.NumberId(1), "foo", listOf("myparam")))
//        val (key, value) = endp.requests.entries.iterator().next()
//        value.complete("success")
//        assertEquals("foo", key.method)
//        assertEquals("myparam", key.params[0])
//        val responseMessage: ResponseMessage = consumer.messages[0] as ResponseMessage
//        assertEquals("success", responseMessage.result)
//        assertEquals(MessageId.NumberId(1), responseMessage.id)
//    }
//
//    @Test
//    fun testHandleRequestIssues() {
//        val endp = TestEndpoint()
//        val consumer = TestMessageConsumer()
//        val endpoint = RemoteEndpoint(consumer, endp)
//        endpoint.handle(RequestMessage(MessageId.StringId("1"), "foo", listOf("myparam")), listOf(MessageIssue("bar")))
//        val responseMessage: ResponseMessage = consumer.messages[0] as ResponseMessage
//        assertNotNull(responseMessage.error)
//        assertEquals("bar", responseMessage.error!!.message)
//    }
//
//    @Test
//    fun testCancellation() {
//        val endp = TestEndpoint()
//        val consumer = TestMessageConsumer()
//        val endpoint = RemoteEndpoint(consumer, endp)
//        endpoint.consume(RequestMessage(MessageId.StringId("1"), "foo", listOf("myparam")))
//        val (_, value) = endp.requests.entries.iterator().next()
//        value.cancel(true)
//        val message: ResponseMessage = consumer.messages[0] as ResponseMessage
//        assertNotNull(message)
//        val error: ResponseError = message.error!!
//        assertEquals(error.code, ResponseErrorCode.RequestCancelled.value)
//        assertEquals(error.message, "The request (id: Either.Right(1), method: 'foo') has been cancelled")
//    }
//
//    @Test
//    fun testExceptionInEndpoint() {
//        LogMessageAccumulator(RemoteEndpoint::class).use { logMessages ->
//            val endp: TestEndpoint = object : TestEndpoint() {
//                override fun request(method: String, params: List<Any?>): CompletableFuture<Any?> {
//                    throw RuntimeException("BAAZ")
//                }
//            }
//            val consumer = TestMessageConsumer()
//            val endpoint = RemoteEndpoint(consumer, endp)
//            endpoint.consume(RequestMessage(MessageId.StringId("1"), "foo", listOf("myparam")))
//            val response: ResponseMessage = consumer.messages[0] as ResponseMessage
//            val error = response.error!!
//            assertEquals("Internal error.", error.message)
//            assertEquals(ResponseErrorCode.InternalError.value, error.code)
//            val exception = error.data as String
//            assertTrue(exception.contains("java.lang.RuntimeException: BAAZ"))
//        }
//    }
//
//    @Test
//    @Throws(Exception::class)
//    fun testExceptionInConsumer() {
//        val endp = TestEndpoint()
//        val consumer = MessageConsumer { message -> throw RuntimeException("BAAZ") }
//        val endpoint = RemoteEndpoint(consumer, endp)
//        val future: CompletableFuture<Any?> = endpoint.request("foo", listOf("myparam"))
//        future.whenComplete { result: Any?, exception: Throwable ->
//            assertNull(result)
//            assertNotNull(exception)
//            assertEquals("BAAZ", exception.message)
//        }
//        try {
//            future[TIMEOUT, TimeUnit.MILLISECONDS]
//            fail("Expected an ExecutionException.") as Any
//        } catch (exception: ExecutionException) {
//            assertEquals("java.lang.RuntimeException: BAAZ", exception.message)
//        }
//    }
//
//    @Test
//    @Throws(Exception::class)
//    fun testExceptionInCompletableFuture() {
//        val endp = TestEndpoint()
//        val consumer = TestMessageConsumer()
//        val endpoint = RemoteEndpoint(consumer, endp)
//        val future: CompletableFuture<Any?> = endpoint.request("foo", listOf("myparam"))
//        val chained = future.thenAccept { _ ->
//            throw RuntimeException(
//                "BAAZ"
//            )
//        }
//        endpoint.consume(ResponseMessage(MessageId.NumberId(1), "Bar"))
//        try {
//            chained.get(TIMEOUT, TimeUnit.MILLISECONDS)
//            fail("Expected an ExecutionException.")
//        } catch (exception: ExecutionException) {
//            assertEquals("java.lang.RuntimeException: BAAZ", exception.message)
//        }
//    }
//
//    @Test
//    @Throws(Exception::class)
//    fun testExceptionInOutputStream() {
//        LogMessageAccumulator(RemoteEndpoint::class).use { logMessages ->
//            val endp = TestEndpoint()
//            val consumer: MessageConsumer =
//                MessageConsumer { throw JsonRpcException(SocketException("Permission denied: connect")) }
//            val endpoint = RemoteEndpoint(consumer, endp)
//            endpoint.notify("foo", listOf(null))
//            logMessages.await(Level.WARNING, "Failed to send notification message.")
//        }
//    }
//
//    @Test
//    @Throws(Exception::class)
//    fun testOutputStreamClosed() {
//        LogMessageAccumulator(RemoteEndpoint::class).use { logMessages ->
//            val endp = TestEndpoint()
//            val consumer: MessageConsumer = MessageConsumer { throw JsonRpcException(SocketException("Socket closed")) }
//            val endpoint = RemoteEndpoint(consumer, endp)
//            endpoint.notify("foo", listOf(null))
//            logMessages.await(Level.INFO, "Failed to send notification message.")
//        }
//    }
//
//
//    // TODO: exception handler cannot return null
//
////    @Test
////    fun testExceptionHandlerMisbehaving1() {
////        val logMessages = LogMessageAccumulator()
////        try {
////            // Don't show the exception in the test execution log
////            logMessages.registerTo(RemoteEndpoint::class.java)
////            val endp: TestEndpoint = object : TestEndpoint() {
////                override fun request(method: String, params: List<Any?>): CompletableFuture<Any?> {
////                    throw RuntimeException("BAAZ")
////                }
////            }
////            val consumer = TestMessageConsumer()
////            // Misbehaving exception handler that returns null
////            val endpoint = RemoteEndpoint(consumer, endp) { e -> null }
////            endpoint.consume(RequestMessage(MessageId.StringId("1"), "foo", "myparam"))
////            assertEquals(1, consumer.messages.size, "Check some response received")
////            val response: ResponseMessage = consumer.messages[0] as ResponseMessage
////            assertEquals(ResponseErrorCode.InternalError.value, response.error!!.code)
////        } finally {
////            logMessages.unregister()
////        }
////    }
////
////    internal class TestMessageConsumer2 : MessageConsumer {
////        var sentException = false
////        var messages: MutableList<Message> = ArrayList<Message>()
////        override fun consume(message: Message) {
////            if (sentException) {
////                messages.add(message)
////            } else {
////                // throw an exception only for the first message
////                sentException = true
////                throw RuntimeException("Exception in consumer")
////            }
////        }
////    }
//
//
////    @Test
////    @Throws(Exception::class)
////    fun testExceptionHandlerMisbehaving2() {
////        val logMessages = LogMessageAccumulator()
////        try {
////            // Don't show the exception in the test execution log
////            logMessages.registerTo(RemoteEndpoint::class.java)
////            val endp: TestEndpoint = object : TestEndpoint() {
////                override fun request(method: String, params: List<Any?>): CompletableFuture<Any?> {
////                    return CompletableFuture.supplyAsync { "baz" }
////                }
////            }
////            val consumer = TestMessageConsumer2()
////            // Misbehaving exception handler that returns null
////            val endpoint = RemoteEndpoint(consumer, endp) { e -> null }
////            endpoint.consume(RequestMessage(MessageId.StringId("1"), "foo", "myparam"))
////            val timeout = System.currentTimeMillis() + TIMEOUT
////            while (consumer.messages.isEmpty()) {
////                Thread.sleep(20)
////                if (System.currentTimeMillis() > timeout) {
////                    fail("Timedout waiting for messages") as Any
////                }
////            }
////            assertEquals(1, consumer.messages.size, "Check some response received")
////            val response: ResponseMessage = consumer.messages[0] as ResponseMessage
////            assertNotNull(response.error, "Check response has error")
////            assertEquals(ResponseErrorCode.InternalError.value, response.error!!.code)
////        } finally {
////            logMessages.unregister()
////        }
////    }
//
//    companion object {
//        private const val TIMEOUT: Long = 2000
//        fun <T> init(value: T, initializer: Consumer<T>): T {
//            initializer.accept(value)
//            return value
//        }
//    }
//}