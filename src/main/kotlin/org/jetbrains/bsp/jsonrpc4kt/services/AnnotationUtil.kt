package org.jetbrains.jsonrpc4kt.services

import java.util.function.Consumer
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.superclasses
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.isAccessible

object AnnotationUtil {
    /**
     * Depth first search for annotated methods in hierarchy.
     */
    fun findRpcMethods(clazz: KClass<*>?, visited: MutableSet<KClass<*>>, acceptor: Consumer<MethodInfo>) {
        if (clazz == null || !visited.add(clazz)) return
        for (interf in clazz.superclasses) {
            findRpcMethods(interf, visited, acceptor)
        }
        val segment = getSegment(clazz)
        for (method in clazz.declaredMemberFunctions) {
            val methodInfo = createMethodInfo(method, segment)
            if (methodInfo != null) {
                acceptor.accept(methodInfo)
            }
        }
    }

    internal fun getSegment(clazz: KClass<*>): String {
        val jsonSegment: JsonSegment? = clazz.findAnnotation()
        return if (jsonSegment == null) "" else jsonSegment.value + "/"
    }

    internal fun createMethodInfo(method: KFunction<*>, segment: String): MethodInfo? {
        val jsonRequest: JsonRequest? = method.findAnnotation()
        if (jsonRequest != null) {
            return createRequestInfo(method, segment, jsonRequest)
        }
        val jsonNotification: JsonNotification? = method.findAnnotation()
        if (jsonNotification != null) {
            return createNotificationInfo(method, segment, jsonNotification)
        }
        return null
    }

    internal fun createNotificationInfo(
        method: KFunction<*>,
        segment: String,
        jsonNotification: JsonNotification
    ): MethodInfo {
        val methodInfo = createMethodInfo(method, jsonNotification.useSegment, segment, jsonNotification.value)
        return methodInfo.copy(isNotification = true)
    }

    internal fun createRequestInfo(method: KFunction<*>, segment: String, jsonRequest: JsonRequest): MethodInfo {
        return createMethodInfo(method, jsonRequest.useSegment, segment, jsonRequest.value)
    }

    internal fun createMethodInfo(
        method: KFunction<*>,
        useSegment: Boolean,
        segment: String,
        value: String?
    ): MethodInfo {
        method.isAccessible = true // TODO: is this necessary?
        val name = getMethodName(method, useSegment, segment, value)
        val parameterTypes = method.valueParameters.map { it.type } // Exclues receiver type
        return MethodInfo(name, method, parameterTypes)
    }

    internal fun getMethodName(method: KFunction<*>, useSegment: Boolean, segment: String, value: String?): String {
        val name = if (!value.isNullOrEmpty()) value else method.name
        return if (useSegment) segment + name else name
    }

    data class MethodInfo(
        val name: String,
        val method: KFunction<*>,
        val parameterTypes: List<KType> = listOf(),
        val isNotification: Boolean = false
    )
}
