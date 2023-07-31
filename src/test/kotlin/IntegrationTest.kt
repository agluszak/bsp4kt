import com.jetbrains.bsp.CompletableFutures
import com.jetbrains.bsp.Launcher
import com.jetbrains.bsp.RemoteEndpoint
import com.jetbrains.bsp.ResponseErrorException
import com.jetbrains.bsp.json.StreamMessageProducer
import com.jetbrains.bsp.messages.Message.Companion.CONTENT_LENGTH_HEADER
import com.jetbrains.bsp.messages.Message.Companion.CRLF
import com.jetbrains.bsp.services.GenericEndpoint
import com.jetbrains.bsp.services.JsonNotification
import com.jetbrains.bsp.services.JsonRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.*
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.reflect.jvm.jvmName
import kotlin.test.Ignore

class IntegrationTest {
    @Serializable
    class MyParam(val value: String?)

    interface MyServer {
        @JsonRequest
        fun askServer(param: MyParam): CompletableFuture<MyParam>
    }

    interface MyVoidServer {
        @JsonRequest
        fun askServer(param: MyParam): CompletableFuture<Unit>
    }

    class MyServerImpl : MyServer {
        override fun askServer(param: MyParam): CompletableFuture<MyParam> {
            return CompletableFuture.completedFuture(param)
        }
    }

    interface MyClient {
        @JsonRequest
        fun askClient(param: MyParam): CompletableFuture<MyParam>
    }

    class MyClientImpl() : MyClient {
        override fun askClient(param: MyParam): CompletableFuture<MyParam> {
            return CompletableFuture.completedFuture(param)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testBothDirectionRequests() {
        // create client side
        val `in` = PipedInputStream()
        val out = PipedOutputStream()
        val in2 = PipedInputStream()
        val out2 = PipedOutputStream()
        `in`.connect(out2)
        out.connect(in2)
        val client: MyClient = MyClientImpl()
        val clientSideLauncher: Launcher<MyClient, MyServer> = Launcher.createLauncher(client, MyServer::class, `in`, out)

        // create server side
        val server: MyServer = MyServerImpl()
        val serverSideLauncher: Launcher<MyServer, MyClient> = Launcher.createLauncher(server, MyClient::class, in2, out2)
        clientSideLauncher.startListening()
        serverSideLauncher.startListening()
        val fooFuture = clientSideLauncher.remoteProxy.askServer(MyParam("FOO"))
        val barFuture= serverSideLauncher.remoteProxy.askClient(MyParam("BAR"))
        assertEquals("FOO", fooFuture[TIMEOUT, TimeUnit.MILLISECONDS]?.value)
        assertEquals("BAR", barFuture[TIMEOUT, TimeUnit.MILLISECONDS].value)
    }

    @Test
    fun `server responds to request with string id`() {
        // create client message
        val requestMessage =
            """{"jsonrpc": "2.0","id": "42","method": "askServer","params": { "value": "bar" }}"""
        val clientMessage = getClientMessage(requestMessage)

        // create server side
        val `in` = ByteArrayInputStream(clientMessage.toByteArray())
        val out = ByteArrayOutputStream()
        val server: MyServer = MyServerImpl()
        val serverSideLauncher: Launcher<MyServer, MyClient> = Launcher.createLauncher(server, MyClient::class, `in`, out)
        serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)

        val actualJson = out.toString().removeContentLengthAndParse(52)
        val expectedJson = Json.parseToJsonElement(
            """{"jsonrpc":"2.0","id":"42","result":{"value":"bar"}}"""
        )

        assertEquals(
            expectedJson,
            actualJson
        )
    }

    @Test
    fun `server responds to request with number id`() {
        // create client message
        val requestMessage =
            """{"jsonrpc": "2.0","id": 42,"method": "askServer","params": { "value": "bar" }}"""
        val clientMessage = getClientMessage(requestMessage)

        // create server side
        val `in` = ByteArrayInputStream(clientMessage.toByteArray())
        val out = ByteArrayOutputStream()
        val server: MyServer = MyServerImpl()
        val serverSideLauncher: Launcher<MyServer, MyClient> = Launcher.createLauncher(server, MyClient::class, `in`, out)
        serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)

        val header = "Content-Length: 50$CRLF$CRLF"
        val actualJson = Json.parseToJsonElement(out.toString().removePrefix(header))
        val expectedJson = Json.parseToJsonElement(
            """{"jsonrpc":"2.0","id":42,"result":{"value":"bar"}}"""
        )
        assertEquals(
            expectedJson,
            actualJson
        )
    }

