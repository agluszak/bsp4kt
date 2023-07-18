package com.jetbrains.bsp.services

import com.jetbrains.bsp.Endpoint
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.*
import kotlin.reflect.KClass


/**
 * A Proxy that wraps an [Endpoint] in one or more service interfaces, i.e. interfaces
 * containing [JsonNotification] and [JsonRequest] methods.
 */
class EndpointProxy<Remote : Any>(private val delegate: Endpoint, remoteInterface: KClass<Remote>) : InvocationHandler {
    private val object_equals: Method
    private val object_hashCode: Method
    private val object_toString: Method
    private val methodInfos: LinkedHashMap<String, AnnotationUtil.MethodInfo>

    init {
        try {
            object_equals = Any::class.java.getDeclaredMethod("equals", Any::class.java)
            object_hashCode = Any::class.java.getDeclaredMethod("hashCode")
            object_toString = Any::class.java.getDeclaredMethod("toString")
        } catch (exception: NoSuchMethodException) {
            throw RuntimeException(exception)
        } catch (exception: SecurityException) {
            throw RuntimeException(exception)
        }
        methodInfos = LinkedHashMap<String, AnnotationUtil.MethodInfo>()
        AnnotationUtil.findRpcMethods(remoteInterface, HashSet()) { methodInfo ->
            check(
                methodInfos.put(
                    methodInfo.method.name, methodInfo
                ) == null
            ) { "Duplicate RPC method " + methodInfo.method }
        }
    }

    @Throws(Throwable::class)
    override fun invoke(proxy: Any, method: Method, args: Array<Any?>?): Any? {
        val args = args?.toList() ?: emptyList()
        val methodInfo: AnnotationUtil.MethodInfo? = methodInfos[method.name]
        if (methodInfo != null) {
            if (methodInfo.isNotification) {
                delegate.notify(methodInfo.name, args)
                return null
            }
            return delegate.request(methodInfo.name, args)
        }
        if (object_equals == method && args.size == 1) {
            if (args[0] != null) {
                try {
                    return this == Proxy.getInvocationHandler(args[0])
                } catch (_: IllegalArgumentException) {
                }
            }
            return this == args[0]
        }
        if (object_hashCode == method) {
            return this.hashCode()
        }
        return if (object_toString == method) {
            this.toString()
        } else method.invoke(delegate, args)
    }

    override fun toString(): String {
        return javaClass.simpleName + " for " + delegate.toString()
    }
}
