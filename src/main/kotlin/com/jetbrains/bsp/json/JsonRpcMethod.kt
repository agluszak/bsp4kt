package com.jetbrains.bsp.json

import kotlin.reflect.KType
import kotlin.reflect.typeOf


/**
 * A description of a JSON-RPC method.
 */
data class JsonRpcMethod(
    val methodName: String, val parameterTypes: List<KType>, val resultType: KType, val isNotification: Boolean
) {

    companion object {
        fun notification(name: String, vararg parameterTypes: KType): JsonRpcMethod {
            return JsonRpcMethod(name, parameterTypes.toList(), typeOf<Unit>(), true)
        }

        fun request(name: String, returnType: KType, vararg parameterTypes: KType): JsonRpcMethod {
            return JsonRpcMethod(name, parameterTypes.toList(), returnType, false)
        }
    }
}

