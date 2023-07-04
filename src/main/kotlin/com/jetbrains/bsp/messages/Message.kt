package com.jetbrains.bsp.messages

import com.jetbrains.bsp.json.JsonDeserialization
import com.jetbrains.bsp.json.JsonSerialization
import com.jetbrains.bsp.messages.Message.Companion.JSONRPC_VERSION
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

sealed interface Message : JsonSerialization {
    companion object : JsonDeserialization<Message> {
        const val JSONRPC_VERSION = "2.0"
        const val CONTENT_LENGTH_HEADER = "Content-Length"
        const val CONTENT_TYPE_HEADER = "Content-Type"
        const val JSON_MIME_TYPE = "application/json"
        const val CRLF = "\r\n"

        override fun deserialize(json: JsonElement): Message {
            when (json) {
                is JsonObject -> {
                    ensureJsonRpcVersion(json)
                    val id = json["id"]?.let { MessageId.deserialize(it) }
                    val method = json["method"]?.jsonPrimitive?.content
                    val params = json["params"]?.let { JsonParams.deserialize(it) }
                    return if (method != null) {
                        if (id != null) {
                            RequestMessage(id, method, params)
                        } else {
                            NotificationMessage(method, params)
                        }
                    } else {
                        if (id == null) {
                            throw SerializationException("Expected a method or id for Message")
                        } else {
                            val result = json["result"]
                            val error = json["error"]?.let { ResponseError.deserialize(it) }
                            if (result != null) {
                                ResponseMessage.Result(id, result)
                            } else if (error != null) {
                                ResponseMessage.Error(id, error)
                            } else {
                                throw SerializationException("A response message must have a result or an error")
                            }
                        }
                    }
                }

                else -> throw SerializationException("Expected a JSON object for Message")
            }
        }
    }
}

@Serializable
sealed interface MessageId : JsonSerialization {
    @JvmInline
    value class NumberId(val id: Int) : MessageId

    @JvmInline
    value class StringId(val id: String) : MessageId

    override fun serializeToJson(): JsonElement {
        return when (this) {
            is NumberId -> JsonPrimitive(id)
            is StringId -> JsonPrimitive(id)
        }
    }

    companion object : JsonDeserialization<MessageId> {
        override fun deserialize(json: JsonElement): MessageId {
            return when (json) {
                is JsonPrimitive -> {
                    if (json.isString) {
                        StringId(json.content)
                    } else {
                        NumberId(json.int)
                    }
                }

                else -> throw SerializationException("Expected a primitive value for MessageId")
            }
        }

    }
}

sealed interface JsonParams : JsonSerialization {
    @JvmInline
    value class ObjectParams(val params: JsonObject) : JsonParams

    @JvmInline
    value class ArrayParams(val params: JsonArray) : JsonParams

    override fun serializeToJson(): JsonElement {
        return when (this) {
            is ObjectParams -> params
            is ArrayParams -> params
        }
    }

    val size: Int get() {
        return when (this) {
            is ObjectParams -> 1
            is ArrayParams -> params.size
        }
    }

    companion object : JsonDeserialization<JsonParams?> {
        override fun deserialize(json: JsonElement): JsonParams? {
            return when (json) {
                is JsonObject -> ObjectParams(json)
                is JsonArray -> ArrayParams(json)
                is JsonNull -> null
                else -> throw SerializationException("Expected a JSON object or array for JsonParams")
            }
        }
    }
}

fun ensureJsonRpcVersion(json: JsonObject) {
    json["jsonrpc"]?.jsonPrimitive?.content?.let {
        if (it != JSONRPC_VERSION) {
            throw SerializationException("Expected jsonrpc version $JSONRPC_VERSION")
        }
    }
}

data class NotificationMessage(val method: String, val params: JsonParams?) : Message {
    override fun serializeToJson(): JsonElement {
        return buildJsonObject {
            put("jsonrpc", JSONRPC_VERSION)
            put("method", method)
            params?.let { put("params", it.serializeToJson()) }
        }
    }
}


data class RequestMessage(val id: MessageId, val method: String, val params: JsonParams?) : Message {
    override fun serializeToJson(): JsonElement {
        return buildJsonObject {
            put("jsonrpc", JSONRPC_VERSION)
            put("id", id.serializeToJson())
            put("method", method)
            params?.let { put("params", it.serializeToJson()) }
        }
    }
}

data class ResponseError(val code: Int, val message: String, val data: JsonElement?) : JsonSerialization {
    override fun serializeToJson(): JsonElement {
        return buildJsonObject {
            put("code", code)
            put("message", message)
            data?.let { put("data", it) }
        }
    }

    companion object : JsonDeserialization<ResponseError> {
        override fun deserialize(json: JsonElement): ResponseError {
            return when (json) {
                is JsonObject -> {
                    val code =
                        json["code"]?.jsonPrimitive?.int ?: throw SerializationException("Expected a code property")
                    val message = json["message"]?.jsonPrimitive?.content
                        ?: throw SerializationException("Expected a message property")
                    val data = json["data"]
                    ResponseError(code, message, data)
                }

                else -> throw SerializationException("Expected a JSON object for ResponseError")
            }
        }
    }
}

sealed interface ResponseMessage : Message {
    val id: MessageId

    data class Result(override val id: MessageId, val result: JsonElement) : ResponseMessage {
        override fun serializeToJson(): JsonElement {
            return buildJsonObject {
                put("jsonrpc", JSONRPC_VERSION)
                put("id", id.serializeToJson())
                put("result", result)
            }
        }
    }

    data class Error(override val id: MessageId, val error: ResponseError) : ResponseMessage {
        override fun serializeToJson(): JsonElement {
            return buildJsonObject {
                put("jsonrpc", JSONRPC_VERSION)
                put("id", id.serializeToJson())
                put("error", error.serializeToJson())
            }
        }
    }
}


