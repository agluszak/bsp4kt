package com.jetbrains.bsp.json

import com.jetbrains.bsp.messages.Message
import kotlinx.serialization.json.Json
import java.io.*
import java.util.function.Consumer


class MessageJsonHandler(val json: Json) {
    private val supportedMethods: Map<String, JsonRpcMethod>
    var methodProvider: MethodProvider? = null

    /**
     * @param supportedMethods - a map used to resolve RPC methods in [.getJsonRpcMethod]
     */
    constructor(supportedMethods: Map<String, JsonRpcMethod>) {
        this.supportedMethods = supportedMethods
        gson = defaultGsonBuilder.create()
    }

    /**
     * @param supportedMethods - a map used to resolve RPC methods in [.getJsonRpcMethod]
     * @param configureGson - a function that contributes to the GsonBuilder created by [.getDefaultGsonBuilder]
     */
    constructor(supportedMethods: Map<String, JsonRpcMethod>, configureGson: Consumer<GsonBuilder>) {
        this.supportedMethods = supportedMethods
        val gsonBuilder: GsonBuilder = defaultGsonBuilder
        configureGson.accept(gsonBuilder)
        gson = gsonBuilder.create()
    }


    /**
     * Resolve an RPC method by name.
     */
    fun getJsonRpcMethod(name: String): JsonRpcMethod? {
        val result: JsonRpcMethod? = supportedMethods[name]
        if (result != null) return result else if (CANCEL_METHOD.methodName == name) return CANCEL_METHOD
        return null
    }

    @Throws(JsonParseException::class)
    fun parseMessage(input: CharSequence): Message {
        val reader = StringReader(input.toString())
        return parseMessage(reader)
    }

    @Throws(JsonParseException::class)
    fun parseMessage(input: Reader): Message {
        val jsonReader = JsonReader(input)
        val message: Message = gson.fromJson(jsonReader, Message::class.java)
        if (message != null) {
            // Check whether the input has been fully consumed
            try {
                if (jsonReader.peek() !== JsonToken.END_DOCUMENT) {
                    val issue =
                        MessageIssue("JSON document was not fully consumed.", ResponseErrorCode.ParseError.getValue())
                    throw MessageIssueException(message, issue)
                }
            } catch (e: MalformedJsonException) {
                val issue = MessageIssue("Message could not be parsed.", ResponseErrorCode.ParseError.getValue(), e)
                throw MessageIssueException(message, issue)
            } catch (e: IOException) {
                throw JsonIOException(e)
            }
        }
        return message
    }

    fun serialize(message: Message?): String {
        val writer = StringWriter()
        serialize(message, writer)
        return writer.toString()
    }

    @Throws(JsonIOException::class)
    fun serialize(message: Message?, output: Writer?) {
        gson.toJson(message, Message::class.java, output)
    }

    companion object {
        val CANCEL_METHOD: JsonRpcMethod = JsonRpcMethod.notification("$/cancelRequest", CancelParams::class.java)
        private var toStringInstance: MessageJsonHandler? = null

        /**
         * Perform JSON serialization of the given object using the default configuration of JSON-RPC messages
         * enhanced with the pretty printing option.
         */
        fun toString(`object`: Any?): String {
            if (toStringInstance == null) {
                toStringInstance = MessageJsonHandler(
                    emptyMap<String, JsonRpcMethod>(),
                    Consumer<GsonBuilder> { gsonBuilder: GsonBuilder -> gsonBuilder.setPrettyPrinting() })
            }
            return toStringInstance!!.gson.toJson(`object`)
        }
    }
}
