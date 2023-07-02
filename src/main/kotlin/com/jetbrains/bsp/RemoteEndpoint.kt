package com.jetbrains.bsp

import arrow.core.left
import com.jetbrains.bsp.json.MessageJsonHandler
import com.jetbrains.bsp.json.MethodProvider
import com.jetbrains.bsp.messages.*
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
    override fun notify(method: String, parameter: Any?) {
        val notificationMessage: NotificationMessage = NotificationMessage(method, parameter)
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
    override fun request(method: String, parameter: Any?): CompletableFuture<Any?> {
        val requestMessage: RequestMessage = createRequestMessage(method, parameter)
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

    protected fun createRequestMessage(method: String, parameter: Any?): RequestMessage {
        val id = nextRequestId.incrementAndGet().left()
        return RequestMessage(id, method, parameter)
    }

    protected fun sendCancelNotification(id: MessageId?) {
        if (id == null) {
            return
        }
        val cancelParams = CancelParams(id)
        notify(MessageJsonHandler.CANCEL_METHOD.methodName, cancelParams)
    }

    override fun consume(message: Message) {
        when (message) {
            is NotificationMessage -> {
                val notificationMessage: NotificationMessage = message as NotificationMessage
                handleNotification(notificationMessage)
            }

            is RequestMessage -> {
                val requestMessage: RequestMessage = message as RequestMessage
                handleRequest(requestMessage)
            }

            is ResponseMessage -> {
                val responseMessage: ResponseMessage = message as ResponseMessage
                handleResponse(responseMessage)
            }

            else -> {
                LOG.log(Level.WARNING, "Unkown message type.", message)
            }
        }
    }

    protected fun handleResponse(responseMessage: ResponseMessage) {
        var requestInfo: PendingRequestInfo?
        synchronized(sentRequestMap) { requestInfo = sentRequestMap.remove(responseMessage.id) }
        if (requestInfo == null) {
            // We have no pending request information that matches the id given in the response
            LOG.log(
                Level.WARNING,
                "Unmatched response message: $responseMessage"
            )
        } else if (responseMessage.error != null) {
            // The remote service has replied with an error
            requestInfo!!.future.completeExceptionally(ResponseErrorException(responseMessage.error))
        } else {
            // The remote service has replied with a result object
            requestInfo!!.future.complete(responseMessage.result)
        }
    }

    protected fun handleNotification(notificationMessage: NotificationMessage) {
        if (!handleCancellation(notificationMessage)) {
            // Forward the notification to the local endpoint
            try {
                localEndpoint.notify(notificationMessage.method, notificationMessage.params)
            } catch (exception: Exception) {
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
    protected fun handleCancellation(notificationMessage: NotificationMessage): Boolean {
        if (MessageJsonHandler.CANCEL_METHOD.methodName == notificationMessage.method) {
            val cancelParams = notificationMessage.params
            if (cancelParams != null) {
                if (cancelParams is CancelParams) {
                    synchronized(receivedRequestMap) {
                        val id = cancelParams.id
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

    protected fun handleRequest(requestMessage: RequestMessage) {
        val future: CompletableFuture<*>
        try {
            // Forward the request to the local endpoint
            future = localEndpoint.request(requestMessage.method, requestMessage.params)
        } catch (throwable: Throwable) {
            // The local endpoint has failed handling the request - reply with an error response
            var errorObject: ResponseError = exceptionHandler.apply(throwable)
            if (errorObject == null) {
                errorObject =
                    fallbackResponseError("Internal error. Exception handler provided no error object", throwable)
            }
            out.consume(createErrorResponseMessage(requestMessage, errorObject))
            if (throwable is Error) throw throwable else return
        }
        val messageId = requestMessage.id
        synchronized(receivedRequestMap) { receivedRequestMap.put(messageId, future) }
        future.thenAccept { result: Any? ->
            // Reply with the result object that was computed by the local endpoint
            out.consume(createResultResponseMessage(requestMessage, result))
        }.exceptionally { t: Throwable ->
            // The local endpoint has failed computing a result - reply with an error response
            val responseMessage: ResponseMessage
            if (isCancellation(t)) {
                val message =
                    "The request (id: " + messageId + ", method: '" + requestMessage.method + "') has been cancelled"
                val errorObject = ResponseError(ResponseErrorCode.RequestCancelled.value, message, null)
                responseMessage = createErrorResponseMessage(requestMessage, errorObject)
            } else {
                var errorObject: ResponseError = exceptionHandler.apply(t)
                if (errorObject == null) {
                    errorObject = fallbackResponseError(
                        "Internal error. Exception handler provided no error object",
                        t
                    )
                }
                responseMessage = createErrorResponseMessage(requestMessage, errorObject)
            }
            out.consume(responseMessage)
            null
        }.thenApply<Any?> { obj: Void? ->
            synchronized(receivedRequestMap) { receivedRequestMap.remove(messageId) }
            null
        }
    }

    override fun handle(message: Message, issues: List<MessageIssue>) {
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

    protected fun logIssues(message: Message, issues: List<MessageIssue>) {
        for (issue in issues) {
            val logMessage = "Issue found in " + message.javaClass.simpleName + ": " + issue.text
            LOG.log(Level.WARNING, logMessage, issue.cause)
        }
    }

    protected fun handleRequestIssues(requestMessage: RequestMessage, issues: List<MessageIssue>) {
        val errorObject =
        if (issues.size == 1) {
            val issue: MessageIssue = issues[0]
            ResponseError(issue.code, issue.text, issue.cause)
        } else {
            val message = "Multiple issues were found in '" + requestMessage.method + "' request."
            ResponseError(ResponseErrorCode.InvalidRequest.value, message, issues)
        }
        out.consume(createErrorResponseMessage(requestMessage, errorObject))
    }

    protected fun handleResponseIssues(responseMessage: ResponseMessage, issues: List<MessageIssue>) {
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

    protected fun createResultResponseMessage(requestMessage: RequestMessage, result: Any?): ResponseMessage {
        return ResponseMessage(requestMessage.id, result)
    }

    protected fun createErrorResponseMessage(
        requestMessage: RequestMessage,
        errorObject: ResponseError?
    ): ResponseMessage {
        return ResponseMessage(requestMessage.id, error = errorObject)
    }

    protected fun isCancellation(t: Throwable?): Boolean {
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
        private val LOG = Logger.getLogger(RemoteEndpoint::class.java.name)
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
            val data = stackTrace.toString()

            return ResponseError(ResponseErrorCode.InternalError.value, "$header.", data)
        }
    }
}
