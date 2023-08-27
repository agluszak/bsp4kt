package org.jetbrains.jsonrpc4kt.messages

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import org.jetbrains.jsonrpc4kt.messages.Message.Companion.JSONRPC_VERSION

@Serializable(with = Message.Companion::class)
sealed interface Message {
    companion object : KSerializer<Message> {
        const val JSONRPC_VERSION = "2.0"
        const val CONTENT_LENGTH_HEADER = "Content-Length"
        const val CONTENT_TYPE_HEADER = "Content-Type"
        const val JSON_MIME_TYPE = "application/json"
        const val CRLF = "\r\n"

        override val descriptor: SerialDescriptor
            // It's incorrect, but it doesn't matter
            get() = PrimitiveSerialDescriptor("Message", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): Message {
            require(decoder is JsonDecoder)
            val json = decoder.decodeJsonElement()
            val deserializer = selectDeserializer(json)

            require(json is JsonObject)
            ensureJsonRpcVersion(json)

            val jsonMutable = json.toMutableMap()
            // Remove the jsonrpc version
            jsonMutable.remove("jsonrpc")

            return decoder.json.decodeFromJsonElement(deserializer, JsonObject(jsonMutable))
        }

        val notificationSerializer = NotificationMessage.serializer()
        val requestSerializer = RequestMessage.serializer()
        val responseSerializer = ResponseMessage.serializer()

        override fun serialize(encoder: Encoder, value: Message) {
            val jsonEncoder = encoder as JsonEncoder
            val json = when (value) {
                is NotificationMessage -> jsonEncoder.json.encodeToJsonElement(notificationSerializer, value)
                is RequestMessage -> jsonEncoder.json.encodeToJsonElement(requestSerializer, value)
                is ResponseMessage -> jsonEncoder.json.encodeToJsonElement(responseSerializer, value)
            }
            require(json is JsonObject)
            // Add the jsonrpc version, ensure it will be the first field
            val jsonWithJsonrpc =
                JsonObject(json.toMutableMap().also { it["jsonrpc"] = JsonPrimitive(JSONRPC_VERSION) })
            jsonEncoder.encodeJsonElement(jsonWithJsonrpc)
        }

        fun selectDeserializer(element: JsonElement): DeserializationStrategy<Message> {
            return when (element) {
                is JsonObject -> {
                    val id = element["id"]
                    val method = element["method"]
                    val result = element["result"]
                    val error = element["error"]
                    if (method != null) {
                        if (id != null) {
                            requestSerializer
                        } else {
                            notificationSerializer
                        }
                    } else if (error != null || result != null) {
                        responseSerializer
                    } else {
                        throw SerializationException("Expected a method, result, or error property for Message")
                    }
                }

                else -> throw SerializationException("Expected a JSON object for Message")
            }
        }
    }
}

@Serializable(with = MessageId.Companion::class)
sealed interface MessageId {
    @Serializable
    @JvmInline
    value class NumberId(val id: Int) : MessageId {
        override fun toString(): String = id.toString()
    }

    @Serializable
    @JvmInline
    value class StringId(val id: String) : MessageId {
        override fun toString(): String = "\"$id\""
    }


    companion object : JsonContentPolymorphicSerializer<MessageId>(MessageId::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<MessageId> {
            return when (element) {
                is JsonPrimitive -> {
                    if (element.isString) {
                        StringId.serializer()
                    } else if (element.intOrNull != null) {
                        NumberId.serializer()
                    } else {
                        throw SerializationException("Expected a string or number for MessageId")
                    }
                }

                else -> throw SerializationException("Expected a primitive value for MessageId")
            }
        }

    }
}

@Serializable(with = JsonParams.Companion::class)
sealed interface JsonParams {
    @Serializable
    @JvmInline
    value class ObjectParams(val params: JsonObject) : JsonParams

    @Serializable
    @JvmInline
    value class ArrayParams(val params: JsonArray) : JsonParams

    val size: Int
        get() {
            return when (this) {
                is ObjectParams -> 1
                is ArrayParams -> params.size
            }
        }

    companion object : JsonContentPolymorphicSerializer<JsonParams>(JsonParams::class) {
        fun array(vararg params: JsonElement): JsonParams {
            return ArrayParams(JsonArray(params.toList()))
        }

        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<JsonParams> {
            return when (element) {
                is JsonObject -> ObjectParams.serializer()
                is JsonArray -> ArrayParams.serializer()
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
    } ?: throw SerializationException("Expected a jsonrpc property")
}

sealed interface IncomingMessage : Message {
    val method: String
    val params: JsonParams?
}

@Serializable
data class NotificationMessage(override val method: String, override val params: JsonParams? = null) : IncomingMessage

@Serializable
data class RequestMessage(val id: MessageId, override val method: String, override val params: JsonParams? = null) :
    IncomingMessage

@Serializable
data class ResponseError(val code: Int, val message: String, val data: JsonElement? = null) {
    companion object {
        fun fromException(exception: Exception): ResponseError {
            when (exception) {
                is SerializationException -> {
                    return ResponseError(
                        ResponseErrorCode.ParseError.code,
                        "Error during serialization: " + exception.message
                    )
                }

                else -> {
                    return ResponseError(ResponseErrorCode.InternalError.code, "Internal error: " + exception.message)
                }
            }
        }
    }
}


@Serializable(with = ResponseMessage.Companion::class)
sealed interface ResponseMessage : Message {
    val id: MessageId?

    @Serializable
    data class Result(override val id: MessageId, val result: JsonElement) : ResponseMessage

    @Serializable
    data class Error(override val id: MessageId?, val error: ResponseError) : ResponseMessage

    companion object : JsonContentPolymorphicSerializer<ResponseMessage>(ResponseMessage::class) {
        override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ResponseMessage> {
            return when (element) {
                is JsonObject -> {
                    val id = element["id"]
                    val result = element["result"]
                    val error = element["error"]
                    if (result != null) {
                        Result.serializer()
                    } else if (error != null) {
                        Error.serializer()
                    } else if (id != null) {
                        throw SerializationException("Expected a result or error property for ResponseMessage")
                    } else {
                        throw SerializationException("Expected an id property for ResponseMessage")
                    }
                }

                else -> throw SerializationException("Expected a JSON object for ResponseMessage")
            }
        }

    }
}

