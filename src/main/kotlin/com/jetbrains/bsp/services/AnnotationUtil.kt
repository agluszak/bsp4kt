package com.jetbrains.bsp.services

import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.Type
import java.util.*
import java.util.function.Consumer

object AnnotationUtil {
    fun findDelegateSegments(clazz: Class<*>?, visited: MutableSet<Class<*>>, acceptor: Consumer<Method>) {
        if (clazz == null || !visited.add(clazz)) return
        findDelegateSegments(clazz.superclass, visited, acceptor)
        for (interf in clazz.interfaces) {
            findDelegateSegments(interf, visited, acceptor)
        }
        for (method in clazz.declaredMethods) {
            if (isDelegateMethod(method)) {
                acceptor.accept(method)
            }
        }
    }

    fun isDelegateMethod(method: Method): Boolean {
        if (!method.isSynthetic) {
            val jsonDelegate: JsonDelegate? = method.getAnnotation(JsonDelegate::class.java)
            if (jsonDelegate != null) {
                check(method.parameterCount == 0 && method.returnType.isInterface) { "The method $method is not a proper @JsonDelegate method." }
                return true
            }
        }
        return false
    }

    /**
     * Depth first search for annotated methods in hierarchy.
     */
    fun findRpcMethods(clazz: Class<*>?, visited: MutableSet<Class<*>>, acceptor: Consumer<MethodInfo>) {
        if (clazz == null || !visited.add(clazz)) return
        findRpcMethods(clazz.superclass, visited, acceptor)
        for (interf in clazz.interfaces) {
            findRpcMethods(interf, visited, acceptor)
        }
        val segment = getSegment(clazz)
        for (method in clazz.declaredMethods) {
            val methodInfo = createMethodInfo(method, segment)
            if (methodInfo != null) {
                acceptor.accept(methodInfo)
            }
        }
    }

    internal fun getSegment(clazz: Class<*>): String {
        val jsonSegment: JsonSegment? = clazz.getAnnotation(JsonSegment::class.java)
        return if (jsonSegment == null) "" else jsonSegment.value + "/"
    }

    internal fun createMethodInfo(method: Method, segment: String): MethodInfo? {
        if (!method.isSynthetic) {
            val jsonRequest: JsonRequest? = method.getAnnotation(JsonRequest::class.java)
            if (jsonRequest != null) {
                return createRequestInfo(method, segment, jsonRequest)
            }
            val jsonNotification: JsonNotification? = method.getAnnotation(JsonNotification::class.java)
            if (jsonNotification != null) {
                return createNotificationInfo(method, segment, jsonNotification)
            }
        }
        return null
    }

    internal fun createNotificationInfo(
        method: Method,
        segment: String,
        jsonNotification: JsonNotification
    ): MethodInfo {
        val methodInfo = createMethodInfo(method, jsonNotification.useSegment, segment, jsonNotification.value)
        methodInfo.isNotification = true
        return methodInfo
    }

    internal fun createRequestInfo(method: Method, segment: String, jsonRequest: JsonRequest): MethodInfo {
        return createMethodInfo(method, jsonRequest.useSegment, segment, jsonRequest.value)
    }

    internal fun createMethodInfo(method: Method, useSegment: Boolean, segment: String, value: String?): MethodInfo {
        method.isAccessible = true
        return MethodInfo(getMethodName(method, useSegment, segment, value), method, getParameterTypes(method))
    }

    internal fun getMethodName(method: Method, useSegment: Boolean, segment: String, value: String?): String {
        val name = if (!value.isNullOrEmpty()) value else method.name
        return if (useSegment) segment + name else name
    }

    internal fun getParameterTypes(method: Method): Array<Type> {
        return Arrays.stream(method.parameters).map { t: Parameter -> t.parameterizedType }
            .toArray { arrayOfNulls(it) }
    }

    data class MethodInfo(val name: String, val method: Method, val parameterTypes: Array<Type> = arrayOf<Type>(), var isNotification: Boolean = false)


    data class DelegateInfo(val method: Method, val delegate: Any)
}
