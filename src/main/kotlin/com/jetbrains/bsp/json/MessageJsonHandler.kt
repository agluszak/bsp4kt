package com.jetbrains.bsp.json

import com.jetbrains.bsp.messages.CancelParams
import com.jetbrains.bsp.messages.Message
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.*
import java.util.function.Consumer
import kotlin.reflect.typeOf


class MessageJsonHandler(val json: Json, val supportedMethods: Map<String, JsonRpcMethod>) {
    var methodProvider: MethodProvider? = null

    /**
     * Resolve an RPC method by name.
     */
    fun getJsonRpcMethod(name: String): JsonRpcMethod? {
        val result: JsonRpcMethod? = supportedMethods[name]
        if (result != null) return result else if (CANCEL_METHOD.methodName == name) return CANCEL_METHOD
        return null
    }

    fun parseMessage(input: String): Message {
        val jsonElement = json.parseToJsonElement(input)
        return Message.deserialize(jsonElement)
    }

    fun serialize(message: Message): String = json.encodeToString(message.serializeToJson())


    companion object {
        val CANCEL_METHOD: JsonRpcMethod = JsonRpcMethod.notification("$/cancelRequest", typeOf<CancelParams>())
//        private var toStringInstance: MessageJsonHandler? = null
//
//        /**
//         * Perform JSON serialization of the given object using the default configuration of JSON-RPC messages
//         * enhanced with the pretty printing option.
//         */
//        fun toString(`object`: Any?): String {
//            if (toStringInstance == null) {
//                toStringInstance = MessageJsonHandler(
//                    emptyMap<String, JsonRpcMethod>(),
//                    Consumer<GsonBuilder> { gsonBuilder: GsonBuilder -> gsonBuilder.setPrettyPrinting() })
//            }
//            return toStringInstance!!.gson.toJson(`object`)
//        }
    }
}