    @Test
    fun `server correctly handles nulls`() {
        // create client message
        val requestMessage = """{"jsonrpc": "2.0","id": 42,"method": "askServer","params": { "value": null }}"""
        val clientMessage = getClientMessage(requestMessage)

        // create server side
        val `in` = ByteArrayInputStream(clientMessage.toByteArray())
        val out = ByteArrayOutputStream()
        val server: MyServer = MyServerImpl()
        val serverSideLauncher: Launcher<MyServer, MyClient> = Launcher.createLauncher(server, MyClient::class, `in`, out)
        serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)

        val header = "Content-Length: 49$CRLF$CRLF"
        val actualJson = Json.parseToJsonElement(out.toString().removePrefix(header))
        val expectedJson = Json.parseToJsonElement(
            """{"jsonrpc":"2.0","id":42,"result":{"value":null}}"""
        )
        assertEquals(
            expectedJson,
            actualJson
        )
    }

    @Test
    fun `unit response`() {
        // create client side
        val `in` = PipedInputStream()
        val out = PipedOutputStream()
        val in2 = PipedInputStream()
        val out2 = PipedOutputStream()
        `in`.connect(out2)
        out.connect(in2)
        val client: MyClient = object : MyClient {
            override fun askClient(param: MyParam): CompletableFuture<MyParam> {
                throw UnsupportedOperationException("Unused by this test")
            }
        }
        val clientSideLauncher: Launcher<MyClient, MyVoidServer> = Launcher.createLauncher(
            client, MyVoidServer::class, `in`, out
        )

        // create server side
        val server: MyServer = object : MyServer {
            override fun askServer(param: MyParam): CompletableFuture<MyParam> {
                return CompletableFuture.completedFuture(param)
            }
        }
        val serverSideLauncher: Launcher<MyServer, MyClient> = Launcher.createLauncher(server, MyClient::class, in2, out2)
        clientSideLauncher.startListening()
        serverSideLauncher.startListening()

        // We call a method that is declared as returning Unit, but the other end returns a non-null value
        // make sure that the json parsing discards that result
        val fooFuture: CompletableFuture<Unit> = clientSideLauncher.remoteProxy.askServer(MyParam("FOO"))
        val unit = fooFuture.get(TIMEOUT, TimeUnit.MILLISECONDS)
        assertEquals(Unit, unit)
    }

    @Test
    @Throws(Exception::class)
    fun `client-side cancellation works`() {
        // create client side
        val `in` = PipedInputStream()
        val out = PipedOutputStream()
        val in2 = PipedInputStream()
        val out2 = PipedOutputStream()
        `in`.connect(out2)
        out.connect(in2)
        val inComputeAsync = BooleanArray(1)
        val cancellationHappened = BooleanArray(1)
        val client: MyClient = object : MyClient {
            override fun askClient(param: MyParam): CompletableFuture<MyParam> {
                return CompletableFutures.computeAsync { cancelToken ->
                    try {
                        val startTime: Long = System.currentTimeMillis()
                        inComputeAsync[0] = true
                        do {
                            cancelToken.checkCanceled()
                            Thread.sleep(50)
                        } while (System.currentTimeMillis() - startTime < TIMEOUT)
                    } catch (e: CancellationException) {
                        cancellationHappened[0] = true
                    } catch (e: InterruptedException) {
                        fail("Thread was interrupted unexpectedly.")
                    }
                    param
                }
            }
        }
        val clientSideLauncher = Launcher.createLauncher(client, MyServer::class, `in`, out)

        // create server side
        val server: MyServer = MyServerImpl()
        val serverSideLauncher = Launcher.createLauncher(server, MyClient::class, in2, out2)
        clientSideLauncher.startListening()
        serverSideLauncher.startListening()
        val future: CompletableFuture<MyParam> = serverSideLauncher.remoteProxy.askClient(MyParam("FOO"))
        var startTime = System.currentTimeMillis()
        while (!inComputeAsync[0]) {
            Thread.sleep(50)
            if (System.currentTimeMillis() - startTime > TIMEOUT) fail("Timeout waiting for client to start computing.") as Any
        }
        future.cancel(true)
        startTime = System.currentTimeMillis()
        while (!cancellationHappened[0]) {
            Thread.sleep(50)
            if (System.currentTimeMillis() - startTime > TIMEOUT) fail("Timeout waiting for confirmation of cancellation.") as Any
        }
        try {
            future.get(TIMEOUT, TimeUnit.MILLISECONDS)
            fail("Expected cancellation.")
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun `cancellation response is correct`() {
        // create client messages
        val requestMessage =
            """{"jsonrpc": "2.0","id": 1,"method": "askServer","params": { "value": "bar" }}"""
        val cancellationMessage =
            """{"jsonrpc": "2.0","method": "$/cancelRequest","params": { "id": 1 }}"""
        val clientMessages = getClientMessage(requestMessage) + getClientMessage(cancellationMessage)

        // create server side
        val `in` = ByteArrayInputStream(clientMessages.toByteArray())
        val out = ByteArrayOutputStream()
        val server: MyServer = object : MyServer {
            override fun askServer(param: MyParam): CompletableFuture<MyParam> {
                return CompletableFutures.computeAsync { cancelToken ->
                    try {
                        val startTime: Long = System.currentTimeMillis()
                        do {
                            cancelToken.checkCanceled()
                            Thread.sleep(50)
                        } while (System.currentTimeMillis() - startTime < TIMEOUT)
                    } catch (e: InterruptedException) {
                        fail("Thread was interrupted unexpectedly.")
                    }
                    param
                }
            }
        }
        val serverSideLauncher: Launcher<MyServer, MyClient> = Launcher.createLauncher(server, MyClient::class, `in`, out)
        serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)

        val header = "Content-Length: 120$CRLF$CRLF"
        val actualJson = Json.parseToJsonElement(out.toString().removePrefix(header))
        val expected = Json.parseToJsonElement("""{"id":1,"error":{"code":-32800,"message":"The request (id: 1, method: 'askServer') has been cancelled"},"jsonrpc":"2.0"}""")

        assertEquals(
            expected,
            actualJson
        )
    }

    @Test
    fun testVersatility() {
        Logger.getLogger(RemoteEndpoint::class.jvmName).level = Level.OFF
        // create client side
        val `in` = PipedInputStream()
        val out = PipedOutputStream()
        val in2 = PipedInputStream()
        val out2 = PipedOutputStream()

        // See https://github.com/eclipse-lsp4j/lsp4j/issues/510 for full details.
        // Make sure that the thread that writes to the PipedOutputStream stays alive
        // until the read from the PipedInputStream. Using a cached thread pool
        // does not 100% guarantee that, but increases the probability that the
        // selected thread will exist for the lifetime of the test.
        val executor = Executors.newCachedThreadPool()
        `in`.connect(out2)
        out.connect(in2)
        val client: MyClient = object : MyClient {
            private var tries = 0
            override fun askClient(param: MyParam): CompletableFuture<MyParam> {
                if (tries == 0) {
                    tries++
                    throw UnsupportedOperationException()
                }
                return CompletableFutures.computeAsync(executor) { cancelToken ->
                    if (tries++ == 1) throw UnsupportedOperationException()
                    param
                }
            }
        }
        val clientSideLauncher = Launcher.createLauncher(client, MyServer::class, `in`, out)

        // create server side
        val server: MyServer = MyServerImpl()
        val serverSideLauncher = Launcher.createLauncher(server, MyClient::class, in2, out2)
        clientSideLauncher.startListening()
        serverSideLauncher.startListening()
        val errorFuture1: CompletableFuture<MyParam> = serverSideLauncher.remoteProxy.askClient(MyParam("FOO"))
        try {
            println(errorFuture1.get())
            fail() as Any
        } catch (e: ExecutionException) {
            assertNotNull((e.cause as ResponseErrorException?)!!.responseError.message)
        }
        val errorFuture2: CompletableFuture<MyParam> = serverSideLauncher.remoteProxy.askClient(MyParam("FOO"))
        try {
            errorFuture2.get()
            fail()
        } catch (e: ExecutionException) {
            assertNotNull((e.cause as ResponseErrorException?)!!.responseError.message)
        }
        val goodFuture: CompletableFuture<MyParam> = serverSideLauncher.remoteProxy.askClient(MyParam("FOO"))
        assertEquals("FOO", goodFuture[TIMEOUT, TimeUnit.MILLISECONDS].value)
    }

    @Test
    fun testUnknownMessages() {
        LogMessageAccumulator(RemoteEndpoint::class).use { logMessages ->

            // create client messages
            val clientMessage1 = """{"jsonrpc": "2.0","method": "foo1"}"""
            val clientMessage2 = """{"jsonrpc": "2.0","id": "1","method": "foo2"}"""
            val clientMessages = getClientMessage(clientMessage1) + getClientMessage(clientMessage2)

            // create server side
            val `in` = ByteArrayInputStream(clientMessages.toByteArray())
            val out = ByteArrayOutputStream()
            val server: MyServer = MyServerImpl()
            val serverSideLauncher = Launcher.createLauncher(
                server, MyClient::class, `in`, out
            )
            serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
            logMessages.await(Level.WARNING, "Notification could not be handled: NotificationMessage(method=foo1, params=null). Unsupported method: foo1")
            logMessages.await(Level.WARNING, "Request could not be handled: RequestMessage(id=\"1\", method=foo2, params=null). Unsupported method: foo2")
            val header = "Content-Length: 87$CRLF$CRLF"
            val actualJson = Json.parseToJsonElement(out.toString().removePrefix(header))
            assertEquals(
                Json.parseToJsonElement("""{"id":"1","error":{"code":-32601,"message":"Unsupported method: foo2"},"jsonrpc":"2.0"}"""),
                actualJson
            )
        }
    }

    @Test
    @Throws(Exception::class)
    fun testUnknownOptionalMessages() {
        // intercept log messages
        LogMessageAccumulator(RemoteEndpoint::class).use { logMessages ->

            // create client messages
            val clientMessage1 = """{"jsonrpc": "2.0","method": "$/foo1"}"""
            val clientMessage2 = """{"jsonrpc": "2.0","id": "1","method": "$/foo2"}"""
            val clientMessages = getClientMessage(clientMessage1) + getClientMessage(clientMessage2)

            // create server side
            val `in` = ByteArrayInputStream(clientMessages.toByteArray())
            val out = ByteArrayOutputStream()
            val server: MyServer = MyServerImpl()
            val serverSideLauncher = Launcher.createLauncher(
                server, MyClient::class, `in`, out
            )
            serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
            logMessages.await(Level.INFO, "Ignoring optional notification: NotificationMessage(method=\$/foo1, params=null)")
            logMessages.await(Level.INFO, "Ignoring optional request: RequestMessage(id=\"1\", method=\$/foo2, params=null)")

            val actual = out.toString().removeContentLengthAndParse(89)
            assertEquals(
                Json.parseToJsonElement("""{"id":"1","error":{"code":-32601,"message":"Unsupported method: $/foo2"},"jsonrpc":"2.0"}"""),
                actual
            )
        }
    }

    interface UnexpectedParamsTestServer {
        @JsonNotification
        fun myNotification()
    }

    @Test
    @Throws(Exception::class)
    fun testUnexpectedParams() {
        LogMessageAccumulator(RemoteEndpoint::class).use { logMessages ->

            // create client messages
            val notificationMessage =
                ("{\"jsonrpc\": \"2.0\",\n\"method\": \"myNotification\",\n\"params\": { \"value\": \"foo\" }\n}")
            val clientMessages = getClientMessage(notificationMessage)

            // create server side
            val `in` = ByteArrayInputStream(clientMessages.toByteArray())
            val out = ByteArrayOutputStream()
            val server: UnexpectedParamsTestServer = object : UnexpectedParamsTestServer {
                override fun myNotification() {}
            }
            val serverSideLauncher = Launcher.createLauncher(
                server, MyClient::class, `in`, out
            )
            serverSideLauncher.startListening()
            logMessages.await(
                Level.WARNING,
                ("Notification could not be handled: NotificationMessage(method=myNotification, params=ObjectParams(params={\"value\":\"foo\"})). Wrong number of parameters for method myNotification: expected 0, got 1")
            )
        }
    }

    // TODO: We don't support handling malformed json yet, because kotlinx.serialization doesn't support it.
//
//    @Test
//    @Ignore
//    fun testMalformedJson1() {
//        val requestMessage1 =
//            ("{\"jsonrpc\": \"2.0\",\n\"id\": \"1\",\n\"method\": \"askServer\",\n\"params\": { \"value\": }\n}")
//        val requestMessage2 =
//            ("{\"jsonrpc\": \"2.0\",\n\"id\": \"2\",\n\"method\": \"askServer\",\n\"params\": { \"value\": \"bar\" }\n}")
//        val clientMessages =
//            getClientMessage(requestMessage1) + getClientMessage(requestMessage2)
//        val `in` = ByteArrayInputStream(clientMessages.toByteArray())
//        val out = ByteArrayOutputStream()
//        val server: MyServer = MyServerImpl()
//        val serverSideLauncher: Launcher<MyServer, MyClient> = Launcher.createLauncher(server, MyClient::class, `in`, out)
//        serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
//        assertEquals(
//            (("Content-Length: 214$CRLF$CRLF{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":-32700,\"message\":\"Message could not be parsed.\",\"data\":{\"message\":\"com.google.gson.stream.MalformedJsonException: Expected value at line 4 column 22 path $.params.value\"}}}Content-Length: 51$CRLF$CRLF").toString() + "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"value\":\"bar\"}}"),
//            out.toString()
//        )
//    }
//
//    @Test
//    @Ignore
//    fun testMalformedJson2() {
//        // intercept log messages
//        val logMessages = LogMessageAccumulator()
//        try {
//            logMessages.registerTo(StreamMessageProducer::class)
//            val requestMessage1 =
//                ("{\"jsonrpc\": \"2.0\",\n\"params\": { \"value\": }\n\"id\": \"1\",\n\"method\":\"askServer\",\n}")
//            val requestMessage2 =
//                ("{\"jsonrpc\": \"2.0\",\n\"id\": \"2\",\n\"method\": \"askServer\",\n\"params\": { \"value\": \"bar\" }\n}")
//            val clientMessages = getClientMessage(requestMessage1) + getClientMessage(requestMessage2)
//            val `in` = ByteArrayInputStream(clientMessages.toByteArray())
//            val out = ByteArrayOutputStream()
//            val server: MyServer = MyServerImpl()
//            val serverSideLauncher = Launcher.createLauncher(
//                server, MyClient::class, `in`, out
//            )
//            serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
//            logMessages.await(
//                Level.SEVERE,
//                "com.google.gson.stream.MalformedJsonException: Expected value at line 2 column 22 path $.params.value"
//            )
//            assertEquals(
//                ("Content-Length: 51$CRLF$CRLF{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"value\":\"bar\"}}"),
//                out.toString()
//            )
//        } finally {
//            logMessages.unregister()
//        }
//    }
//
//    @Test
//    @Ignore
//    fun testMalformedJson3() {
//        val requestMessage1 =
//            ("{\"jsonrpc\": \"2.0\",\n\"id\": \"1\",\n\"method\": \"askServer\",\n\"params\": { \"value\": \"bar\" }\n]")
//        val requestMessage2 =
//            ("{\"jsonrpc\": \"2.0\",\n\"id\": \"2\",\n\"method\": \"askServer\",\n\"params\": { \"value\": \"bar\" }\n}")
//        val clientMessages = getClientMessage(requestMessage1) + getClientMessage(requestMessage2)
//        val `in` = ByteArrayInputStream(clientMessages.toByteArray())
//        val out = ByteArrayOutputStream()
//        val server: MyServer = MyServerImpl()
//        val serverSideLauncher: Launcher<MyServer, MyClient> = Launcher.createLauncher(server, MyClient::class, `in`, out)
//        serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
//        assertEquals(
//            """Content-Length: 165$CRLF$CRLF{"jsonrpc":"2.0","id":"1","error":{"code":-32700,"message":"Message could not be parsed.","data":{"message":"Unterminated object at line 5 column 2 path $.params"}}}Content-Length: 51$CRLF$CRLF{"jsonrpc":"2.0","id":"2","result":{"value":"bar"}}""",
//            out.toString()
//        )
//    }
//
//    @Test
//    @Ignore
//    fun testMalformedJson4() {
//        val requestMessage1 =
//            "{\"jsonrpc\": \"2.0\",\n\"id\": \"1\",\n\"method\": \"askServer\",\n\"params\": { \"value\": \"bar\" }\n}}"
//        val requestMessage2 =
//            ("{\"jsonrpc\":\"2.0\",\n\"id\":\"2\",\n\"method\":\"askServer\",\n\"params\": { \"value\": \"bar\" }\n}")
//        val clientMessages = getClientMessage(requestMessage1) + getClientMessage(requestMessage2)
//        val `in` = ByteArrayInputStream(clientMessages.toByteArray())
//        val out = ByteArrayOutputStream()
//        val server: MyServer = MyServerImpl()
//        val serverSideLauncher: Launcher<MyServer, MyClient> = Launcher.createLauncher(server, MyClient::class, `in`, out)
//        serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
//        assertEquals(
//            (("Content-Length: 195$CRLF$CRLF{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":-32700,\"message\":\"Message could not be parsed.\",\"data\":{\"message\":\"Use JsonReader.setLenient(true) to accept malformed JSON at line 5 column 3 path $\"}}}Content-Length: 51$CRLF$CRLF").toString() + "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"value\":\"bar\"}}"),
//            out.toString()
//        )
//    }

    private fun getClientMessage(requestMessage: String): String {
        val contentLength = requestMessage.toByteArray().size
        val builder = StringBuilder()
        builder.append(CONTENT_LENGTH_HEADER).append(": ").append(contentLength).append(CRLF)
        builder.append(CRLF)
        builder.append(requestMessage)
        return builder.toString()
    }

    companion object {
        private val TIMEOUT: Long = 2000
    }
}
