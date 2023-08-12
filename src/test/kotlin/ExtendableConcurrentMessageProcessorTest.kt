import ExtendableConcurrentMessageProcessorTest.*
import ExtendableConcurrentMessageProcessorTest.MessageContextStore.MessageContext
import com.jetbrains.jsonrpc4kt.Launcher
import com.jetbrains.jsonrpc4kt.MessageConsumer
import com.jetbrains.jsonrpc4kt.MessageProducer
import com.jetbrains.jsonrpc4kt.json.ConcurrentMessageProcessor
import com.jetbrains.jsonrpc4kt.services.JsonRequest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * This is a test to verify that it is easy for a client to override the construction
 * of the ConcurrentMessageProcessor, so that an extender making use of
 * lsp4j.jsonrpc might be able to use these bootstrapping classes
 * for different protocols that may need to identify which client is making
 * each and every request.
 *
 */
class ExtendableConcurrentMessageProcessorTest {
    /**
     * Test that an adopter making use of these APIs is able to
     * identify which client is making any given request.
     */
    @Test
    
    fun testIdentifyClientRequest() {
        val contextStore = MessageContextStore<MyClient>()
        val testContext = TestContextWrapper<MyServer, MyClient>(contextStore)

        // create client side
        val `in` = PipedInputStream()
        val out = PipedOutputStream()
        val in2 = PipedInputStream()
        val out2 = PipedOutputStream()
        `in`.connect(out2)
        out.connect(in2)
        val client: MyClient = MyClientImpl()
        val clientSideLauncher: Launcher<MyClient, MyServer> = Launcher.createLauncher(
            client,
            MyServer::class, `in`, out
        )

        // create server side
        val server: MyServer = MyServerImpl(testContext)

        val serverSideLauncher: Launcher<MyServer, MyClient> = testContext.createLauncher(
            server, MyClient::class, in2, out2
        )
        clientSideLauncher.startListening()
        serverSideLauncher.startListening()

        val fooFuture: CompletableFuture<MyParam> = clientSideLauncher.remoteProxy.askServer(MyParam("FOO"))
        val barFuture: CompletableFuture<MyParam> = serverSideLauncher.remoteProxy.askClient(MyParam("BAR"))

        assertEquals("FOO", fooFuture.get(TIMEOUT, TimeUnit.MILLISECONDS).value)
        assertEquals("BAR", barFuture.get(TIMEOUT, TimeUnit.MILLISECONDS).value)
        assertFalse(testContext.error)
    }

    /*
	 * The custom message processor, which can make sure to persist which clients are 
	 * making a given request before propagating those requests to the server implementation. 
	 */
    class CustomConcurrentMessageProcessor<T>(
        reader: MessageProducer, messageConsumer: MessageConsumer,
        private val remoteProxy: T, private val threadMap: MessageContextStore<T>
    ) :
        ConcurrentMessageProcessor(reader, messageConsumer) {
        protected override fun processingStarted() {
            super.processingStarted()
            threadMap.context = MessageContext(remoteProxy)
        }

        protected override fun processingEnded() {
            super.processingEnded()
            threadMap.clear()
        }
    }

    /*
	 * Server and client interfaces are below, along with any parameters required
	 */
    interface MyServer {
        @JsonRequest
        fun askServer(param: MyParam): CompletableFuture<MyParam>
    }

    interface MyClient {
        @JsonRequest
        fun askClient(param: MyParam): CompletableFuture<MyParam>
    }

    @Serializable
    data class MyParam(val value: String)

    class MyServerImpl(val testContext: TestContextWrapper<MyServer, MyClient>) : MyServer {
        override fun askServer(param: MyParam): CompletableFuture<MyParam> {
            testContext.error = false
            return CompletableFuture.completedFuture(param)
        }
    }

    class MyClientImpl : MyClient {
        override fun askClient(param: MyParam): CompletableFuture<MyParam> {
            return CompletableFuture.completedFuture(param)
        }
    }

    /*
	 * A custom class for storing the context for any given message
	 */
    class MessageContextStore<T> {
        private val messageContext = ThreadLocal<MessageContext<T>>()
        var context: MessageContext<T>
            /**
             * Get the context for the current request
             * @return
             */
            get() = messageContext.get()
            set(context) {
                messageContext.set(context)
            }

        /**
         * Remove the context for this request.
         * Any new requests will need to set their context anew.
         */
        fun clear() {
            messageContext.remove()
        }

        /**
         * This object can be extended to include whatever other context
         * from the raw message we may consider making available to implementations.
         * At a minimum, it should make available the remote proxy, so a given
         * request knows which remote proxy is making the request.
         */
        class MessageContext<T>(var remoteProxy: T)
    }

    /*
	 * A class used to store the results of the test (success or failure)
	 */
    class TestContextWrapper<Local : Any, Remote : Any>(
        val store: MessageContextStore<Remote>,
        var error: Boolean = false
    ) {
        fun createLauncher(
            localService: Local,
            remoteInterface: KClass<Remote>,
            `in`: InputStream,
            out: OutputStream
        ): Launcher<Local, Remote> {
            return object : Launcher.Builder<Local, Remote>(`in`, out, localService, remoteInterface) {
                override fun createMessageProcessor(
                    reader: MessageProducer,
                    messageConsumer: MessageConsumer, remoteProxy: Remote
                ): ConcurrentMessageProcessor {
                    return CustomConcurrentMessageProcessor(reader, messageConsumer, remoteProxy, store)
                }
            }
                .create()
        }
    }

    companion object {
        private const val TIMEOUT: Long = 2000

    }
}

