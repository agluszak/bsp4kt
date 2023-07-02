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
import org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.*
import java.util.logging.Level
import java.util.logging.Logger

class IntegrationTest {
    class MyParam(val value: String)

    interface MyServer {
        @JsonRequest
        fun askServer(param: MyParam): CompletableFuture<MyParam>
    }

    interface MyVoidServer {
        @JsonRequest
        fun askServer(param: MyParam): CompletableFuture<Void>
    }

    class MyServerImpl() : MyServer {
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
        val clientSideLauncher: Launcher<MyServer> = Launcher.createLauncher(client, MyServer::class.java, `in`, out)

        // create server side
        val server: MyServer = MyServerImpl()
        val serverSideLauncher: Launcher<MyClient> = Launcher.createLauncher(server, MyClient::class.java, in2, out2)
        clientSideLauncher.startListening()
        serverSideLauncher.startListening()
        val fooFuture: CompletableFuture<MyParam> = clientSideLauncher.remoteProxy.askServer(MyParam("FOO"))
        val barFuture: CompletableFuture<MyParam> = serverSideLauncher.remoteProxy.askClient(MyParam("BAR"))
        assertEquals("FOO", fooFuture[TIMEOUT, TimeUnit.MILLISECONDS].value)
        assertEquals("BAR", barFuture[TIMEOUT, TimeUnit.MILLISECONDS].value)
    }

