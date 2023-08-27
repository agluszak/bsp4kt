package org.jetbrains.jsonrpc4kt

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.jsonrpc4kt.json.MessageJsonHandler
import org.jetbrains.jsonrpc4kt.json.MethodProvider
import org.jetbrains.jsonrpc4kt.messages.*
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
    private val localEndpoint: org.jetbrains.jsonrpc4kt.Endpoint,
    private val jsonHandler: MessageJsonHandler,
    private val coroutineScope: CoroutineScope,
    private val exceptionHandler: Function<Throwable, ResponseError> = DEFAULT_EXCEPTION_HANDLER,
) : org.jetbrains.jsonrpc4kt.Endpoint, MethodProvider {
    private val nextRequestId = AtomicInteger()
    private val sentRequestMap: MutableMap<MessageId, PendingRequestInfo> = ConcurrentHashMap()
    private val sentRequestMapMutex = Mutex()
    private val receivedRequestMap: MutableMap<MessageId, Deferred<*>> = ConcurrentHashMap()
    private val receivedRequestMapMutex = Mutex()
    private val waker = Channel<Unit>()
    private val messagesBeingHandled = AtomicInteger()
    private lateinit var localEndpointSupervisor: Job

    /**
     * Information about requests that have been sent and for which no response has been received yet.
     */
    private data class PendingRequestInfo(
        val requestMessage: RequestMessage,
        val future: CompletableDeferred<Any?>
    )

    fun start(coroutineScope: CoroutineScope): Job = coroutineScope.launch {
        println("remote endpoint starting listening")
        localEndpointSupervisor = SupervisorJob(this.coroutineContext.job)
        try {
            for (message in `in`) {
                startHandling()
                launch {
                    println("remote endpoint received message $message")
                    try {
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
                    } finally {
                        println("endpoint start finally " + this.coroutineContext.job + this.coroutineContext.job.children.toList() + localEndpointSupervisor.children.toList())
                        finishHandling()
                    }
                }
            }

            println("between stopping")


            while (true) {
                println("endpoint woke to see if all messages have been handled")
                if (messagesBeingHandled.get() == 0) {
                    println("remote endpoint: all messages handled, closing output")
                    localEndpointSupervisor.cancelAndJoin()
                    waker.close()
                    break
                }
                waker.receive()
            }
            println("remote endpoint: done" + this.coroutineContext.job + this.coroutineContext.job.children.toList())
        } finally {
            localEndpointSupervisor.cancelAndJoin()
            out.close()
        }
    }

    /**
     * Send a notification to the remote endpoint.
     */
    override fun notify(method: String, params: List<Any?>) {
        coroutineScope.launch {
            try {
                val serializedParams = jsonHandler.serializeParams(method, params)
                val notificationMessage = NotificationMessage(method, serializedParams)

                out.send(notificationMessage)
            } catch (e: MessageIssueException) {
                LOG.log(Level.WARNING, "Error while processing the message", e)
            } catch (e: Exception) {
                val level = if (org.jetbrains.jsonrpc4kt.JsonRpcException.indicatesStreamClosed(e)) {
                    Level.INFO
                } else {
                    Level.WARNING
                }
                LOG.log(level, "Failed to send notification message", e)
            }
        }
    }


    /**
     * Send a request to the remote endpoint.
     */
    override suspend fun request(method: String, params: List<Any?>): Any? {

        val resultDeferred: CompletableDeferred<Any?> = CompletableDeferred()
        val id = MessageId.NumberId(nextRequestId.incrementAndGet())

        try {
            println("REQUEST starting ${currentCoroutineContext()}")
            val serializedParams = jsonHandler.serializeParams(method, params)
            val requestMessage = RequestMessage(id, method, serializedParams)

            // Store request information so it can be handled when the response is received
            sentRequestMap[id] = PendingRequestInfo(requestMessage, resultDeferred)

            // Send the request to the remote service
            out.send(requestMessage)
            println("REQUEST sent")
            val result = resultDeferred.await()
            println("REQUEST received result")
            return result
        } catch (exception: CancellationException) {
            println("REQUEST got cancelled")
            sendCancelNotification(id)
            resultDeferred.completeExceptionally(exception)
            throw exception
        } catch (exception: Exception) {
            println("REQUEST got exception $exception")
            resultDeferred.completeExceptionally(exception)
            throw exception
        }
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
            is ResponseMessage.Error -> {
                // The remote service has replied with an error
                println("RESPONSE got error")
                requestInfo.future.completeExceptionally(ResponseErrorException(responseMessage.error))
            }

            is ResponseMessage.Result -> {
                // The remote service has replied with a result object
                println("RESPONSE got result")
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

    private fun startHandling() {
        messagesBeingHandled.incrementAndGet()
    }

    private fun finishHandling() {
        messagesBeingHandled.decrementAndGet()
        waker.trySend(Unit)
        println("finished handling")
    }

    private fun handleNotification(notificationMessage: NotificationMessage) {
        println("handleNotification : $notificationMessage")
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
                    println("pierdololo")
                    println("receivedRequestMap: $receivedRequestMap")
                    val future = receivedRequestMap[id]
                    println("handleCancellation: $id, $future")
                    println("receivedRequestMap: $receivedRequestMap")
                    println("sentRequestMap: $sentRequestMap")
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

        println("handleRequest: $requestMessage ${currentCoroutineContext()}")
        try {
            val params = jsonHandler.deserializeParams(requestMessage)
            // Forward the request to the local endpoint
            val resultDeferred = coroutineScope.async(localEndpointSupervisor, start = CoroutineStart.LAZY) {
                println("before local endpoint request + $this")

                try {
                    val res = handleInvocationTargetException {
                        localEndpoint.request(
                            requestMessage.method,
                            params
                        )
                    }
                    println("INSIDE done ${this.coroutineContext.job} ${this.coroutineContext.job.children.toList()}")

                    res
                } catch (e: Exception) {
                    println("local endpoint request exception: $e ${e.message}")

                    throw e
                }
            }
            println("handleRequest: $resultDeferred")
            receivedRequestMap[messageId] = resultDeferred
            println("handleRequest: $receivedRequestMap")
            resultDeferred.start()

            val result = resultDeferred.await()
            val serializedResult =
                jsonHandler.serializeResult(requestMessage.method, result)
            println("handleRequest: result sent $result")
            out.send(ResponseMessage.Result(messageId, serializedResult))
        } catch (e: CancellationException) {
            println("handleRequest: CancellationException")
            val message =
                "The request (id: " + messageId + ", method: '" + requestMessage.method + "') has been cancelled"
            val errorObject =
                ResponseError(ResponseErrorCode.RequestCancelled.code, message, null)

            val response = ResponseMessage.Error(messageId, errorObject)
            println("handleRequest: before send cancellation")
            out.send(response)
            println("handleRequest: CancellationException: $response")
        } catch (e: MessageIssueException) {
            if (e.issue is NoSuchMethod && isOptional(requestMessage)) {
                LOG.info { "Ignoring optional request: $requestMessage" }
            } else {
                logMessageIssue(requestMessage, e)
                // There was an issue with the request - reply with an error response
            }
            out.send(ResponseMessage.Error(messageId, e.issue.toErrorResponse()))
        } catch (throwable: Throwable) {
            println("handleRequest: throwable $throwable")
            // The local endpoint has failed handling the request - reply with an error response
            val errorObject: ResponseError = exceptionHandler.apply(throwable)
            println("handleRequest: throwable in try, ${out.isClosedForSend}, $errorObject")
            out.send(ResponseMessage.Error(messageId, errorObject))
            println("handleRequest: throwable in try sent")
            if (throwable is Error) throw throwable else return
        } finally {
            val request = receivedRequestMap.remove(messageId) // TODO: check

            if (request != null) {
                println("handleRequest: finally")
                request.cancel()
                println("handleRequest: finallycancelled")
            }

        }
        println("handleRequest FINISHING")
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
