/******************************************************************************
 * Copyright (c) 2016 TypeFox and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 */
package json

import com.jetbrains.jsonrpc4kt.json.JsonRpcMethod
import com.jetbrains.jsonrpc4kt.json.MessageJsonHandler
import com.jetbrains.jsonrpc4kt.messages.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.function.Consumer
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class MessageJsonHandlerTest {
    @Serializable
    data class Entry(val name: String? = null, val location: Location? = null, val kind: Int = 0)

    @Serializable
    data class Location(val uri: String? = null, val range: Range? = null)

    @Serializable
    data class Range(val start: Position? = null, val end: Position? = null)

    @Serializable
    data class Position(val line: Int = 0, val character: Int = 0)

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `parse list`() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<List<Entry>>(),
            typeOf<List<Entry>>(),
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val message: Message = handler.deserializeMessage(
            """
                {
                 "jsonrpc":"2.0",
                 "id":"2",
                 "result": [
                   {"name":"${'$'}schema","kind":15,"location":{"uri":"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json","range":{"start":{"line":1,"character":3},"end":{"line":1,"character":55}}}},
                   {"name":"type","kind":15,"location":{"uri":"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json","range":{"start":{"line":2,"character":3},"end":{"line":2,"character":19}}}},
                   {"name":"title","kind":15,"location":{"uri":"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json","range":{"start":{"line":3,"character":3},"end":{"line":3,"character":50}}}},
                   {"name":"additionalProperties","kind":17,"location":{"uri":"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json","range":{"start":{"line":4,"character":4},"end":{"line":4,"character":32}}}},
                   {"name":"properties","kind":15,"location":{"uri":"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json","range":{"start":{"line":5,"character":3},"end":{"line":5,"character":31}}}}
                 ]
                }
            """.trimIndent()
        )
        val result = handler.deserializeResult("foo", (message as ResponseMessage.Result).result) as List<Entry>
        assertEquals(5, result.size)
        for (e: Entry in result) {
            assertTrue(e.location!!.uri!!.startsWith("file:/home/mistria"))
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `parse set`() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Set<Entry>>(),
            typeOf<Set<Entry>>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val message: Message = handler.deserializeMessage(
           """
               {
                "jsonrpc":"2.0",
                "id":"2",
                "result": [
                  {"name":"${'$'}schema","kind":15,"location":{"uri":"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json","range":{"start":{"line":1,"character":3},"end":{"line":1,"character":55}}}},
                  {"name":"type","kind":15,"location":{"uri":"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json","range":{"start":{"line":2,"character":3},"end":{"line":2,"character":19}}}},
                  {"name":"title","kind":15,"location":{"uri":"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json","range":{"start":{"line":3,"character":3},"end":{"line":3,"character":50}}}},
                  {"name":"additionalProperties","kind":17,"location":{"uri":"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json","range":{"start":{"line":4,"character":4},"end":{"line":4,"character":32}}}},
                  {"name":"properties","kind":15,"location":{"uri":"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json","range":{"start":{"line":5,"character":3},"end":{"line":5,"character":20}}}}
                ]
               }
           """.trimIndent()
        )
        val result = handler.deserializeResult("foo", (message as ResponseMessage.Result).result) as Set<Entry>
        assertEquals(5, result.size)
        for (e: Entry in result) {
            assertTrue(e.location!!.uri!!.startsWith("file:/home/mistria"))
        }
    }

    @Test
    fun `parse null result`() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<List<Entry>?>(),
            typeOf<List<Entry>>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val message: Message = handler.deserializeMessage(
            """
                {
                "jsonrpc":"2.0",
                "id":"2",
                "result": null
                }
            """.trimIndent()
        )
        val result = handler.deserializeResult("foo", (message as ResponseMessage.Result).result)
        assertNull(result)
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `parse empty list`() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<List<Entry>>(),
            typeOf<List<Entry>>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val message: Message = handler.deserializeMessage(
            """
                {"jsonrpc":"2.0",
                "id":"2",
                "result": []}
            """.trimIndent()
        )
        val result = handler.deserializeResult("foo", (message as ResponseMessage.Result).result) as List<Entry>

        assertEquals(0, result.size)
    }

    @Test
    fun `serialize notification with no params`() {
        val handler = MessageJsonHandler(Json.Default, emptyMap())
        val message = NotificationMessage("foo", null)
        val actual = Json.parseToJsonElement(handler.serializeMessage(message))

        val expected = Json.parseToJsonElement("""{"jsonrpc":"2.0","method":"foo"}""")
        assertEquals(expected, actual)
    }

    @Test
    fun `serialize notification with list params`() {
        val handler = MessageJsonHandler(
            Json.Default,
            mapOf("foo" to JsonRpcMethod.notification("foo", typeOf<String>(), typeOf<String>()))
        )
        val list = listOf("a", "b")
        val params = handler.serializeParams("foo", list)
        val message = NotificationMessage("foo", params)
        val actual = Json.parseToJsonElement(handler.serializeMessage(message))
        val expected = Json.parseToJsonElement("""{"jsonrpc":"2.0","method":"foo","params":["a","b"]}""")
        assertEquals(expected, actual)
    }

    @Test
    fun testParamsParsing_01() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<Location>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val message: RequestMessage = handler.deserializeMessage(
            ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + "\"params\": {\"uri\": \"dummy://mymodel.mydsl\"},\n"
                    + "\"method\":\"foo\"\n"
                    + "}")
        ) as RequestMessage
        val params = handler.deserializeParams(message)
        assertTrue(params[0] is Location)
    }

    // Arguments can be in any order
    @Test
    fun testParamsParsing_02() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<Location>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val message: RequestMessage = handler.deserializeMessage(
            ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + "\"method\":\"foo\",\n"
                    + "\"params\": {\"uri\": \"dummy://mymodel.mydsl\"}\n"
                    + "}")
        ) as RequestMessage
        val params = handler.deserializeParams(message)
        assertTrue(params[0] is Location)
    }

    @Test
    fun testParamsParsing_03() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<Location>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val message: RequestMessage = handler.deserializeMessage(
            """
                {"jsonrpc":"2.0",
                "id":"2",
                "method":"foo",
                "params": null}
            """.trimIndent()
        ) as RequestMessage
        assertEquals(null, message.params)
    }

    @Test
    fun testRawMultiParamsParsing_01() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<String>(),
            typeOf<Int>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val message: RequestMessage = handler.deserializeMessage(
            """
                {"jsonrpc":"2.0",
                "id":"2",
                "method":"foo",
                "params": ["foo", 2]}
            """.trimIndent()
        ) as RequestMessage
        val params = handler.deserializeParams(message)
        assertEquals(2, params.size)
        assertEquals("foo", params[0])
        assertEquals(2, params[1])
    }

    @Test
    fun testRawMultiParamsParsing_02() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["bar"] = JsonRpcMethod.request(
            "bar",
            typeOf<Void>(),
            typeOf<String>(),
            typeOf<Int>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val message: RequestMessage = handler.deserializeMessage(
            """{"jsonrpc":"2.0",
                "id":"2",
                "method":"bar",
                "params": ["foo", 2]
                }""".trimIndent()
        ) as RequestMessage
        val params = handler.deserializeParams(message)
        assertTrue(params[0] is String)
        assertTrue(params[1] is Int)
    }

    @Test
    fun testRawMultiParamsParsing_03() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<List<String>>(),
            typeOf<List<Int>>(),
            typeOf<Location>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val message: RequestMessage = handler.deserializeMessage(
            """{"jsonrpc":"2.0",
                "id":"2",
                "method":"foo",
                "params": [["foo", "bar"], [1, 2], {"uri": "dummy://mymodel.mydsl"}]
                }""".trimIndent()
        ) as RequestMessage
        val params = handler.deserializeParams(message)
        assertEquals(3, params.size)
        assertEquals("[foo, bar]", params[0].toString())
        assertEquals("[1, 2]", params[1].toString())
        assertTrue(params[2] is Location)
    }

    @Test
    fun testRawMultiParamsParsing_04() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<List<String>>(),
            typeOf<List<Int>>(),
            typeOf<Location?>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val message: RequestMessage = handler.deserializeMessage(
            """{"jsonrpc":"2.0",
                "id":"2",
                "method":"foo",
                "params": [["foo", "bar"], [1, 2], null]
                }""".trimIndent()
        ) as RequestMessage

        val params = handler.deserializeParams(message)
        assertEquals(3, params.size)
        assertEquals("[foo, bar]", params[0].toString())
        assertEquals("[1, 2]", params[1].toString())
        assertNull(params[2])
    }

    @Test
    fun testMultiParamsParsing_01() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<String>(),
            typeOf<Int>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val message: RequestMessage = handler.deserializeMessage(
            """{"jsonrpc":"2.0",
                "id":"2",
                "method":"foo",
                "params": ["foo", 2]
                }""".trimIndent()
        ) as RequestMessage
        val params = handler.deserializeParams(message)
        assertEquals(2, params.size)
        assertEquals("foo", params[0])
        assertEquals(2, params[1])
    }

    @Test
    fun testMultiParamsParsing_02() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["bar"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<String>(),
            typeOf<Int>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        val message: RequestMessage = handler.deserializeMessage(
            """
                {"jsonrpc":"2.0",
                "id":"2",
                "params": ["foo", 2],
                "method":"bar"}
            """.trimIndent()
        ) as RequestMessage
        val params = handler.deserializeParams(message)
        assertTrue(params[0] is String)
        assertTrue(params[1] is Int)
    }

    @Test
    fun testMultiParamsParsing_03() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<List<String>>(),
            typeOf<List<Int>>(),
            typeOf<Location>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val message: RequestMessage = handler.deserializeMessage(
            """
                {"jsonrpc":"2.0",
                "id":"2",
                "method":"foo",
                "params": [["foo", "bar"], [1, 2], {"uri": "dummy://mymodel.mydsl"}]}
            """.trimIndent()
        ) as RequestMessage

        val params = handler.deserializeParams(message)
        assertEquals(3, params.size)
        assertEquals("[foo, bar]", params[0].toString())
        assertEquals("[1, 2]", params[1].toString())
        assertTrue(params[2] is Location)
    }

    @Test
    fun testMultiParamsParsing_04() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<List<String>>(),
            typeOf<List<Int>>(),
            typeOf<Location?>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val message: RequestMessage = handler.deserializeMessage(
            """
                {"jsonrpc":"2.0",
                "id":"2",
                "method":"foo",
                "params": [["foo", "bar"], [1, 2], null]}
            """.trimIndent()
        ) as RequestMessage

        val params = handler.deserializeParams(message)
        assertEquals(3, params.size)
        assertEquals("[foo, bar]", params[0].toString())
        assertEquals("[1, 2]", params[1].toString())
        assertNull(params[2])
    }

    @Test
    fun testEnumParam() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<List<MyEnum>>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val message: RequestMessage = handler.deserializeMessage(
            """
                {"jsonrpc":"2.0",
                "id":"2",
                "params": [[1, 2, 3]],
                "method":"foo"}
            """.trimIndent()
        ) as RequestMessage

        val params = handler.deserializeParams(message)
        assertEquals(
            listOf(
                listOf(MyEnum.A, MyEnum.B, MyEnum.C)
            ),
            params
        )
    }

    @Test
    fun testEnumParamNull() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<List<MyEnum?>>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val message: RequestMessage = handler.deserializeMessage(
            """
                {"jsonrpc":"2.0",
                "id":"2",
                "params": [[1, 2, null]],
                "method":"foo"}
            """.trimIndent()
        ) as RequestMessage

        val params = handler.deserializeParams(message)
        assertEquals(
            listOf(
                listOf(MyEnum.A, MyEnum.B, null)
            ),
            params
        )
    }

    @Test
    fun testResponseErrorData() {
        val handler = MessageJsonHandler(Json.Default, emptyMap())
        val message = handler.deserializeMessage(
            """
                {"jsonrpc":"2.0",
                "id":"2",
                "error": {
                    "code": 123,
                    "message": "foo",
                    "data": {
                        "uri": "file:/foo",
                        "version": 5,
                        "list": ["a", "b", "c"]
                    }
                }}
            """.trimIndent()
        ) as ResponseMessage.Error
        val error: ResponseError = message.error
        assertTrue(error.data is JsonObject, "Expected a JsonObject in error.data")
        val data: JsonObject = error.data as JsonObject
        assertEquals("file:/foo", data["uri"]!!.jsonPrimitive.content)
        assertEquals(5, data["version"]!!.jsonPrimitive.int)
        val list: JsonArray = data["list"]!!.jsonArray
        assertEquals("a", list[0].jsonPrimitive.content)
        assertEquals("b", list[1].jsonPrimitive.content)
        assertEquals("c", list[2].jsonPrimitive.content)
    }

    fun <T> testAllPermutationsInner(array: Array<T>, i: Int, n: Int, test: Consumer<Array<T>>) {
        var j: Int
        if (i == n) {
            test.accept(array)
        } else {
            j = i
            while (j <= n) {
                swap(array, i, j)
                testAllPermutationsInner(array, i + 1, n, test)
                swap(array, i, j)
                j++
            }
        }
    }

    fun <T> testAllPermutationsStart(array: Array<T>, test: Consumer<Array<T>>) {
        testAllPermutationsInner(array, 0, array.size - 1, test)
    }

    fun testAllPermutations(properties: Array<String>, test: Consumer<String>) {
        testAllPermutationsStart(properties) { mutatedProperties: Array<String> ->
            val json: StringBuilder = StringBuilder()
            json.append("{")
            for (k in mutatedProperties.indices) {
                json.append(mutatedProperties[k])
                if (k != mutatedProperties.size - 1) {
                    json.append(",")
                }
            }
            json.append("}")
            val jsonString: String = json.toString()
            try {
                test.accept(jsonString)
            } catch (e: Exception) {
                // To make it easier to debug a failing test, add another exception
                // layer that shows the version of the json used
                throw AssertionError("Failed with this input json: $jsonString", e)
            } catch (e: AssertionError) {
                throw AssertionError("Failed with this input json: $jsonString", e)
            }
        }
    }

    @Test
    fun `test testAllPermutations`() {
        // make sure that the testAllPermutations works as expected
        val collectedPermutations: MutableSet<String> = HashSet()
        val expectedPermutations: MutableSet<String> = HashSet()
        expectedPermutations.add("{a,b,c}")
        expectedPermutations.add("{a,c,b}")
        expectedPermutations.add("{b,a,c}")
        expectedPermutations.add("{b,c,a}")
        expectedPermutations.add("{c,a,b}")
        expectedPermutations.add("{c,b,a}")
        testAllPermutations(
            arrayOf("a", "b", "c")
        ) { perm: String -> collectedPermutations.add(perm) }
        assertEquals(expectedPermutations, collectedPermutations)
    }

    @Test
    fun `parse request json in any order`() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<Location>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val properties = arrayOf(
            "\"jsonrpc\":\"2.0\"",
            "\"id\":2",
            "\"method\":\"foo\"",
            "\"params\": {\"uri\": \"dummy://mymodel.mydsl\"}"
        )
        testAllPermutations(properties) { json: String ->
            val message: RequestMessage = handler.deserializeMessage(json) as RequestMessage
            val params = handler.deserializeParams(message)

            assertEquals(
                "dummy://mymodel.mydsl",
                (params[0] as Location).uri
            )
        }
    }

    @Test
    fun `parse response json in any order`() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Location>(),
            typeOf<Void>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val properties = arrayOf(
            "\"jsonrpc\":\"2.0\"",
            "\"id\":2",
            "\"result\": {\"uri\": \"dummy://mymodel.mydsl\"}"
        )
        testAllPermutations(properties) { json: String ->
            val message = handler.deserializeMessage(json) as ResponseMessage.Result
            val result = handler.deserializeResult("foo", message.result) as Location

            assertEquals(
                "dummy://mymodel.mydsl",
                result.uri
            )
        }
    }

    @Test
    fun `parse error response json in any order`() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Location>(),
            typeOf<Void>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val properties = arrayOf(
            "\"jsonrpc\":\"2.0\"",
            "\"id\":2",
            "\"error\": {\"code\": 123456, \"message\": \"failed\", \"data\": {\"uri\": \"failed\"}}"
        )
        testAllPermutations(properties) { json: String ->
            val message = handler.deserializeMessage(json) as ResponseMessage.Error
            assertEquals("failed", message.error.message)
            val data: Any = message.error.data!!
            val expected: JsonObject = buildJsonObject { put("uri", JsonPrimitive("failed")) }
            assertEquals(expected, data)
        }
    }

    @Test
    fun `parse notification json in any order`() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<Location>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)

        val properties = arrayOf<String>(
            "\"jsonrpc\":\"2.0\"",
            "\"method\":\"foo\"",
            "\"params\": {\"uri\": \"dummy://mymodel.mydsl\"}"
        )
        testAllPermutations(properties) { json: String ->
            val message: NotificationMessage = handler.deserializeMessage(json) as NotificationMessage
            val params = handler.deserializeParams(message)

            assertEquals(
                "dummy://mymodel.mydsl",
                (params[0] as Location).uri
            )
        }
    }

    @Test
    fun `serialize array params with a single element`() {
        val handler: MessageJsonHandler = createSimpleRequestHandler(
            typeOf<String>(),
            typeOf<String>()
        )
        val request =
            RequestMessage(
                MessageId.NumberId(1),
                "testMethod",
                JsonParams.ArrayParams(JsonArray(listOf(JsonPrimitive("param"))))
            )

        val expected = Json.parseToJsonElement(
            """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "testMethod",
                "params": [
                    "param"
                ]
            }
        """.trimIndent()
        )
        assertEquals(expected, Json.parseToJsonElement(handler.serializeMessage(request)))
    }

    @Test
    fun `parse array params with a single element`() {
        val handler: MessageJsonHandler = createSimpleRequestHandler(
            typeOf<String>(),
            typeOf<String>()
        )

        val request = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "testMethod",
                "params": [
                    "param"
                ]
            }
        """.trimIndent()
        // Check parse - unwrap primitive
        val message: RequestMessage = handler.deserializeMessage(request) as RequestMessage
        val params = handler.deserializeParams(message)
        assertEquals(listOf("param"), params)
    }

    @Test
    fun `wrap array`() {
        val handler: MessageJsonHandler =
            createSimpleRequestHandler(typeOf<String>(), typeOf<List<Boolean>>())
        val request = RequestMessage(
            MessageId.NumberId(1),
            "testMethod",
            JsonParams.array(
                JsonArray(
                    listOf(JsonPrimitive(true), JsonPrimitive(false))
                )
            )
        )

        val expected = Json.parseToJsonElement(
            """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "testMethod",
                "params": [
                    [
                        true,
                        false
                    ]
                ]
            }
        """.trimIndent()
        )

        assertEquals(expected, Json.parseToJsonElement(handler.serializeMessage(request)))
    }

    @Test
    fun `unwrap array`() {
        val handler: MessageJsonHandler =
            createSimpleRequestHandler(typeOf<String>(), typeOf<List<Boolean>>())
        val request = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "testMethod",
                "params": [
                    [
                        true,
                        false
                    ]
                ]
            }
        """.trimIndent()
        val message: RequestMessage = handler.deserializeMessage(request) as RequestMessage

        // Check parse - unwrap array
        val params = handler.deserializeParams(message)
        assertEquals(listOf(listOf(true, false)), params)
    }

    @Test
    fun `serialize array params`() {
        val handler: MessageJsonHandler = createSimpleRequestHandler(
            typeOf<String>(),
            typeOf<Boolean>(),
            typeOf<String>()
        )

        val request = RequestMessage(
            MessageId.NumberId(1),
            "testMethod",
            JsonParams.array(JsonPrimitive(true), JsonPrimitive("param2"))
        )

        val expected =
            Json.parseToJsonElement("""{"jsonrpc":"2.0","id":1,"method":"testMethod","params":[true,"param2"]}""")
        val actual = Json.parseToJsonElement(handler.serializeMessage(request))
        assertEquals(
            expected,
            actual
        )
    }

    @Test
    fun `deserialize array params`() {
        val handler: MessageJsonHandler = createSimpleRequestHandler(
            typeOf<String>(),
            typeOf<Boolean>(),
            typeOf<String>()
        )

        val request = """{
            "jsonrpc": "2.0",
            "id": 1,
            "method": "testMethod",
            "params": [
                true,
                "param2"
            ]
        }"""
        val message: RequestMessage = handler.deserializeMessage(request) as RequestMessage

        // Check parse - unwrap array
        val params = handler.deserializeParams(message)
        assertEquals(listOf(true, "param2"), params)
    }

    @Test
    fun `serialize array params with a list param`() {
        val handler: MessageJsonHandler = createSimpleRequestHandler(
            typeOf<String>(), typeOf<List<Boolean>>(),
            typeOf<String>()
        )
        val request = RequestMessage(
            MessageId.NumberId(1),
            "testMethod",
            JsonParams.array(JsonArray(listOf(JsonPrimitive(true), JsonPrimitive(false))), JsonPrimitive("param2"))
        )


        val expected =
            Json.parseToJsonElement("""{"jsonrpc":"2.0","id":1,"method":"testMethod","params":[[true,false],"param2"]}""")
        val actual = Json.parseToJsonElement(handler.serializeMessage(request))
        assertEquals(
            expected,
            actual
        )
    }

    @Test
    fun `deserialize array params with a list param`() {
        val handler: MessageJsonHandler = createSimpleRequestHandler(
            typeOf<String>(), typeOf<List<Boolean>>(),
            typeOf<String>()
        )

        val request = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "testMethod",
                "params": [
                    [
                        true,
                        false
                    ],
                    "param2"
                ]
            }
        """
        val message: RequestMessage = handler.deserializeMessage(request) as RequestMessage

        // Check parse - unwrap array
        val params = handler.deserializeParams(message)
        assertEquals(listOf(listOf(true, false), "param2"), params)
    }

    companion object {
        fun <T> swap(a: Array<T>, i: Int, j: Int) {
            val t = a[i]
            a[i] = a[j]
            a[j] = t
        }

        private fun createSimpleRequestHandler(returnType: KType, vararg paramType: KType): MessageJsonHandler {
            val requestMethod: JsonRpcMethod = JsonRpcMethod.request("testMethod", returnType, *paramType)
            return MessageJsonHandler(Json, mapOf(Pair(requestMethod.methodName, requestMethod)))
        }
    }
}
