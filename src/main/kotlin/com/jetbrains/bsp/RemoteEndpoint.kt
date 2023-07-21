package com.jetbrains.bsp

import com.jetbrains.bsp.json.MessageJsonHandler
import com.jetbrains.bsp.json.MethodProvider
import com.jetbrains.bsp.messages.*
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
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
    private val out: MessageConsumer,
    private val localEndpoint: Endpoint,
    private val jsonHandler: MessageJsonHandler,
    private val exceptionHandler: Function<Throwable, ResponseError> = DEFAULT_EXCEPTION_HANDLER
) :
    Endpoint, MessageConsumer, MessageIssueHandler, MethodProvider {
    private val nextRequestId = AtomicInteger()
    private val sentRequestMap: MutableMap<MessageId, PendingRequestInfo> = LinkedHashMap()
    private val receivedRequestMap: MutableMap<MessageId, CompletableFuture<*>> = LinkedHashMap()

    /**
     * Information about requests that have been sent and for which no response has been received yet.
     */
    private data class PendingRequestInfo(
        val requestMessage: RequestMessage,
        val future: CompletableFuture<Any?>
    )

    /**
     * Send a notification to the remote endpoint.
     */
    override fun notify(method: String, params: List<Any?>) {
        val serializedParams = jsonHandler.serializeParams(method, params)
        val notificationMessage = NotificationMessage(method, serializedParams)
        try {
            out.consume(notificationMessage)
        } catch (exception: Exception) {
            val logLevel = if (JsonRpcException.indicatesStreamClosed(exception)) Level.INFO else Level.WARNING
            LOG.log(logLevel, "Failed to send notification message.", exception)
        }
    }

    /**
     * Send a request to the remote endpoint.
     */
    override fun request(method: String, params: List<Any?>): CompletableFuture<Any?> {
        val serializedParams = jsonHandler.serializeParams(method, params)
        val requestMessage: RequestMessage = createRequestMessage(method, serializedParams)
        val result: CompletableFuture<Any?> = object : CompletableFuture<Any?>() {
            override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
                sendCancelNotification(requestMessage.id)
                return super.cancel(mayInterruptIfRunning)
            }
        }
        synchronized(sentRequestMap) {
            // Store request information so it can be handled when the response is received
            sentRequestMap.put(
                requestMessage.id,
                PendingRequestInfo(requestMessage, result)
            )
        }
        try {
            // Send the request to the remote service
            out.consume(requestMessage)
        } catch (exception: Exception) {
            // The message could not be sent, e.g. because the communication channel was closed
            result.completeExceptionally(exception)
        }
        return result
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

    override fun consume(message: Message) {
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

    private fun handleResponse(responseMessage: ResponseMessage) {
        var requestInfo: PendingRequestInfo?
        synchronized(sentRequestMap) { requestInfo = sentRequestMap.remove(responseMessage.id) }
        if (requestInfo == null) {
            // We have no pending request information that matches the id given in the response
            LOG.log(
                Level.WARNING,
                "Unmatched response message: $responseMessage"
            )
        } else when (responseMessage) {
            is ResponseMessage.Error ->
                // The remote service has replied with an error
                requestInfo!!.future.completeExceptionally(ResponseErrorException(responseMessage.error))

            is ResponseMessage.Result -> {
                // The remote service has replied with a result object
                val deserialized = jsonHandler.deserializeResult(requestInfo!!.requestMessage.method, responseMessage.result)
                requestInfo!!.future.complete(deserialized)
            }
        }
    }

    private fun handleNotification(notificationMessage: NotificationMessage) {
        val params = jsonHandler.deserializeParams(notificationMessage.method, notificationMessage.params)
        if (!handleCancellation(notificationMessage, params)) {
            // Forward the notification to the local endpoint
            try {
                localEndpoint.notify(notificationMessage.method, params)
            } catch (exception: Exception) { // TODO: error handling
                LOG.log(
                    Level.WARNING,
                    "Notification threw an exception: $notificationMessage", exception
                )
            }
        }
    }

    /**
     * Cancellation is handled inside this class and not forwarded to the local endpoint.
     *
     * @return `true` if the given message is a cancellation notification,
     * `false` if it can be handled by the local endpoint
     */
    private fun handleCancellation(notificationMessage: NotificationMessage, cancelParams: List<Any?>): Boolean {
        if (MessageJsonHandler.CANCEL_METHOD.methodName == notificationMessage.method) {
            val cancelParam = cancelParams[0]
            if (cancelParam != null) {
                if (cancelParam is CancelParams) {
                    synchronized(receivedRequestMap) {
                        val id = cancelParam.id
                        val future = receivedRequestMap[id]
                        future?.cancel(true) ?: LOG.warning("Unmatched cancel notification for request id $id")
                    }
                    return true
                } else {
                    LOG.warning("Cancellation support is disabled, since the '${MessageJsonHandler.CANCEL_METHOD.methodName}' method has been registered explicitly.")
                }
            } else {
                LOG.warning("Missing 'params' attribute of cancel notification.")
            }
        }
        return false
    }

    private fun handleRequest(requestMessage: RequestMessage) {
        val messageId = requestMessage.id
        val params = jsonHandler.deserializeParams(requestMessage.method, requestMessage.params)
        val future: CompletableFuture<*>
        try {
            // Forward the request to the local endpoint
            future = localEndpoint.request(requestMessage.method, params)
        } catch (throwable: Throwable) { // TODO error handling
            // The local endpoint has failed handling the request - reply with an error response
            val errorObject: ResponseError = exceptionHandler.apply(throwable)
            out.consume(ResponseMessage.Error(messageId, errorObject))
            if (throwable is Error) throw throwable else return
        }

        synchronized(receivedRequestMap) { receivedRequestMap.put(messageId, future) }
        future.thenAccept { result: Any? ->
            val serializedResult = jsonHandler.serializeResult(requestMessage.method, result)
            // Reply with the result object that was computed by the local endpoint
            out.consume(ResponseMessage.Result(messageId, serializedResult))
        }.exceptionally { t: Throwable ->
            // The local endpoint has failed computing a result - reply with an error response
            val responseMessage = if (isCancellation(t)) {
                val message =
                    "The request (id: " + messageId + ", method: '" + requestMessage.method + "') has been cancelled"
                val errorObject = ResponseError(ResponseErrorCode.RequestCancelled.value, message, null)
                ResponseMessage.Error(messageId, errorObject)
            } else {
                val errorObject: ResponseError = exceptionHandler.apply(t)
                ResponseMessage.Error(messageId, errorObject)
            }
            out.consume(responseMessage)
            null
        }.thenApply<Any?> {
            synchronized(receivedRequestMap) { receivedRequestMap.remove(messageId) }
            null
        }
    }

    override fun handleIssues(message: Message?, issues: List<MessageIssue>) {
        require(issues.isNotEmpty()) { "The list of issues must not be empty." }
        when (message) {
            is RequestMessage -> {
                handleRequestIssues(message, issues)
            }

            is ResponseMessage -> {
                handleResponseIssues(message, issues)
            }

            else -> {
                logIssues(message, issues)
            }
        }
    }

    private fun logIssues(message: Message?, issues: List<MessageIssue>) {
        val messageName = if (message == null) "message" else message.javaClass.simpleName
        for (issue in issues) {
            val logMessage = "Issue found in " + messageName + ": " + issue.text
            LOG.log(Level.WARNING, logMessage, issue.cause)
        }
    }

    private fun handleRequestIssues(requestMessage: RequestMessage, issues: List<MessageIssue>) {
        val requestId = requestMessage.id
        val errorObject =
            if (issues.size == 1) {
                val issue: MessageIssue = issues[0]
                val serializedIssue = jsonHandler.serialize(issue.cause)
                ResponseError(issue.code, issue.text, serializedIssue)
            } else {
                val message = "Multiple issues were found in '" + requestMessage.method + "' request."
                val serializedIssues = jsonHandler.serialize(issues)
                ResponseError(ResponseErrorCode.InvalidRequest.value, message, serializedIssues)
            }
        out.consume(ResponseMessage.Error(requestId, errorObject))
    }

    private fun handleResponseIssues(responseMessage: ResponseMessage, issues: List<MessageIssue>) {
        var requestInfo: PendingRequestInfo?
        synchronized(sentRequestMap) { requestInfo = sentRequestMap.remove(responseMessage.id) }
        if (requestInfo == null) {
            // We have no pending request information that matches the id given in the response
            LOG.log(
                Level.WARNING,
                "Unmatched response message: $responseMessage"
            )
            logIssues(responseMessage, issues)
        } else {
            requestInfo!!.future.completeExceptionally(MessageIssueException(responseMessage, issues))
        }
    }

    private fun isCancellation(t: Throwable?): Boolean {
        return if (t is CompletionException) {
            isCancellation(t.cause)
        } else t is CancellationException
    }

    override fun resolveMethod(requestId: MessageId?): String? {
        synchronized(sentRequestMap) {
            val requestInfo =
                sentRequestMap[requestId]
            if (requestInfo != null) {
                return requestInfo.requestMessage.method
            }
        }
        return null
    }

    companion object {
        private val LOG = Logger.getLogger(RemoteEndpoint::class.jvmName)
        val DEFAULT_EXCEPTION_HANDLER: Function<Throwable, ResponseError> =
            Function<Throwable, ResponseError> { throwable: Throwable ->
                if (throwable is ResponseErrorException) {
                    return@Function (throwable as ResponseErrorException).responseError
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
            return ResponseError(ResponseErrorCode.InternalError.value, "$header.", data)
        }
    }
}
