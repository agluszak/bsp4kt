package org.jetbrains.jsonrpc4kt

import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.Continuation


internal inline fun handleInvocationTargetException(action: () -> Any?): Any? = try {
    action()
} catch (e: InvocationTargetException) {
    throw e.cause!!
}

internal fun invokeSuspendFunction(
    continuation: Continuation<*>,
    suspendFunction: suspend () -> Any?,
): Any? = handleInvocationTargetException {
    @Suppress("UNCHECKED_CAST") (suspendFunction as (Continuation<*>) -> Any?)(continuation)
}

class ReflectionHelper {
}