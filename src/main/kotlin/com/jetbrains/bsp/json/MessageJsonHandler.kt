package com.jetbrains.bsp.json

import com.jetbrains.bsp.messages.CancelParams
import com.jetbrains.bsp.messages.Message
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.*
import java.util.function.Consumer


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

    fun parseMessage(input: CharSequence): Message {
        val stream = input.toString().byteInputStream()
        return parseMessage(stream)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun parseMessage(input: InputStream): Message {
//        try {
            val message: Message = json.decodeFromStream(input)
            // TODO: check input.available()
            return message
//        }
//        catch (e: SerializationException) {
//            val issue = MessageIssue("Message could not be parsed.", ResponseErrorCode.ParseError.value, e)
//            throw MessageIssueException(message, listOf(issue))
//        } catch (e: IllegalArgumentException) {
//
//        } catch (e: IOException)
//        val jsonReader = JsonReader(input)
//        val message: Message = gson.fromJson(jsonReader, Message::class.java)
//        return message
    }

    fun serialize(message: Message): String = json.encodeToString(message)


    companion object {
        val CANCEL_METHOD: JsonRpcMethod = JsonRpcMethod.notification("$/cancelRequest", CancelParams::class.java)
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