    @Test
    @Throws(Exception::class)
    fun testResponse1() {
        // create client message
        val requestMessage =
            ("{\"jsonrpc\": \"2.0\",\n" + "\"id\": \"42\",\n" + "\"method\": \"askServer\",\n" + "\"params\": { \"value\": \"bar\" }\n" + "}")
        val clientMessage = getHeader(requestMessage.toByteArray().size) + requestMessage

        // create server side
        val `in` = ByteArrayInputStream(clientMessage.toByteArray())
        val out = ByteArrayOutputStream()
        val server: MyServer = MyServerImpl()
        val serverSideLauncher: Launcher<MyClient> = Launcher.createLauncher(server, MyClient::class.java, `in`, out)
        serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
        assertEquals(
            ("Content-Length: 52$CRLF$CRLF").toString() + "{\"jsonrpc\":\"2.0\",\"id\":\"42\",\"result\":{\"value\":\"bar\"}}",
            out.toString()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testResponse2() {
        // create client message
        val requestMessage =
            ("{\"jsonrpc\": \"2.0\",\n" + "\"id\": 42,\n" + "\"method\": \"askServer\",\n" + "\"params\": { \"value\": \"bar\" }\n" + "}")
        val clientMessage = getHeader(requestMessage.toByteArray().size) + requestMessage

        // create server side
        val `in` = ByteArrayInputStream(clientMessage.toByteArray())
        val out = ByteArrayOutputStream()
        val server: MyServer = MyServerImpl()
        val serverSideLauncher: Launcher<MyClient> = Launcher.createLauncher(server, MyClient::class.java, `in`, out)
        serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
        assertEquals(
            """Content-Length: 50$CRLF$CRLF{"jsonrpc":"2.0","id":42,"result":{"value":"bar"}}""",
            out.toString()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testEither() {
        // create client message
        val requestMessage =
            ("{\"jsonrpc\": \"2.0\",\n" + "\"id\": 42,\n" + "\"method\": \"askServer\",\n" + "\"params\": { \"either\": \"bar\", \"value\": \"foo\" }\n" + "}")
        val clientMessage = getHeader(requestMessage.toByteArray().size) + requestMessage

        // create server side
        val `in` = ByteArrayInputStream(clientMessage.toByteArray())
        val out = ByteArrayOutputStream()
        val server: MyServer = MyServerImpl()
        val serverSideLauncher: Launcher<MyClient> = Launcher.createLauncher(server, MyClient::class.java, `in`, out)
        serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
        assertEquals(
            (("Content-Length: 65$CRLF$CRLF").toString() + "{\"jsonrpc\":\"2.0\",\"id\":42,\"result\":{\"value\":\"foo\",\"either\":\"bar\"}}"),
            out.toString()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testEitherNull() {
        // create client message
        val requestMessage =
            ("{\"jsonrpc\": \"2.0\",\n" + "\"id\": 42,\n" + "\"method\": \"askServer\",\n" + "\"params\": { \"either\": null, \"value\": \"foo\" }\n" + "}")
        val clientMessage = getHeader(requestMessage.toByteArray().size) + requestMessage

        // create server side
        val `in` = ByteArrayInputStream(clientMessage.toByteArray())
        val out = ByteArrayOutputStream()
        val server: MyServer = MyServerImpl()
        val serverSideLauncher: Launcher<MyClient> = Launcher.createLauncher(server, MyClient::class.java, `in`, out)
        serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
        assertEquals(
            (("Content-Length: 50$CRLF$CRLF").toString() + "{\"jsonrpc\":\"2.0\",\"id\":42,\"result\":{\"value\":\"foo\"}}"),
            out.toString()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testVoidResponse() {
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
        val clientSideLauncher: Launcher<MyVoidServer> = Launcher.createLauncher(
            client, MyVoidServer::class.java, `in`, out
        )

        // create server side
        val server: MyServer = object : MyServer {
            override fun askServer(param: MyParam): CompletableFuture<MyParam> {
                return CompletableFuture.completedFuture(param)
            }
        }
        val serverSideLauncher: Launcher<MyClient> = Launcher.createLauncher(server, MyClient::class.java, in2, out2)
        clientSideLauncher.startListening()
        serverSideLauncher.startListening()

        // We call a method that is declared as returning Void, but the other end returns a non-null value
        // make sure that the json parsing discards that result
        val fooFuture: CompletableFuture<Void> = clientSideLauncher.remoteProxy.askServer(MyParam("FOO"))
        val void1 = fooFuture[TIMEOUT, TimeUnit.MILLISECONDS]
        assertNull(void1)
    }

    @Test
    @Throws(Exception::class)
    fun testCancellation() {
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
        val clientSideLauncher: Launcher<MyServer> = Launcher.createLauncher(client, MyServer::class.java, `in`, out)

        // create server side
        val server: MyServer = MyServerImpl()
        val serverSideLauncher: Launcher<MyClient> = Launcher.createLauncher(server, MyClient::class.java, in2, out2)
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
            future[TIMEOUT, TimeUnit.MILLISECONDS]
            fail("Expected cancellation.")
        } catch (_: CancellationException) {
        }
    }

    @Test
    @Throws(Exception::class)
    fun testCancellationResponse() {
        // create client messages
        val requestMessage =
            ("{\"jsonrpc\": \"2.0\",\n" + "\"id\": \"1\",\n" + "\"method\": \"askServer\",\n" + "\"params\": { \"value\": \"bar\" }\n" + "}")
        val cancellationMessage =
            ("{\"jsonrpc\": \"2.0\",\n" + "\"method\": \"$/cancelRequest\",\n" + "\"params\": { \"id\": 1 }\n" + "}")
        val clientMessages =
            (getHeader(requestMessage.toByteArray().size) + requestMessage + getHeader(cancellationMessage.toByteArray().size) + cancellationMessage)

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
        val serverSideLauncher: Launcher<MyClient> = Launcher.createLauncher(server, MyClient::class.java, `in`, out)
        serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
        assertEquals(
            (("Content-Length: 132$CRLF$CRLF").toString() + "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":-32800,\"message\":\"The request (id: 1, method: \\u0027askServer\\u0027) has been cancelled\"}}"),
            out.toString()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testVersatility() {
        Logger.getLogger(RemoteEndpoint::class.java.name).level = Level.OFF
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
        val clientSideLauncher: Launcher<MyServer> = Launcher.createLauncher(client, MyServer::class.java, `in`, out)

        // create server side
        val server: MyServer = MyServerImpl()
        val serverSideLauncher: Launcher<MyClient> = Launcher.createLauncher(server, MyClient::class.java, in2, out2)
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
    @Throws(Exception::class)
    fun testUnknownMessages() {
        // intercept log messages
        val logMessages = LogMessageAccumulator()
        try {
            logMessages.registerTo(GenericEndpoint::class.java)

            // create client messages
            val clientMessage1 =
                ("{\"jsonrpc\": \"2.0\",\n" + "\"method\": \"foo1\",\n" + "\"params\": \"bar\"\n" + "}")
            val clientMessage2 =
                ("{\"jsonrpc\": \"2.0\",\n" + "\"id\": \"1\",\n" + "\"method\": \"foo2\",\n" + "\"params\": \"bar\"\n" + "}")
            val clientMessages =
                (getHeader(clientMessage1.toByteArray().size) + clientMessage1 + getHeader(clientMessage2.toByteArray().size) + clientMessage2)

            // create server side
            val `in` = ByteArrayInputStream(clientMessages.toByteArray())
            val out = ByteArrayOutputStream()
            val server: MyServer = MyServerImpl()
            val serverSideLauncher: Launcher<MyClient> = Launcher.createLauncher(
                server, MyClient::class.java, `in`, out
            )
            serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
            logMessages.await(Level.WARNING, "Unsupported notification method: foo1")
            logMessages.await(Level.WARNING, "Unsupported request method: foo2")
            assertEquals(
                (("Content-Length: 95$CRLF$CRLF").toString() + "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":-32601,\"message\":\"Unsupported request method: foo2\"}}"),
                out.toString()
            )
        } finally {
            logMessages.unregister()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testUnknownOptionalMessages() {
        // intercept log messages
        val logMessages = LogMessageAccumulator()
        try {
            logMessages.registerTo(GenericEndpoint::class.java)

            // create client messages
            val clientMessage1 =
                ("{\"jsonrpc\": \"2.0\",\n" + "\"method\": \"$/foo1\",\n" + "\"params\": \"bar\"\n" + "}")
            val clientMessage2 =
                ("{\"jsonrpc\": \"2.0\",\n" + "\"id\": \"1\",\n" + "\"method\": \"$/foo2\",\n" + "\"params\": \"bar\"\n" + "}")
            val clientMessages =
                (getHeader(clientMessage1.toByteArray().size) + clientMessage1 + getHeader(clientMessage2.toByteArray().size) + clientMessage2)

            // create server side
            val `in` = ByteArrayInputStream(clientMessages.toByteArray())
            val out = ByteArrayOutputStream()
            val server: MyServer = MyServerImpl()
            val serverSideLauncher: Launcher<MyClient> = Launcher.createLauncher(
                server, MyClient::class.java, `in`, out
            )
            serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
            logMessages.await(Level.INFO, "Unsupported notification method: $/foo1")
            logMessages.await(Level.INFO, "Unsupported request method: $/foo2")
            assertEquals(
                (("Content-Length: 40$CRLF$CRLF").toString() + "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":null}"),
                out.toString()
            )
        } finally {
            logMessages.unregister()
        }
    }

    interface UnexpectedParamsTestServer {
        @JsonNotification
        fun myNotification()
    }

    @Test
    @Throws(Exception::class)
    fun testUnexpectedParams() {
        // intercept log messages
        val logMessages = LogMessageAccumulator()
        try {
            logMessages.registerTo(GenericEndpoint::class.java)

            // create client messages
            val notificationMessage =
                ("{\"jsonrpc\": \"2.0\",\n" + "\"method\": \"myNotification\",\n" + "\"params\": { \"value\": \"foo\" }\n" + "}")
            val clientMessages = getHeader(notificationMessage.toByteArray().size) + notificationMessage

            // create server side
            val `in` = ByteArrayInputStream(clientMessages.toByteArray())
            val out = ByteArrayOutputStream()
            val server: UnexpectedParamsTestServer = object : UnexpectedParamsTestServer {
                override fun myNotification() {}
            }
            val serverSideLauncher: Launcher<MyClient> = Launcher.createLauncher(
                server, MyClient::class.java, `in`, out
            )
            serverSideLauncher.startListening()
            logMessages.await(
                Level.WARNING,
                ("Unexpected params '{\"value\":\"foo\"}' for " + "'public abstract void org.eclipse.lsp4j.jsonrpc.test.IntegrationTest\$UnexpectedParamsTestServer.myNotification()' is ignored")
            )
        } finally {
            logMessages.unregister()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMalformedJson1() {
        val requestMessage1 =
            ("{\"jsonrpc\": \"2.0\",\n" + "\"id\": \"1\",\n" + "\"method\": \"askServer\",\n" + "\"params\": { \"value\": }\n" + "}")
        val requestMessage2 =
            ("{\"jsonrpc\": \"2.0\",\n" + "\"id\": \"2\",\n" + "\"method\": \"askServer\",\n" + "\"params\": { \"value\": \"bar\" }\n" + "}")
        val clientMessages =
            (getHeader(requestMessage1.toByteArray().size) + requestMessage1 + getHeader(requestMessage2.toByteArray().size) + requestMessage2)
        val `in` = ByteArrayInputStream(clientMessages.toByteArray())
        val out = ByteArrayOutputStream()
        val server: MyServer = MyServerImpl()
        val serverSideLauncher: Launcher<MyClient> = Launcher.createLauncher(server, MyClient::class.java, `in`, out)
        serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
        assertEquals(
            ((("Content-Length: 214$CRLF$CRLF").toString() + "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":-32700,\"message\":\"Message could not be parsed.\"," + "\"data\":{\"message\":\"com.google.gson.stream.MalformedJsonException: Expected value at line 4 column 22 path $.params.value\"}}}" + "Content-Length: 51" + CRLF + CRLF).toString() + "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"value\":\"bar\"}}"),
            out.toString()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testMalformedJson2() {
        // intercept log messages
        val logMessages = LogMessageAccumulator()
        try {
            logMessages.registerTo(StreamMessageProducer::class.java)
            val requestMessage1 =
                ("{\"jsonrpc\": \"2.0\",\n" + "\"params\": { \"value\": }\n" + "\"id\": \"1\",\n" + "\"method\":\"askServer\",\n" + "}")
            val requestMessage2 =
                ("{\"jsonrpc\": \"2.0\",\n" + "\"id\": \"2\",\n" + "\"method\": \"askServer\",\n" + "\"params\": { \"value\": \"bar\" }\n" + "}")
            val clientMessages =
                (getHeader(requestMessage1.toByteArray().size) + requestMessage1 + getHeader(requestMessage2.toByteArray().size) + requestMessage2)
            val `in` = ByteArrayInputStream(clientMessages.toByteArray())
            val out = ByteArrayOutputStream()
            val server: MyServer = MyServerImpl()
            val serverSideLauncher: Launcher<MyClient> = Launcher.createLauncher(
                server, MyClient::class.java, `in`, out
            )
            serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
            logMessages.await(
                Level.SEVERE,
                "com.google.gson.stream.MalformedJsonException: Expected value at line 2 column 22 path $.params.value"
            )
            assertEquals(
                (("Content-Length: 51$CRLF$CRLF").toString() + "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"value\":\"bar\"}}"),
                out.toString()
            )
        } finally {
            logMessages.unregister()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMalformedJson3() {
        val requestMessage1 =
            ("{\"jsonrpc\": \"2.0\",\n" + "\"id\": \"1\",\n" + "\"method\": \"askServer\",\n" + "\"params\": { \"value\": \"bar\" }\n" + "]")
        val requestMessage2 =
            ("{\"jsonrpc\": \"2.0\",\n" + "\"id\": \"2\",\n" + "\"method\": \"askServer\",\n" + "\"params\": { \"value\": \"bar\" }\n" + "}")
        val clientMessages =
            (getHeader(requestMessage1.toByteArray().size) + requestMessage1 + getHeader(requestMessage2.toByteArray().size) + requestMessage2)
        val `in` = ByteArrayInputStream(clientMessages.toByteArray())
        val out = ByteArrayOutputStream()
        val server: MyServer = MyServerImpl()
        val serverSideLauncher: Launcher<MyClient> = Launcher.createLauncher(server, MyClient::class.java, `in`, out)
        serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
        assertEquals(
            ((("Content-Length: 165$CRLF$CRLF").toString() + "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":-32700,\"message\":\"Message could not be parsed.\"," + "\"data\":{\"message\":\"Unterminated object at line 5 column 2 path $.params\"}}}" + "Content-Length: 51" + CRLF + CRLF).toString() + "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"value\":\"bar\"}}"),
            out.toString()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testMalformedJson4() {
        val requestMessage1 =
            ("{\"jsonrpc\": \"2.0\",\n" + "\"id\": \"1\",\n" + "\"method\": \"askServer\",\n" + "\"params\": { \"value\": \"bar\" }\n" + "}}")
        val requestMessage2 =
            ("{\"jsonrpc\":\"2.0\",\n" + "\"id\":\"2\",\n" + "\"method\":\"askServer\",\n" + "\"params\": { \"value\": \"bar\" }\n" + "}")
        val clientMessages =
            (getHeader(requestMessage1.toByteArray().size) + requestMessage1 + getHeader(requestMessage2.toByteArray().size) + requestMessage2)
        val `in` = ByteArrayInputStream(clientMessages.toByteArray())
        val out = ByteArrayOutputStream()
        val server: MyServer = MyServerImpl()
        val serverSideLauncher: Launcher<MyClient> = Launcher.createLauncher(server, MyClient::class.java, `in`, out)
        serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
        assertEquals(
            ((("Content-Length: 195$CRLF$CRLF").toString() + "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":-32700,\"message\":\"Message could not be parsed.\"," + "\"data\":{\"message\":\"Use JsonReader.setLenient(true) to accept malformed JSON at line 5 column 3 path $\"}}}" + "Content-Length: 51" + CRLF + CRLF).toString() + "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"value\":\"bar\"}}"),
            out.toString()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testValidationIssue1() {
        val requestMessage1 =
            ("{\"jsonrpc\": \"2.0\",\n" + "\"id\": \"1\",\n" + "\"method\": \"askServer\",\n" + "\"params\": { \"value\": null }\n" + "}")
        val requestMessage2 =
            ("{\"jsonrpc\": \"2.0\",\n" + "\"id\": \"2\",\n" + "\"method\": \"askServer\",\n" + "\"params\": { \"value\": \"bar\" }\n" + "}")
        val clientMessages =
            (getHeader(requestMessage1.toByteArray().size) + requestMessage1 + getHeader(requestMessage2.toByteArray().size) + requestMessage2)
        val `in` = ByteArrayInputStream(clientMessages.toByteArray())
        val out = ByteArrayOutputStream()
        val server: MyServer = MyServerImpl()
        val serverSideLauncher: Launcher<MyClient> = Launcher.createLauncher(
            server, MyClient::class.java, `in`, out
        )
        serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
        assertEquals(
            ((("Content-Length: 157$CRLF$CRLF").toString() + "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":-32602,\"message\":\"The accessor \\u0027MyParam.getValue()\\u0027 must return a non-null value. Path: $.params.value\"}}" + "Content-Length: 51" + CRLF + CRLF).toString() + "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"result\":{\"value\":\"bar\"}}"),
            out.toString()
        )
    }

    @Test
    @Throws(Exception::class)
    fun testValidationIssue2() {
        val requestMessage1 =
            ("{\"jsonrpc\": \"2.0\",\n" + "\"id\": \"1\",\n" + "\"method\": \"askServer\",\n" + "\"params\": { \"value\": null, \"nested\": { \"value\": null } }\n" + "}")
        val clientMessages = getHeader(requestMessage1.toByteArray().size) + requestMessage1
        val `in` = ByteArrayInputStream(clientMessages.toByteArray())
        val out = ByteArrayOutputStream()
        val server: MyServer = MyServerImpl()
        val serverSideLauncher: Launcher<MyClient> = Launcher.createLauncher(
            server, MyClient::class.java, `in`, out
        )
        serverSideLauncher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
        assertEquals(
            (("Content-Length: 379$CRLF$CRLF").toString() + "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":-32600,\"message\":\"Multiple issues were found in \\u0027askServer\\u0027 request.\"," + "\"data\":[" + "{\"text\":\"The accessor \\u0027MyParam.getValue()\\u0027 must return a non-null value. Path: $.params.nested.value\",\"code\":-32602}," + "{\"text\":\"The accessor \\u0027MyParam.getValue()\\u0027 must return a non-null value. Path: $.params.value\",\"code\":-32602}" + "]}}"),
            out.toString()
        )
    }

    protected fun getHeader(contentLength: Int): String {
        val headerBuilder = StringBuilder()
        headerBuilder.append(CONTENT_LENGTH_HEADER).append(": ").append(contentLength).append(CRLF)
        headerBuilder.append(CRLF)
        return headerBuilder.toString()
    }

    companion object {
        private val TIMEOUT: Long = 2000
    }
}
