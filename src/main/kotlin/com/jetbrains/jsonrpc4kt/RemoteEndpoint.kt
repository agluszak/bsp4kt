package com.jetbrains.jsonrpc4kt

import com.jetbrains.jsonrpc4kt.json.MessageJsonHandler
import com.jetbrains.jsonrpc4kt.json.MethodProvider
import com.jetbrains.jsonrpc4kt.messages.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.reflect.jvm.jvmName

/**
 * An endpoint that can be used to send messages to a given [MessageConsumer] by calling
 * [.request] or [.notify]. When connected to a [MessageProducer],
 * this class forwards received messages to the local [Endpoint] given in the constructor.
 *
 * @param out - a consumer that transmits messages to the remote service
 * @param localEndpoint - the local service implementation
 * @param exceptionHandler - an exception handler that should never return null.
 */

class RemoteEndpoint(
    private val `in`: ReceiveChannel<Message>,
    private val out: SendChannel<Message>,
    private val localEndpoint: Endpoint,
    private val jsonHandler: MessageJsonHandler,
    private val coroutineScope: CoroutineScope,
    private val exceptionHandler: Function<Throwable, ResponseError> = DEFAULT_EXCEPTION_HANDLER,
) : Endpoint, MethodProvider {
    private val nextRequestId = AtomicInteger()
    private val sentRequestMap: MutableMap<MessageId, PendingRequestInfo> = ConcurrentHashMap()
    private val receivedRequestMap: MutableMap<MessageId, Deferred<*>> = ConcurrentHashMap()

    /**
     * Information about requests that have been sent and for which no response has been received yet.
     */
    private data class PendingRequestInfo(
        val requestMessage: RequestMessage,
        val future: CompletableDeferred<Any?>
    )

    fun start(coroutineScope: CoroutineScope): Job = coroutineScope.launch {
        for (message in `in`) {
            when (message) {
                is NotificationMessage -> {
                    handleNotification(message)
                }

                is RequestMessage -> {
                    handleRequest(message)
                }

                is ResponseMessage -> {
                    handleResponse(message)
                }
            }
        }
        out.close()
    }

    /**
     * Send a notification to the remote endpoint.
     */
    override fun notify(method: String, params: List<Any?>) {
        try {
            val serializedParams = jsonHandler.serializeParams(method, params)
            val notificationMessage = NotificationMessage(method, serializedParams)

            out.trySend(notificationMessage).getOrThrow()
        } catch (e: MessageIssueException) {
            LOG.log(Level.WARNING, "Error while processing the message", e)
        } catch (e: Exception) {
            val level = if (JsonRpcException.indicatesStreamClosed(e)) {
                Level.INFO
            } else {
                Level.WARNING
            }
            LOG.log(level, "Failed to send notification message", e)
        }
    }


    /**
     * Send a request to the remote endpoint.
     */
    override suspend fun request(method: String, params: List<Any?>): Any? {

        val result: CompletableDeferred<Any?> = CompletableDeferred()

//        coroutineScope.launch {
        val serializedParams = jsonHandler.serializeParams(method, params)
        val requestMessage: RequestMessage = createRequestMessage(method, serializedParams)

        result.invokeOnCompletion {
            if (it is CancellationException) {
                sendCancelNotification(requestMessage.id)
            }
        }

        // Store request information so it can be handled when the response is received
        sentRequestMap[requestMessage.id] = PendingRequestInfo(requestMessage, result)
        try {
            // Send the request to the remote service
            out.send(requestMessage)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // The message could not be sent, e.g. because the communication channel was closed
            result.completeExceptionally(exception)
        }
//        }

        val x = result.await()
        return x
    }

    private fun createRequestMessage(method: String, params: JsonParams?): RequestMessage {
        val idRaw = nextRequestId.incrementAndGet()
        val id = MessageId.NumberId(idRaw)
        return RequestMessage(id, method, params)
    }

    private fun sendCancelNotification(id: MessageId?) {
        if (id == null) {
            return
        }
        val cancelParams = CancelParams(id)
        notify(MessageJsonHandler.CANCEL_METHOD.methodName, listOf(cancelParams))
    }

    private fun handleResponse(responseMessage: ResponseMessage) {
        val requestInfo = sentRequestMap.remove(responseMessage.id)
        if (requestInfo == null) {
            // We have no pending request information that matches the id given in the response
            LOG.info { "Unmatched response message: $responseMessage" }
        } else when (responseMessage) {
            is ResponseMessage.Error ->
                // The remote service has replied with an error
                requestInfo.future.completeExceptionally(ResponseErrorException(responseMessage.error))

            is ResponseMessage.Result -> {
                // The remote service has replied with a result object
                try {
                    val deserialized =
                        jsonHandler.deserializeResult(requestInfo.requestMessage.method, responseMessage.result)
                    requestInfo.future.complete(deserialized)
                } catch (exception: MessageIssueException) {
                    requestInfo.future.completeExceptionally(exception)
                }
            }
        }
    }

    private val Message.name: String
        get() = when (this) {
            is NotificationMessage -> "Notification"
            is RequestMessage -> "Request"
            is ResponseMessage -> "Response"
        }

    private fun logMessageIssue(message: Message, exception: MessageIssueException) {
        LOG.warning { "${message.name} could not be handled: $message. ${exception.issue.message}" }
    }

    private fun logError(message: Message, exception: Throwable) {
        LOG.log(Level.SEVERE, "${message.name} threw an exception: ${exception.message}", exception)
    }

    private fun handleNotification(notificationMessage: NotificationMessage) {
        try {
            val params = jsonHandler.deserializeParams(notificationMessage)
            if (!handleCancellation(notificationMessage, params)) {
                // Forward the notification to the local endpoint
                localEndpoint.notify(notificationMessage.method, params)
            }
        } catch (e: MessageIssueException) {
            if (e.issue is NoSuchMethod && isOptional(notificationMessage)) {
                // The remote service has sent a notification for a method that is not implemented by the local endpoint
                LOG.info { "Ignoring optional notification: $notificationMessage" }
            } else {
                logMessageIssue(notificationMessage, e)
            }
        } catch (exception: Throwable) {
            logError(notificationMessage, exception)
        }
    }

    /**
     * Cancellation is handled inside this class and not forwarded to the local endpoint.
     *
     * @return `true` if the given message is a cancellation notification,
     * `false` if it can be handled by the local endpoint
     */
    private fun handleCancellation(notificationMessage: NotificationMessage, cancelParams: List<Any?>): Boolean {
        println("handleCancellation")
        if (MessageJsonHandler.CANCEL_METHOD.methodName == notificationMessage.method) {
            val cancelParam = cancelParams[0]
            if (cancelParam != null) {
                if (cancelParam is CancelParams) {
                    val id = cancelParam.id
                    val future = receivedRequestMap[id]
                    println("handleCancellation: $id, $future")
                    future?.cancel() ?: LOG.info { "Unmatched cancel notification for request id $id" }
                    return true
                } else {
                    LOG.info { "Cancellation support is disabled, since the '${MessageJsonHandler.CANCEL_METHOD.methodName}' method has been registered explicitly." }
                }
            } else {
                LOG.warning { "Missing 'params' attribute of cancel notification." }
            }
        }
        return false
    }

    private fun isOptional(message: IncomingMessage): Boolean =
        message.method.startsWith("$/")

    private suspend fun handleRequest(requestMessage: RequestMessage) {
        val messageId = requestMessage.id

        println("handleRequest: $requestMessage")

        try {
            val params = jsonHandler.deserializeParams(requestMessage)
            // Forward the request to the local endpoint
            val resultDeferred = supervisorScope {
                async(currentCoroutineContext()) {
                    localEndpoint.request(
                        requestMessage.method,
                        params
                    )
                }
            }
            receivedRequestMap[messageId] = resultDeferred
            val serializedResult = jsonHandler.serializeResult(requestMessage.method, resultDeferred.await())
            out.send(ResponseMessage.Result(messageId, serializedResult))
        } catch (e: CancellationException) {
            println("handleRequest: CancellationException")
            val message =
                "The request (id: " + messageId + ", method: '" + requestMessage.method + "') has been cancelled"
            val errorObject =
                ResponseError(ResponseErrorCode.RequestCancelled.code, message, null)

            val response = ResponseMessage.Error(messageId, errorObject)
            out.send(response)
        } catch (e: MessageIssueException) {
            if (e.issue is NoSuchMethod && isOptional(requestMessage)) {
                LOG.info { "Ignoring optional request: $requestMessage" }
            } else {
                logMessageIssue(requestMessage, e)
                // There was an issue with the request - reply with an error response
            }
            out.send(ResponseMessage.Error(messageId, e.issue.toErrorResponse()))
        } catch (throwable: Throwable) {
            // The local endpoint has failed handling the request - reply with an error response
            val errorObject: ResponseError = exceptionHandler.apply(throwable)
            out.send(ResponseMessage.Error(messageId, errorObject))
            if (throwable is Error) throw throwable else return
        } finally {
            val request = receivedRequestMap.remove(messageId) // TODO: check
            if (request != null) {
                println("handleRequest: finally")
                request.cancel()
            }

        }

    }


    override fun resolveMethod(requestId: MessageId?): String? {
        val requestInfo =
            sentRequestMap[requestId]
        if (requestInfo != null) {
            return requestInfo.requestMessage.method
        }
        return null
    }

    companion object {
        private val LOG = Logger.getLogger(RemoteEndpoint::class.jvmName)
        private fun isCancellation(t: Throwable?): Boolean {
            return if (t is CompletionException) {
                isCancellation(t.cause)
            } else t is CancellationException
        }

        val DEFAULT_EXCEPTION_HANDLER: Function<Throwable, ResponseError> =
            Function<Throwable, ResponseError> { throwable: Throwable ->
                if (throwable is ResponseErrorException) {
                    return@Function throwable.responseError
                } else if ((throwable is CompletionException || throwable is InvocationTargetException)
                    && throwable.cause is ResponseErrorException
                ) {
                    return@Function (throwable.cause as ResponseErrorException?)?.responseError
                } else {
                    return@Function fallbackResponseError("Internal error", throwable)
                }
            }

        private fun fallbackResponseError(header: String, throwable: Throwable): ResponseError {
            LOG.log(Level.SEVERE, header + ": " + throwable.message, throwable)
            val stackTrace = ByteArrayOutputStream()
            val stackTraceWriter = PrintWriter(stackTrace)
            throwable.printStackTrace(stackTraceWriter)
            stackTraceWriter.flush()
            val data = JsonPrimitive(stackTrace.toString())
            return ResponseError(ResponseErrorCode.InternalError.code, "$header.", data)
        }
    }
}
