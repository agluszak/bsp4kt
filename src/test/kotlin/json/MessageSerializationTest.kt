package json

import com.jetbrains.jsonrpc4kt.messages.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.test.assertEquals

class MessageSerializationTest {
    /// This is a helper function to test serialization of a message using a superclass' serializer.
    @OptIn(InternalSerializationApi::class)
    fun <T : Cast, Cast : Any> validateJsonSerialization(value: T, expectedJson: JsonElement, cast: KClass<Cast>) {
        val json = Json.encodeToJsonElement(cast.serializer(), cast.cast(value))
        // Remove any null fields
        val filteredJson = when (json) {
            is JsonObject -> JsonObject(json.jsonObject.filterValues { it != JsonNull })
            else -> json
        }
        assertEquals(expectedJson, filteredJson)

        val decoded = Json.decodeFromJsonElement(cast.serializer(), json)
        assertEquals(value, decoded)
    }

    @Test
    fun `serialize number message id`() {
        val id = MessageId.NumberId(1)
        val json = JsonPrimitive(1)

        validateJsonSerialization(id, json, MessageId.NumberId::class)
        validateJsonSerialization(id, json, MessageId::class)
    }

    @Test
    fun `serialize string message id`() {
        val id = MessageId.StringId("1")
        val json = JsonPrimitive("1")

        validateJsonSerialization(id, json, MessageId.StringId::class)
        validateJsonSerialization(id, json, MessageId::class)
    }

    @Test
    fun `serialize array params`() {
        val params = JsonParams.ArrayParams(JsonArray(listOf(JsonPrimitive("1"), JsonPrimitive(2))))
        val json = buildJsonArray { add("1"); add(2) }

        validateJsonSerialization(params, json, JsonParams.ArrayParams::class)
        validateJsonSerialization(params, json, JsonParams::class)
    }

    @Test
    fun `serialize object params`() {
        val params = JsonParams.ObjectParams(buildJsonObject { put("a", "1"); put("b", 2) })
        val json = buildJsonObject { put("a", "1"); put("b", 2) }

        validateJsonSerialization(params, json, JsonParams.ObjectParams::class)
        validateJsonSerialization(params, json, JsonParams::class)
    }

    @Test
    fun `serialize notification message`() {
        val withParams =
            NotificationMessage("test", JsonParams.ObjectParams(buildJsonObject { put("a", "1"); put("b", 2) }))
        val json = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "test")
            put("params", buildJsonObject { put("a", "1"); put("b", 2) })
        }

        validateJsonSerialization(withParams, json, Message::class)

        val withoutParams = NotificationMessage("test")
        val json2 = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", "test")
        }

        validateJsonSerialization(withoutParams, json2, Message::class)
    }

    @Test
    fun `serialize request message`() {
        val withParams = RequestMessage(
            MessageId.NumberId(1),
            "test",
            JsonParams.ObjectParams(buildJsonObject { put("a", "1"); put("b", 2) })
        )
        val json = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "test")
            put("params", buildJsonObject { put("a", "1"); put("b", 2) })
        }

        validateJsonSerialization(withParams, json, Message::class)

        val withoutParams = RequestMessage(MessageId.NumberId(1), "test")
        val json2 = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "test")
        }

        validateJsonSerialization(withoutParams, json2, Message::class)
    }

    @Test
    fun `serialize response message`() {
        val result = ResponseMessage.Result(MessageId.NumberId(1), JsonPrimitive("test"))
        val resultJson = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("result", "test")
        }

        validateJsonSerialization(result, resultJson, Message::class)

        val errorWithId = ResponseMessage.Error(MessageId.NumberId(1), ResponseError(1, "test"))
        val errorWithIdJson = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("error", buildJsonObject { put("code", 1); put("message", "test") })
        }

        validateJsonSerialization(errorWithId, errorWithIdJson, Message::class)

        val errorWithoutId = ResponseMessage.Error(null, ResponseError(1, "test"))
        val errorWithoutIdJson = buildJsonObject {
            put("jsonrpc", "2.0")
            put("error", buildJsonObject { put("code", 1); put("message", "test") })
        }

        validateJsonSerialization(errorWithoutId, errorWithoutIdJson, Message::class)
    }

}