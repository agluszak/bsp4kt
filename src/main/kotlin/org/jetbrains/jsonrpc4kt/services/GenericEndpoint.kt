package org.jetbrains.jsonrpc4kt.services

import org.jetbrains.jsonrpc4kt.ResponseErrorException
import org.jetbrains.jsonrpc4kt.messages.ResponseError
import org.jetbrains.jsonrpc4kt.messages.ResponseErrorCode
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.reflect.KClass
import kotlin.reflect.full.callSuspend
import kotlin.reflect.jvm.jvmName

/**
 * An endpoint that reflectively delegates to [JsonNotification] and
 * [JsonRequest] methods of one or more given delegate objects.
 */
class GenericEndpoint<T>(delegate: T) : org.jetbrains.jsonrpc4kt.Endpoint {
    private val requestHandlers = LinkedHashMap<String, (List<Any?>) -> suspend () -> Any?>()
    private val notificationHandlers = LinkedHashMap<String, (List<Any?>) -> Unit>()

    init {
        recursiveFindRpcMethods(delegate, HashSet())
    }

    private fun getArguments(methodInfo: AnnotationUtil.MethodInfo, args: List<Any?>): Array<Any?> {
        val argumentCount = args.size
        val parameterCount = methodInfo.method.parameters.size - 1 // -1 for the receiver
        val argsCorrectCount = if (argumentCount == parameterCount) {
            args
        } else if (argumentCount < parameterCount) {
            // Take as many as there are and fill the rest with nulls
            val missing = parameterCount - argumentCount
            args + List(missing) { null }
        } else {
            // Take as many as there are parameters and log a warning for the rest
            args.take(parameterCount).also {
                val unexpectedArgs = args.drop(parameterCount)
                LOG.warning("Unexpected additional params '$unexpectedArgs' for '${methodInfo.method}' are ignored")
            }
        }
        return argsCorrectCount.toTypedArray()
    }


    private fun recursiveFindRpcMethods(current: T, visited: MutableSet<KClass<*>>) {
        AnnotationUtil.findRpcMethods(current!!::class, visited) { methodInfo ->
            if (methodInfo.isNotification) {
                val handler: (List<Any?>) -> Unit = { args: List<Any?> ->
                    val arguments = getArguments(methodInfo, args)
                    methodInfo.method.call(current, *arguments)
                }
                check(
                    notificationHandlers.put(
                        methodInfo.name,
                        handler
                    ) == null
                ) { "Multiple methods for name " + methodInfo.name }
            } else {
                val handler = { args: List<Any?> ->
                    suspend {
                        val arguments = getArguments(methodInfo, args)
                        methodInfo.method.callSuspend(current, *arguments)
                    }
                }
                check(
                    requestHandlers.put(
                        methodInfo.name,
                        handler
                    ) == null
                ) { "Multiple methods for name " + methodInfo.name }
            }
        }
    }

    override suspend fun request(method: String, params: List<Any?>): Any? {
        // Check the registered method handlers
        val handler = requestHandlers[method]
        if (handler != null) {
            return handler(params).invoke()
        }

        // TODO: remove
        // Create a log message about the unsupported method
        val message = "Unsupported request method: $method"
        if (isOptionalMethod(method)) {
            LOG.log(Level.INFO, message)
            return null
        }
        LOG.log(Level.WARNING, message)
        val error = ResponseError(ResponseErrorCode.MethodNotFound.code, message, null)
        throw ResponseErrorException(error)
    }

    override fun notify(method: String, params: List<Any?>) {
        // Check the registered method handlers
        val handler = notificationHandlers[method]
        if (handler != null) {
            handler(params)
            return
        }

        if (isOptionalMethod(method)) {
            LOG.info { "Unsupported optional notification method: $method" }
        } else {
            LOG.warning("Unsupported notification method: $method")
        }
    }

    private fun isOptionalMethod(method: String?): Boolean {
        return method != null && method.startsWith("$/")
    }

    companion object {
        private val LOG = Logger.getLogger(GenericEndpoint::class.jvmName)
    }
}
