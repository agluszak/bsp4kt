package com.jetbrains.bsp.services

import com.jetbrains.bsp.Endpoint
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.*


/**
 * A Proxy that wraps an [Endpoint] in one or more service interfaces, i.e. interfaces
 * containing [JsonNotification] and [JsonRequest] methods.
 */
class EndpointProxy(private val delegate: Endpoint, interfaces: Collection<Class<*>>) : InvocationHandler {
    private var object_equals: Method? = null
    private var object_hashCode: Method? = null
    private var object_toString: Method? = null
    private val methodInfos: LinkedHashMap<String, AnnotationUtil.MethodInfo>
    private val delegatedSegments: LinkedHashMap<String, AnnotationUtil.DelegateInfo>

    constructor(delegate: Endpoint, interface_: Class<*>) : this(delegate, listOf<Class<*>>(interface_))

    init {
        require(!interfaces.isEmpty()) { "interfaces must not be empty." }
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
        delegatedSegments = LinkedHashMap<String, AnnotationUtil.DelegateInfo>()
        for (interf in interfaces) {
            AnnotationUtil.findRpcMethods(interf, HashSet()) { methodInfo ->
                check(
                    methodInfos.put(
                        methodInfo.method.name, methodInfo
                    ) == null
                ) { "Duplicate RPC method " + methodInfo.method }
            }
            AnnotationUtil.findDelegateSegments(interf, HashSet<Class<*>>()) { method ->
                val delegateProxy: Any = ServiceEndpoints.toServiceObject(delegate, method.returnType)
                val info = AnnotationUtil.DelegateInfo(method, delegateProxy)
                check(delegatedSegments.put(method.name, info) == null) { "Duplicate RPC method $method" }
            }
        }
    }

    @Throws(Throwable::class)
    override fun invoke(proxy: Any, method: Method, args: Array<Any>?): Any? {
        val args: Array<out Any?> = args ?: arrayOfNulls<Any>(0)
        val methodInfo: AnnotationUtil.MethodInfo? = methodInfos[method.name]
        if (methodInfo != null) {
            val params = getParams(args, methodInfo)
            if (methodInfo.isNotification) {
                delegate.notify(methodInfo.name, params)
                return null
            }
            return delegate.request(methodInfo.name, params)
        }
        val delegateInfo: AnnotationUtil.DelegateInfo? = delegatedSegments[method.name]
        if (delegateInfo != null) {
            return delegateInfo.delegate
        }
        if (object_equals == method && args.size == 1) {
            if (args[0] != null) {
                try {
                    return this == Proxy.getInvocationHandler(args[0])
                } catch (exception: IllegalArgumentException) {
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

    protected fun getParams(args: Array<out Any?>, methodInfo: AnnotationUtil.MethodInfo?): Any? {
        if (args.isEmpty()) {
            return null
        }
        return if (args.size == 1) {
            args[0]
        } else listOf(*args)
    }

    override fun toString(): String {
        return javaClass.simpleName + " for " + delegate.toString()
    }
}
