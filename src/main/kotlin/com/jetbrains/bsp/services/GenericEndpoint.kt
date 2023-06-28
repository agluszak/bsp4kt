package com.jetbrains.bsp.services

import com.jetbrains.bsp.Endpoint
import com.jetbrains.bsp.ResponseErrorException
import com.jetbrains.bsp.messages.ResponseError
import com.jetbrains.bsp.messages.ResponseErrorCode
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import java.util.logging.Level
import java.util.logging.Logger

/**
 * An endpoint that reflectively delegates to [JsonNotification] and
 * [JsonRequest] methods of one or more given delegate objects.
 */
class GenericEndpoint(vararg delegates: Any) : Endpoint {
    private val delegates: Collection<Any>
    private val methodHandlers = LinkedHashMap<String, Function<Any?, CompletableFuture<*>?>>()

    init {
        assert(delegates.isNotEmpty())
        this.delegates = delegates.asList()
        for (delegate in this.delegates) {
            recursiveFindRpcMethods(delegate, HashSet(), HashSet())
        }
    }

     fun recursiveFindRpcMethods(current: Any, visited: MutableSet<Class<*>>, visitedForDelegate: MutableSet<Class<*>>) {
        AnnotationUtil.findRpcMethods(current.javaClass, visited) { methodInfo ->
            val handler =
                Function { arg: Any? ->
                    try {
                        val method: Method = methodInfo.method
                        val arguments = getArguments(method, arg)
                        return@Function method.invoke(current, *arguments) as CompletableFuture<*>?
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
        AnnotationUtil.findDelegateSegments(current.javaClass, visitedForDelegate) { method ->
            try {
                val delegate: Any = method.invoke(current)
                if (delegate != null) {
                    recursiveFindRpcMethods(delegate, visited, visitedForDelegate)
                } else {
                    LOG.fine("A delegate object is null, jsonrpc methods of '$method' are ignored")
                }
            } catch (e: InvocationTargetException) {
                throw RuntimeException(e)
            } catch (e: IllegalAccessException) {
                throw RuntimeException(e)
            }
        }
    }

     fun getArguments(method: Method, arg: Any?): Array<Any?> {
        val parameterCount = method.parameterCount
        if (parameterCount == 0) {
            if (arg != null) {
                LOG.warning("Unexpected params '$arg' for '$method' is ignored")
            }
            return NO_ARGUMENTS
        }
        if (arg is List<*>) {
            val argumentCount = arg.size
            if (argumentCount == parameterCount) {
                return arg.toTypedArray()
            }
            if (argumentCount > parameterCount) {
                val unexpectedArguments = arg.stream().skip(parameterCount.toLong())
                val unexpectedParams = unexpectedArguments.map { a: Any? -> "'$a'" }
                    .reduce { a: String, a2: String -> "$a, $a2" }.get()
                LOG.warning("Unexpected params $unexpectedParams for '$method' is ignored")
                return arg.subList(0, parameterCount).toTypedArray()
            }
            return arg.toTypedArray<Any?>().copyOf(parameterCount)
        }
        val arguments = arrayOfNulls<Any>(parameterCount)
        arguments[0] = arg
        return arguments
    }

    override fun request(method: String, parameter: Any?): CompletableFuture<*> {
        // Check the registered method handlers
        val handler = methodHandlers[method]
        if (handler != null) {
            return handler.apply(parameter)!!
        }

        // Ask the delegate objects whether they can handle the request generically
        val futures: MutableList<CompletableFuture<*>> = ArrayList(delegates.size)
        for (delegate in delegates) {
            if (delegate is Endpoint) {
                futures.add((delegate as Endpoint).request(method, parameter))
            }
        }
        if (!futures.isEmpty()) {
            return CompletableFuture.anyOf(*futures.toTypedArray<CompletableFuture<*>>())
        }

        // Create a log message about the unsupported method
        val message = "Unsupported request method: $method"
        if (isOptionalMethod(method)) {
            LOG.log(Level.INFO, message)
            return CompletableFuture.completedFuture<Any?>(null)
        }
        LOG.log(Level.WARNING, message)
        val exceptionalResult: CompletableFuture<*> = CompletableFuture<Any>()
        val error = ResponseError(ResponseErrorCode.MethodNotFound.value, message, null)
        exceptionalResult.completeExceptionally(ResponseErrorException(error))
        return exceptionalResult
    }

    override fun notify(method: String, parameter: Any?) {
        // Check the registered method handlers
        val handler = methodHandlers[method]
        if (handler != null) {
            handler.apply(parameter)
            return
        }

        // Ask the delegate objects whether they can handle the notification generically
        var notifiedDelegates = 0
        for (delegate in delegates) {
            if (delegate is Endpoint) {
                (delegate as Endpoint).notify(method, parameter)
                notifiedDelegates++
            }
        }
        if (notifiedDelegates == 0) {
            // Create a log message about the unsupported method
            val message = "Unsupported notification method: $method"
            if (isOptionalMethod(method)) {
                LOG.log(Level.INFO, message)
            } else {
                LOG.log(Level.WARNING, message)
            }
        }
    }

     fun isOptionalMethod(method: String?): Boolean {
        return method != null && method.startsWith("$/")
    }

    companion object {
        private val LOG = Logger.getLogger(GenericEndpoint::class.java.name)
        private val NO_ARGUMENTS = arrayOf<Any?>()
    }
}
