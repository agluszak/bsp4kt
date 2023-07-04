package com.jetbrains.bsp.json

import kotlin.reflect.KType
import kotlin.reflect.typeOf


/**
 * A description of a JSON-RPC method.
 */
data class JsonRpcMethod(
    val methodName: String, val parameterTypes: List<KType>, val resultType: KType, val isNotification: Boolean
) {

    override fun toString(): String {
        val builder = StringBuilder()
        if (isNotification) builder.append("JsonRpcMethod (notification) {\n") else builder.append("JsonRpcMethod (request) {\n")
        builder.append("\tmethodName: ").append(methodName).append('\n')
        if (parameterTypes != null) builder.append("\tparameterTypes: ").append(parameterTypes).append('\n')
        if (resultType != null) builder.append("\treturnType: ").append(resultType).append('\n')
        builder.append("}")
        return builder.toString()
    }

    companion object {
        fun notification(name: String, vararg parameterTypes: KType): JsonRpcMethod {
            return JsonRpcMethod(name, parameterTypes.toList(), typeOf<Unit>(), true)
        }

        fun request(name: String, returnType: KType, vararg parameterTypes: KType): JsonRpcMethod {
            return JsonRpcMethod(name, parameterTypes.toList(), returnType, false)
        }

//        fun request(
//            name: String?,
//            returnType: Type,
//            returnTypeAdapterFactory: TypeAdapterFactory?,
//            vararg parameterTypes: Type
//        ): JsonRpcMethod {
//            return JsonRpcMethod(name, parameterTypes, returnType, returnTypeAdapterFactory, false)
//        }
    }
}

