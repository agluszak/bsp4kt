package com.jetbrains.bsp.services


import com.jetbrains.bsp.Endpoint
import com.jetbrains.bsp.json.JsonRpcMethod
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy


object ServiceEndpoints {
    /**
     * Wraps a given [Endpoint] in the given service interface.
     *
     * @return the wrapped service object
     */
    fun <T> toServiceObject(endpoint: Endpoint, interface_: Class<T>): T {
        val interfArray = arrayOf<Class<*>>(interface_, Endpoint::class.java)
        val invocationHandler = EndpointProxy(endpoint, interface_)
        return Proxy.newProxyInstance(interface_.classLoader, interfArray, invocationHandler) as T
    }

    /**
     * Wraps a given [Endpoint] in the given service interfaces.
     *
     * @return the wrapped service object
     */
    fun toServiceObject(endpoint: Endpoint, interfaces: Collection<Class<*>>, classLoader: ClassLoader): Any {
        val interfacesWithEndpoint = interfaces.toMutableList()
        interfacesWithEndpoint.add(Endpoint::class.java)
        val interfArray = interfacesWithEndpoint.toTypedArray()
        val invocationHandler = EndpointProxy(endpoint, interfacesWithEndpoint)
        return Proxy.newProxyInstance(classLoader, interfArray, invocationHandler)
    }

    /**
     * Wraps a given object with service annotations behind an [Endpoint] interface.
     *
     * @return the wrapped service endpoint
     */
    fun toEndpoint(serviceObject: Any): Endpoint {
        return GenericEndpoint(listOf(serviceObject))
    }

    /**
     * Wraps a collection of objects with service annotations behind an [Endpoint] interface.
     *
     * @return the wrapped service endpoint
     */
    fun toEndpoint(serviceObjects: Collection<Any>): Endpoint {
        return GenericEndpoint(serviceObjects)
    }

    /**
     * Finds all Json RPC methods on a given class.
     *
     * @return the supported JsonRpcMethods
     */
    fun getSupportedMethods(type: Class<*>): Map<String, JsonRpcMethod> {
        val visitedTypes = mutableSetOf<Class<*>>()
        return getSupportedMethods(type, visitedTypes)
    }

    /**
     * Finds all Json RPC methods on a given type
     */
    private fun getSupportedMethods(type: Class<*>, visitedTypes: MutableSet<Class<*>>): Map<String, JsonRpcMethod> {
        val result: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        AnnotationUtil.findRpcMethods(type, visitedTypes) { methodInfo ->
            val meth: JsonRpcMethod
            if (methodInfo.isNotification) {
                meth = JsonRpcMethod.notification(methodInfo.name, *methodInfo.parameterTypes)
            } else {
                val genericReturnType = methodInfo.method.genericReturnType
                if (genericReturnType is ParameterizedType) {
                    val returnType =
                        genericReturnType.actualTypeArguments[0]
//                    var responseTypeAdapter: TypeAdapterFactory? = null
//                    val responseTypeAdapterAnnotation: ResponseJsonAdapter = methodInfo.method.getAnnotation(
//                        ResponseJsonAdapter::class.java
//                    )
//                    if (responseTypeAdapterAnnotation != null) {
//                        responseTypeAdapter = try {
//                            responseTypeAdapterAnnotation.value().getDeclaredConstructor().newInstance()
//                        } catch (e: ReflectiveOperationException) {
//                            throw RuntimeException(e)
//                        }
//                    }
                    meth = JsonRpcMethod.request(
                        methodInfo.name,
                        returnType,
                        *methodInfo.parameterTypes
                    )
                } else {
                    throw IllegalStateException("Expecting return type of CompletableFuture but was : $genericReturnType")
                }
            }
            check(result.put(methodInfo.name, meth) == null) { "Duplicate RPC method " + methodInfo.name + "." }
        }
        AnnotationUtil.findDelegateSegments(type, HashSet()) { method ->
            val supportedDelegateMethods: Map<String, JsonRpcMethod> =
                getSupportedMethods(
                    method.returnType, visitedTypes
                )
            for (meth in supportedDelegateMethods.values) {
                check(
                    result.put(
                        meth.methodName,
                        meth
                    ) == null
                ) { "Duplicate RPC method " + meth.methodName + "." }
            }
        }
        return result
    }
}

