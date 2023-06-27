package com.jetbrains.bsp.json

import java.lang.reflect.Type


/**
 * A description of a JSON-RPC method.
 */
data class JsonRpcMethod(
    val methodName: String, val parameterTypes: Array<out Type>, val returnType: Type, val isNotification: Boolean
) {

    override fun toString(): String {
        val builder = StringBuilder()
        if (isNotification) builder.append("JsonRpcMethod (notification) {\n") else builder.append("JsonRpcMethod (request) {\n")
        builder.append("\tmethodName: ").append(methodName).append('\n')
        if (parameterTypes != null) builder.append("\tparameterTypes: ").append(parameterTypes).append('\n')
        if (returnType != null) builder.append("\treturnType: ").append(returnType).append('\n')
        builder.append("}")
        return builder.toString()
    }

    companion object {
        fun notification(name: String, vararg parameterTypes: Type): JsonRpcMethod {
            return JsonRpcMethod(name, parameterTypes, Void::class.java, true)
        }

        fun request(name: String, returnType: Type, vararg parameterTypes: Type): JsonRpcMethod {
            return JsonRpcMethod(name, parameterTypes, returnType, false)
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

