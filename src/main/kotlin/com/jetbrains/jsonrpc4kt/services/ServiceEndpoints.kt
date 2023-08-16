package com.jetbrains.jsonrpc4kt.services


import com.jetbrains.jsonrpc4kt.Endpoint
import com.jetbrains.jsonrpc4kt.json.JsonRpcMethod
import java.lang.reflect.Proxy
import kotlin.reflect.KClass


object ServiceEndpoints {
    /**
     * Wraps a given [Endpoint] in the given service interface.
     *
     * @return the wrapped service object
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> toServiceObject(endpoint: Endpoint, interface_: KClass<T>): T {
        val interfArray = arrayOf<Class<*>>(interface_.java, Endpoint::class.java)
        val invocationHandler = EndpointProxy(endpoint, interface_)
        return Proxy.newProxyInstance(interface_.java.classLoader, interfArray, invocationHandler) as T
    }

    /**
     * Wraps a given object with service annotations behind an [Endpoint] interface.
     *
     * @return the wrapped service endpoint
     */
    fun <T : Any> toEndpoint(serviceObject: T): Endpoint {
        return GenericEndpoint(serviceObject)
    }

    /**
     * Finds all Json RPC methods on a given class.
     *
     * @return the supported JsonRpcMethods
     */
    fun getSupportedMethods(type: KClass<*>): Map<String, JsonRpcMethod> {
        val visitedTypes = mutableSetOf<KClass<*>>()
        return getSupportedMethods(type, visitedTypes)
    }

    /**
     * Finds all Json RPC methods on a given type
     */
    private fun getSupportedMethods(type: KClass<*>, visitedTypes: MutableSet<KClass<*>>): Map<String, JsonRpcMethod> {
        val result = LinkedHashMap<String, JsonRpcMethod>()
        AnnotationUtil.findRpcMethods(type, visitedTypes) { methodInfo ->
            val method = if (methodInfo.isNotification) {
                JsonRpcMethod.notification(methodInfo.name, *methodInfo.parameterTypes.toTypedArray())
            } else {
                check(methodInfo.method.isSuspend) { "JsonRPC requests must use suspend functions" }
                val returnType = methodInfo.method.returnType
                JsonRpcMethod.request(
                    methodInfo.name,
                    returnType,
                    *methodInfo.parameterTypes.toTypedArray()
                )
            }
            check(result.put(methodInfo.name, method) == null) { "Duplicate RPC method " + methodInfo.name + "." }
        }
        return result
    }
}

