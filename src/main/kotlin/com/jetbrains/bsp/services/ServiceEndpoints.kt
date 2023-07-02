package com.jetbrains.bsp.services


import com.jetbrains.bsp.Endpoint
import com.jetbrains.bsp.json.JsonRpcMethod
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass


object ServiceEndpoints {
    /**
     * Wraps a given [Endpoint] in the given service interface.
     *
     * @return the wrapped service object
     */
    fun <T : Any> toServiceObject(endpoint: Endpoint, interface_: KClass<T>): T {
        val interfArray = arrayOf<Class<*>>(interface_.java, Endpoint::class.java)
        val invocationHandler = EndpointProxy(endpoint, interface_)
        return Proxy.newProxyInstance(interface_.java.classLoader, interfArray, invocationHandler) as T
    }

    /**
     * Wraps a given [Endpoint] in the given service interfaces.
     *
     * @return the wrapped service object
     */
    fun toServiceObject(endpoint: Endpoint, interfaces: Collection<KClass<*>>, classLoader: ClassLoader): Any {
        val interfacesWithEndpoint = interfaces.toMutableList()
        interfacesWithEndpoint.add(Endpoint::class)
        val interfacesAsJava = interfacesWithEndpoint.map { it.java }
        val interfArray = interfacesAsJava.toTypedArray()
        val invocationHandler = EndpointProxy(endpoint, interfacesWithEndpoint)
        return Proxy.newProxyInstance(classLoader, interfArray, invocationHandler)
    }

    /**
     * Wraps a given object with service annotations behind an [Endpoint] interface.
     *
     * @return the wrapped service endpoint
     */
    fun <T: Any> toEndpoint(serviceObject: T): Endpoint {
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
        val result: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        AnnotationUtil.findRpcMethods(type, visitedTypes) { methodInfo ->
            val meth: JsonRpcMethod
            if (methodInfo.isNotification) {
                meth = JsonRpcMethod.notification(methodInfo.name, *methodInfo.parameterTypes.toTypedArray())
            } else {
                val genericReturnType = methodInfo.method.returnType
                if (genericReturnType.arguments.size == 1 && genericReturnType.classifier == CompletableFuture::class) {
                    val returnType =
                        genericReturnType.arguments[0].type ?: throw IllegalStateException("Expecting return type of CompletableFuture but was : $genericReturnType")
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
                        *methodInfo.parameterTypes.toTypedArray()
                    )
                } else {
                    throw IllegalStateException("Expecting return type of CompletableFuture but was : $genericReturnType")
                }
            }
            check(result.put(methodInfo.name, meth) == null) { "Duplicate RPC method " + methodInfo.name + "." }
        }
        return result
    }
}

