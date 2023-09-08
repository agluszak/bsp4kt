package org.jetbrains.jsonrpc4kt.services

import com.jetbrains.jsonrpc4kt.Endpoint
import com.jetbrains.jsonrpc4kt.ResponseErrorException
import com.jetbrains.jsonrpc4kt.messages.ResponseError
import com.jetbrains.jsonrpc4kt.messages.ResponseErrorCode
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.jvmName

/**
 * An endpoint that reflectively delegates to [JsonNotification] and
 * [JsonRequest] methods of one or more given delegate objects.
 */
class GenericEndpoint<T>(delegate: T) : Endpoint {
    private val methodHandlers = LinkedHashMap<String, Function<List<Any?>, CompletableFuture<*>?>>()

    init {
        recursiveFindRpcMethods(delegate, HashSet())
    }

    private fun recursiveFindRpcMethods(current: T, visited: MutableSet<KClass<*>>) {
        AnnotationUtil.findRpcMethods(current!!::class, visited) { methodInfo ->
            val handler =
                Function { args: List<Any?> ->
                    try {
                        val method: KFunction<*> = methodInfo.method
                        val argumentCount = args.size
                        val parameterCount = method.parameters.size - 1 // -1 for the receiver
                        val arguments = if (argumentCount == parameterCount) {
                            args
                        } else if (argumentCount < parameterCount) {
                            // Take as many as there are and fill the rest with nulls
                            val missing = parameterCount - argumentCount
                            args + List(missing) { null }
                        } else {
                            // Take as many as there are parameters and log a warning for the rest
                            args.take(parameterCount).also {
                                val unexpectedArgs = args.drop(parameterCount)
                                LOG.warning("Unexpected additional params '$unexpectedArgs' for '$method' are ignored")
                            }
                        }

                        val result = method.call(current, *arguments.toTypedArray())
                        if (result is CompletableFuture<*>) {
                            return@Function result
                        } else {
                            return@Function null
                        }
                    } catch (e: InvocationTargetException) {
                        throw RuntimeException(e)
                    } catch (e: IllegalAccessException) {
                        throw RuntimeException(e)
                    }
                }
            check(
                methodHandlers.put(
                    methodInfo.name,
                    handler
                ) == null
            ) { "Multiple methods for name " + methodInfo.name }
        }
    }

    override fun request(method: String, params: List<Any?>): CompletableFuture<*> {
        // Check the registered method handlers
        val handler = methodHandlers[method]
        if (handler != null) {
            return handler.apply(params)!!
        }

        // TODO: remove
        // Create a log message about the unsupported method
        val message = "Unsupported request method: $method"
        if (isOptionalMethod(method)) {
            LOG.log(Level.INFO, message)
            return CompletableFuture.completedFuture<Any?>(null)
        }
        LOG.log(Level.WARNING, message)
        val exceptionalResult: CompletableFuture<*> = CompletableFuture<Any>()
        val error = ResponseError(ResponseErrorCode.MethodNotFound.code, message, null)
        exceptionalResult.completeExceptionally(ResponseErrorException(error))
        return exceptionalResult
    }

    override fun notify(method: String, params: List<Any?>) {
        // Check the registered method handlers
        val handler = methodHandlers[method]
        if (handler != null) {
            handler.apply(params)
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
