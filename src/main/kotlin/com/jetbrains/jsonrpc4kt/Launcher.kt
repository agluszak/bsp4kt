package com.jetbrains.jsonrpc4kt

import com.jetbrains.jsonrpc4kt.RemoteEndpoint.Companion.DEFAULT_EXCEPTION_HANDLER
import com.jetbrains.jsonrpc4kt.json.*
import com.jetbrains.jsonrpc4kt.messages.Message
import com.jetbrains.jsonrpc4kt.messages.ResponseError
import com.jetbrains.jsonrpc4kt.services.ServiceEndpoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.util.function.Function
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName


/**
 * This is the entry point for applications that use LSP4J. A Launcher does all the wiring that is necessary to connect
 * your endpoint via an input stream and an output stream.
 *
 * @param <T> remote service interface type
</T> */
class Launcher<Local : Any, Remote : Any>(
    val input: InputStream,
    val output: OutputStream,
    val localService: Local,
    val remoteInterface: KClass<out Remote>,
    val coroutineScope: CoroutineScope,
    val json: Json = Json {
        ignoreUnknownKeys = true
    },
    val exceptionHandler: Function<Throwable, ResponseError> = DEFAULT_EXCEPTION_HANDLER
) {

    val supportedMethods: Map<String, JsonRpcMethod>

    init {
        supportedMethods = run {
            val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
            supportedMethods.putAll(ServiceEndpoints.getSupportedMethods(remoteInterface))
            supportedMethods.putAll(ServiceEndpoints.getSupportedMethods(localService::class))
            supportedMethods
        }
    }

    val jsonHandler: MessageJsonHandler = MessageJsonHandler(
        json,
        supportedMethods
    )


    val producerChannel = Channel<Message>()

    val consumerChannel = Channel<Message>()

    // Create the message processor
    val producer = StreamMessageProducer(input, jsonHandler, producerChannel)
    val consumer = StreamMessageConsumer(output, jsonHandler, consumerChannel)

    val localEndpoint = ServiceEndpoints.toEndpoint(localService)
    val remoteEndpoint = RemoteEndpoint(producerChannel, consumerChannel, localEndpoint, jsonHandler, coroutineScope, exceptionHandler)
    val remoteProxy = ServiceEndpoints.toServiceObject(remoteEndpoint, remoteInterface)

    fun start(): Job {
        return coroutineScope.launch {
                val producer = producer.start(this)
                val endpoint = remoteEndpoint.start(this)
                val consumer = consumer.start(this)

                producer.join()
                endpoint.join()
                consumer.join()
            }
        }

companion object {
        private val LOG = Logger.getLogger(Launcher::class.jvmName)
    }

}




