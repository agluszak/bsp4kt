package com.jetbrains.bsp.json

import com.jetbrains.bsp.*
import com.jetbrains.bsp.messages.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import kotlin.reflect.KType
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.typeOf


class MessageJsonHandler(val json: Json, val supportedMethods: Map<String, JsonRpcMethod>) {
    /**
     * Resolve an RPC method by name.
     */
    fun getJsonRpcMethod(name: String): JsonRpcMethod? {
        val result: JsonRpcMethod? = supportedMethods[name]
        if (result != null) return result else if (CANCEL_METHOD.methodName == name) return CANCEL_METHOD
        return null
    }

    fun <T> serialize(value: T, type: KType): JsonElement {
        return serialize(value, json.serializersModule.serializer(type))
    }

    fun <T> serialize(value: T, serializer: KSerializer<T>): JsonElement {
        try {
            return json.encodeToJsonElement(serializer, value)
        } catch (e: SerializationException) {
            throw MessageIssueException(SerializationIssue(e))
        }
    }

    fun <T> deserialize(jsonElement: JsonElement, type: KType): T {
        return deserialize(jsonElement, json.serializersModule.serializer(type)) as T
    }

    fun <T> deserialize(jsonElement: JsonElement, serializer: KSerializer<T>): T {
        try {
            return json.decodeFromJsonElement(serializer, jsonElement)
        } catch (e: SerializationException) {
            throw MessageIssueException(SerializationIssue(e))
        }
    }

    fun serializeResult(method: String, result: Any?): JsonElement {
        val jsonRpcMethod = getJsonRpcMethod(method) ?: throw MessageIssueException(NoSuchMethod(method))
        val resultType = jsonRpcMethod.resultType
        return serialize(result, resultType)
    }

    fun deserializeResult(method: String, result: JsonElement): Any? {
        val jsonRpcMethod = getJsonRpcMethod(method) ?: throw MessageIssueException(NoSuchMethod(method))
        val resultType = jsonRpcMethod.resultType
        return deserialize(result, resultType)
    }

    fun serializeParams(method: String, params: List<Any?>): JsonParams {
        val jsonRpcMethod = getJsonRpcMethod(method) ?: throw MessageIssueException(NoSuchMethod(method))
        if (params.size != jsonRpcMethod.parameterTypes.size) {
            val issue = WrongNumberOfParamsIssue(method, jsonRpcMethod.parameterTypes.size, params.size)
            throw MessageIssueException(issue)
        }
        return when (params.size) {
            0 -> JsonParams.ObjectParams(buildJsonObject { })
            1 -> {
                val jsonElement = serialize(params[0], jsonRpcMethod.parameterTypes[0])
                when (jsonElement) {
                    is JsonObject -> JsonParams.ObjectParams(jsonElement)
                    else -> JsonParams.ArrayParams(JsonArray(listOf(jsonElement)))
                }
            }

            else -> {
                // TODO use correct serializers for each parameter
                val jsonElement = serialize(params, typeOf<Any>())
                JsonParams.ArrayParams(jsonElement.jsonArray)
            }
        }
    }

    fun deserializeParams(message: IncomingMessage): List<Any?> {
        val size = message.params?.size ?: 0
        val jsonRpcMethod = getJsonRpcMethod(message.method) ?: throw MessageIssueException(NoSuchMethod(message.method))

        val params = message.params
        return when (params) {
            null -> emptyList()
            is JsonParams.ObjectParams -> {
                val jsonObject = params.params
                if (jsonObject.isEmpty()) {
                    listOf(null)
                } else {
                    val parameterType = jsonRpcMethod.parameterTypes.singleOrNull()
                    if (parameterType == null) {
                        throw MessageIssueException(WrongNumberOfParamsIssue(message.method, 0, 1))
                    } else {
                        val result = deserialize<Any>(jsonObject, parameterType)
                        listOf(result)
                    }
                }
            }

            is JsonParams.ArrayParams -> {
                // If the method has a single parameter of type List, we deserialize the whole array as that parameter
                if (jsonRpcMethod.parameterTypes.size == 1 && jsonRpcMethod.parameterTypes[0].isSubtypeOf(typeOf<List<*>>())) {
                    val elementType = json.serializersModule.serializer(jsonRpcMethod.parameterTypes[0])
                    val serializer = ListSerializer(elementType)
                    return deserialize(params.params, serializer)
                }
                // Otherwise, we treat the array as a list of parameters and add nulls if the array is too short
                val jsonArray = if (size < jsonRpcMethod.parameterTypes.size) {
                    params.params + List(jsonRpcMethod.parameterTypes.size - size) { JsonNull }
                } else {
                    params.params
                }
                jsonArray.zip(jsonRpcMethod.parameterTypes).map { (jsonElement, type) ->
                    json.decodeFromJsonElement(json.serializersModule.serializer(type), jsonElement)
                }
            }
        }
    }

    fun deserializeMessage(input: String): Message {
        return json.decodeFromString(input)
    }

    fun serializeMessage(message: Message): String {
        return json.encodeToString(message)
    }

    companion object {
        val CANCEL_METHOD: JsonRpcMethod = JsonRpcMethod.notification("$/cancelRequest", typeOf<CancelParams>())
    }
}
