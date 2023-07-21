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

import com.jetbrains.bsp.json.JsonRpcMethod
import com.jetbrains.bsp.json.MessageJsonHandler
import com.jetbrains.bsp.json.MethodProvider
import com.jetbrains.bsp.messages.*
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
    fun testParseList_01() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<List<Entry>>(),
            typeOf<List<Entry>>(),
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val message: Message = handler.parseMessage(
            "{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + " \"result\": [\n"
                    + "  {\"name\":\"\$schema\",\"kind\":15,\"location\":{\"uri\":\"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json\",\"range\":{\"start\":{\"line\":1,\"character\":3},\"end\":{\"line\":1,\"character\":55}}}},\n"
                    + "  {\"name\":\"type\",\"kind\":15,\"location\":{\"uri\":\"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json\",\"range\":{\"start\":{\"line\":2,\"character\":3},\"end\":{\"line\":2,\"character\":19}}}},\n"
                    + "  {\"name\":\"title\",\"kind\":15,\"location\":{\"uri\":\"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json\",\"range\":{\"start\":{\"line\":3,\"character\":3},\"end\":{\"line\":3,\"character\":50}}}},\n"
                    + "  {\"name\":\"additionalProperties\",\"kind\":17,\"location\":{\"uri\":\"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json\",\"range\":{\"start\":{\"line\":4,\"character\":4},\"end\":{\"line\":4,\"character\":32}}}},\n"
                    + "  {\"name\":\"properties\",\"kind\":15,\"location\":{\"uri\":\"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json\",\"range\":{\"start\":{\"line\":5,\"character\":3},\"end\":{\"line\":5,\"character\":20}}}}\n"
                    + "]}"
        )
        val result = handler.deserializeResult("foo", (message as ResponseMessage.Result).result) as List<Entry>
        assertEquals(5, result.size)
        for (e: Entry in result) {
            assertTrue(e.location!!.uri!!.startsWith("file:/home/mistria"))
        }
    }

    @Test
    fun testParseList_02() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Set<Entry>>(),
            typeOf<Set<Entry>>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val message: Message = handler.parseMessage(
            ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + " \"result\": [\n"
                    + "  {\"name\":\"\$schema\",\"kind\":15,\"location\":{\"uri\":\"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json\",\"range\":{\"start\":{\"line\":1,\"character\":3},\"end\":{\"line\":1,\"character\":55}}}},\n"
                    + "  {\"name\":\"type\",\"kind\":15,\"location\":{\"uri\":\"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json\",\"range\":{\"start\":{\"line\":2,\"character\":3},\"end\":{\"line\":2,\"character\":19}}}},\n"
                    + "  {\"name\":\"title\",\"kind\":15,\"location\":{\"uri\":\"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json\",\"range\":{\"start\":{\"line\":3,\"character\":3},\"end\":{\"line\":3,\"character\":50}}}},\n"
                    + "  {\"name\":\"additionalProperties\",\"kind\":17,\"location\":{\"uri\":\"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json\",\"range\":{\"start\":{\"line\":4,\"character\":4},\"end\":{\"line\":4,\"character\":32}}}},\n"
                    + "  {\"name\":\"properties\",\"kind\":15,\"location\":{\"uri\":\"file:/home/mistria/runtime-EclipseApplication-with-patch/EclipseConEurope/something.json\",\"range\":{\"start\":{\"line\":5,\"character\":3},\"end\":{\"line\":5,\"character\":20}}}}\n"
                    + "]}")
        )
        val result = handler.deserializeResult("foo", (message as ResponseMessage.Result).result) as Set<Entry>
        assertEquals(5, result.size)
        for (e: Entry in result) {
            assertTrue(e.location!!.uri!!.startsWith("file:/home/mistria"))
        }
    }

    @Test
    fun testParseNullList() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<List<Entry>?>(),
            typeOf<List<Entry>>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val message: Message = handler.parseMessage(
            ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + " \"result\": null}")
        )
        val result = handler.deserializeResult("foo", (message as ResponseMessage.Result).result)
        assertNull(result)
    }

    @Test
    fun testParseEmptyList() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<List<Entry>>(),
            typeOf<List<Entry>>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val message: Message = handler.parseMessage(
            ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + " \"result\": []}")
        )
        val result = handler.deserializeResult("foo", (message as ResponseMessage.Result).result) as List<Entry>

        assertEquals(0, result.size)
    }

    @Test
    fun `serialize notification with no params`() {
        val handler = MessageJsonHandler(Json.Default, emptyMap())
        val message = NotificationMessage("foo", null)
        val actual =  Json.parseToJsonElement(handler.serialize(message))

        val expected = Json.parseToJsonElement("""{"jsonrpc":"2.0","method":"foo"}""")
        assertEquals(expected, actual)
    }

//    @Test
//    fun testSerializeImmutableList() {
//        val handler = MessageJsonHandler(Json.Default, emptyMap())
//        val list = listOf("a", "b")
//        handler.serializeParams("foo", list)
//        val message = NotificationMessage("foo", list)
//        val json: String = handler.serialize(message)
//        assertEquals("{\"jsonrpc\":\"2.0\",\"method\":\"foo\",\"params\":[\"a\",\"b\"]}", json)
//    }
//
//    @Test
//    fun testEither_01() {
//        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
//        supportedMethods["foo"] = JsonRpcMethod.request("foo",
//            typeOf<Either<String, List<Map<String, String>>>>(),
//            typeOf<Either<String, Int>>()
//        )
//        val handler = MessageJsonHandler(Json.Default, supportedMethods)
//        handler.methodProvider = MethodProvider { "foo" }
//        var message: Message = handler.parseMessage(
//            ("{\"jsonrpc\":\"2.0\","
//                    + "\"id\":\"2\",\n"
//                    + " \"result\": [\n"
//                    + "  {\"name\":\"foo\"},\n"
//                    + "  {\"name\":\"bar\"}\n"
//                    + "]}")
//        )
//        var result: Either<String, List<Map<String, String>>> =
//            (message as ResponseMessage).result as Either<String, List<Map<String, String>>>
//        assertTrue(result.isRight())
//        for (e: Map<String, String> in result.getOrNull()!!) {
//            assertNotNull(e["name"])
//        }
//        message = handler.parseMessage(
//            ("{\"jsonrpc\":\"2.0\","
//                    + "\"id\":\"2\",\n"
//                    + "\"result\": \"name\"\n"
//                    + "}")
//        )
//        result = (message as ResponseMessage).result as Either<String, List<Map<String, String>>>
//        assertFalse(result.isRight())
//        assertEquals("name", result.leftOrNull()!!)
//    }
//
//    @Test
//    fun testEither_02() {
//        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
//        supportedMethods["foo"] = JsonRpcMethod.request("foo",
//            typeOf<Either<MyEnum, Map<String, String>>>(),
//            typeOf<Any>()
//        )
//        val handler = MessageJsonHandler(Json.Default, supportedMethods)
//        handler.methodProvider = MethodProvider { "foo" }
//        val message: Message = handler.parseMessage(
//            ("{\"jsonrpc\":\"2.0\","
//                    + "\"id\":\"2\",\n"
//                    + "\"result\": 2\n"
//                    + "}")
//        )
//        val result: Either<MyEnum, List<Map<String, String>>> =
//            (message as ResponseMessage).result as Either<MyEnum, List<Map<String, String>>>
//        assertTrue(result.isLeft())
//        assertEquals(MyEnum.B, result.leftOrNull()!!)
//    }
//
//    @Test
//    fun testEither_03() {
//        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
//        supportedMethods["foo"] = JsonRpcMethod.request("foo",
//            typeOf<Either<Either<MyEnum, Map<String, String>>, List<Either<MyEnum, Map<String, String>>>>>(),
//            typeOf<Any>()
//        )
//        val handler = MessageJsonHandler(Json.Default, supportedMethods)
//        handler.methodProvider = MethodProvider { "foo" }
//        var message: Message = handler.parseMessage(
//            ("{\"jsonrpc\":\"2.0\","
//                    + "\"id\":\"2\",\n"
//                    + "\"result\": 2\n"
//                    + "}")
//        )
//        var result: Either<Either<MyEnum, Map<String, String>>, List<Either<MyEnum, Map<String, String>>>> =
//            (message as ResponseMessage).result as Either<Either<MyEnum, Map<String, String>>, List<Either<MyEnum, Map<String, String>>>>
//        assertTrue(result.isLeft())
//        assertTrue(result.leftOrNull()!!.isLeft())
//        assertEquals(MyEnum.B, result.leftOrNull()!!.leftOrNull()!!)
//        message = handler.parseMessage(
//            ("{\"jsonrpc\":\"2.0\","
//                    + "\"id\":\"2\",\n"
//                    + " \"result\": {\n"
//                    + "  \"foo\":\"1\",\n"
//                    + "  \"bar\":\"2\"\n"
//                    + "}}")
//        )
//        result =
//            (message as ResponseMessage).result as Either<Either<MyEnum, Map<String, String>>, List<Either<MyEnum, Map<String, String>>>>
//        assertTrue(result.isLeft())
//        assertTrue(result.leftOrNull()!!.isRight())
//        assertEquals("1", result.leftOrNull()!!.getOrNull()!!.get("foo"))
//        assertEquals("2", result.leftOrNull()!!.getOrNull()!!.get("bar"))
//        message = handler.parseMessage(
//            ("{\"jsonrpc\":\"2.0\","
//                    + "\"id\":\"2\",\n"
//                    + " \"result\": [{\n"
//                    + "  \"foo\":\"1\",\n"
//                    + "  \"bar\":\"2\"\n"
//                    + "}]}")
//        )
//        result =
//            (message as ResponseMessage).result as Either<Either<MyEnum, Map<String, String>>, List<Either<MyEnum, Map<String, String>>>>
//        assertTrue(result.isRight())
//        assertTrue(result.getOrNull()!!.get(0).isRight())
//        assertEquals("1", result.getOrNull()!!.get(0).getOrNull()!!.get("foo"))
//        assertEquals("2", result.getOrNull()!!.get(0).getOrNull()!!.get("bar"))
//        message = handler.parseMessage(
//            ("{\"jsonrpc\":\"2.0\","
//                    + "\"id\":\"2\",\n"
//                    + " \"result\": [\n"
//                    + "  2\n"
//                    + "]}")
//        )
//        result =
//            (message as ResponseMessage).result as Either<Either<MyEnum, Map<String, String>>, List<Either<MyEnum, Map<String, String>>>>
//        assertTrue(result.isRight())
//        assertTrue(result.getOrNull()!!.get(0).isLeft())
//        assertEquals(MyEnum.B, result.getOrNull()!!.get(0).leftOrNull()!!)
//    }
//
//    @Test
//    fun testEither_04() {
//        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
//        supportedMethods["foo"] = JsonRpcMethod.request("foo",
//            typeOf<Either<MyClass, List<MyClass>>>(),
//            typeOf<Any>()
//        )
//        val handler = MessageJsonHandler(Json.Default, supportedMethods)
//        handler.methodProvider = MethodProvider { "foo" }
//        var message: Message = handler.parseMessage(
//            ("{\"jsonrpc\":\"2.0\","
//                    + "\"id\":\"2\",\n"
//                    + "\"result\": {\n"
//                    + "  value:\"foo\"\n"
//                    + "}}")
//        )
//        var result: Either<MyClass, List<MyClass>> =
//            (message as ResponseMessage).result as Either<MyClass, List<MyClass>>
//        assertTrue(result.isLeft())
//        assertEquals("foo", result.leftOrNull()!!.value)
//        message = handler.parseMessage(
//            ("{\"jsonrpc\":\"2.0\","
//                    + "\"id\":\"2\",\n"
//                    + "\"result\": [{\n"
//                    + "  value:\"bar\"\n"
//                    + "}]}")
//        )
//        result = (message as ResponseMessage).result as Either<MyClass, List<MyClass>>
//        assertTrue(result.isRight())
//        assertEquals("bar", result.getOrNull()!!.get(0).value)
//    }
//
//    @Test
//    fun testEither_05() {
//        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
//        supportedMethods["foo"] = JsonRpcMethod.request("foo",
//            typeOf<Either<List<MyClass>, MyClassList>>(),
//            typeOf<Any>()
//        )
//        val handler = MessageJsonHandler(Json.Default, supportedMethods)
//        handler.methodProvider = MethodProvider { "foo" }
//        var message: Message = handler.parseMessage(
//            ("{\"jsonrpc\":\"2.0\","
//                    + "\"id\":\"2\",\n"
//                    + "\"result\": [{\n"
//                    + "  value:\"foo\"\n"
//                    + "}]}")
//        )
//        var result: Either<List<MyClass>, MyClassList> =
//            (message as ResponseMessage).result as Either<List<MyClass>, MyClassList>
//        assertTrue(result.isLeft())
//        assertEquals("foo", result.leftOrNull()!!.get(0).value)
//        message = handler.parseMessage(
//            ("{\"jsonrpc\":\"2.0\","
//                    + "\"id\":\"2\",\n"
//                    + "\"result\": {\n"
//                    + "  items: [{\n"
//                    + "    value:\"bar\"\n"
//                    + "}]}}")
//        )
//        result = (message as ResponseMessage).result as Either<List<MyClass>, MyClassList>
//        assertTrue(result.isRight())
//        assertEquals("bar", result.getOrNull()!!.items.get(0).value)
//    }

    @Test
    fun testParamsParsing_01() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<Location>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val message: RequestMessage = handler.parseMessage(
            ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + "\"params\": {\"uri\": \"dummy://mymodel.mydsl\"},\n"
                    + "\"method\":\"foo\"\n"
                    + "}")
        ) as RequestMessage
        val params = handler.deserializeParams("foo", message.params)
        assertTrue(params[0] is Location)
    }

    // Arguments can be in any order
    @Test
    fun testParamsParsing_02() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<Location>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val message: RequestMessage = handler.parseMessage(
            ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + "\"method\":\"foo\",\n"
                    + "\"params\": {\"uri\": \"dummy://mymodel.mydsl\"}\n"
                    + "}")
        ) as RequestMessage
        val params = handler.deserializeParams("foo", message.params)
        assertTrue(params[0] is Location)
    }

//    // Parameters are parsed as JsonObject if the method is not known
//    @Test
//    fun testParamsParsing_03() {
//        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
//        supportedMethods["foo"] = JsonRpcMethod.request("foo",
//            typeOf<Void>(),
//            typeOf<Location>()
//        )
//        val handler = MessageJsonHandler(Json.Default, supportedMethods)
//        handler.methodProvider = MethodProvider { "foo" }
//        val message: RequestMessage = handler.parseMessage(
//            ("{\"jsonrpc\":\"2.0\","
//                    + "\"id\":\"2\",\n"
//                    + "\"method\":\"bar\",\n"
//                    + "\"params\": {\"uri\": \"dummy://mymodel.mydsl\"}\n"
//                    + "}")
//        ) as RequestMessage
//        val params = handler.deserializeParams("foo", params)
//        assertTrue(params is JsonObject)
//    }

    @Test
    fun testParamsParsing_04() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<Location>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val message: RequestMessage = handler.parseMessage(
            ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + "\"method\":\"bar\",\n"
                    + "\"params\": null\n"
                    + "}")
        ) as RequestMessage
        assertEquals(null, message.params)
    }

    @Test
    fun testRawMultiParamsParsing_01() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<String>(),
            typeOf<Int>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val message: RequestMessage = handler.parseMessage(
            ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + "\"method\":\"foo\",\n"
                    + "\"params\": [\"foo\", 2]\n"
                    + "}")
        ) as RequestMessage
        val params = handler.deserializeParams("foo", message.params)
        assertEquals(2, params.size)
        assertEquals("foo", params[0])
        assertEquals(2, params[1])
    }

    @Test
    fun testRawMultiParamsParsing_02() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<String>(),
            typeOf<Int>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val message: RequestMessage = handler.parseMessage(
            ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + "\"method\":\"bar\",\n"
                    + "\"params\": [\"foo\", 2]\n"
                    + "}")
        ) as RequestMessage
        val params = handler.deserializeParams("foo", message.params)
        assertTrue(params[0] is String)
        assertTrue(params[1] is Int)
    }

    @Test
    fun testRawMultiParamsParsing_03() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<List<String>>(),
            typeOf<List<Int>>(),
            typeOf<Location>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val message: RequestMessage = handler.parseMessage(
            ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + "\"method\":\"foo\",\n"
                    + "\"params\": [[\"foo\", \"bar\"], [1, 2], {\"uri\": \"dummy://mymodel.mydsl\"}]\n"
                    + "}")
        ) as RequestMessage
        val params = handler.deserializeParams("foo", message.params)
        assertEquals(3, params.size)
        assertEquals("[foo, bar]", params[0].toString())
        assertEquals("[1, 2]", params[1].toString())
        assertTrue(params[2] is Location)
    }

    @Test
    fun testRawMultiParamsParsing_04() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<List<String>>(),
            typeOf<List<Int>>(),
            typeOf<Location?>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val message: RequestMessage = handler.parseMessage(
            ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + "\"method\":\"foo\",\n"
                    + "\"params\": [[\"foo\", \"bar\"], [1, 2]]\n"
                    + "}")
        ) as RequestMessage

        val params = handler.deserializeParams("foo", message.params)
        assertEquals(3, params.size)
        assertEquals("[foo, bar]", params[0].toString())
        assertEquals("[1, 2]", params[1].toString())
        assertNull(params[2])
    }

    @Test
    fun testMultiParamsParsing_01() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<String>(),
            typeOf<Int>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val message: RequestMessage = handler.parseMessage(
            ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + "\"params\": [\"foo\", 2],\n"
                    + "\"method\":\"foo\"\n"
                    + "}")
        ) as RequestMessage
        val params = handler.deserializeParams("foo", message.params)
        assertEquals(2, params.size)
        assertEquals("foo", params[0])
        assertEquals(2, params[1])
    }

    @Test
    fun testMultiParamsParsing_02() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<String>(),
            typeOf<Int>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val message: RequestMessage = handler.parseMessage(
            ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + "\"params\": [\"foo\", 2],\n"
                    + "\"method\":\"bar\"\n"
                    + "}")
        ) as RequestMessage
        val params = handler.deserializeParams("foo", message.params)
        assertTrue(params[0] is String)
        assertTrue(params[1] is Int)
    }

    @Test
    fun testMultiParamsParsing_03() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<List<String>>(),
            typeOf<List<Int>>(),
            typeOf<Location>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val message: RequestMessage = handler.parseMessage(
            ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + "\"params\": [[\"foo\", \"bar\"], [1, 2], {\"uri\": \"dummy://mymodel.mydsl\"}],\n"
                    + "\"method\":\"foo\"\n"
                    + "}")
        ) as RequestMessage

        val params = handler.deserializeParams("foo", message.params)
        assertEquals(3, params.size)
        assertEquals("[foo, bar]", params[0].toString())
        assertEquals("[1, 2]", params[1].toString())
        assertTrue(params[2] is Location)
    }

    @Test
    fun testMultiParamsParsing_04() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<List<String>>(),
            typeOf<List<Int>>(),
            typeOf<Location?>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val message: RequestMessage = handler.parseMessage(
            ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + "\"params\": [[\"foo\", \"bar\"], [1, 2]],\n"
                    + "\"method\":\"foo\"\n"
                    + "}")
        ) as RequestMessage

        val params = handler.deserializeParams("foo", message.params)
        assertEquals(3, params.size)
        assertEquals("[foo, bar]", params[0].toString())
        assertEquals("[1, 2]", params[1].toString())
        assertNull(params[2])
    }

    @Test
    fun testEnumParam() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<List<MyEnum>>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val message: RequestMessage = handler.parseMessage(
            ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + "\"params\": [[1, 2, 3]],\n"
                    + "\"method\":\"foo\"\n"
                    + "}")
        ) as RequestMessage

        val params = handler.deserializeParams("foo", message.params)
        assertEquals(
            listOf(
            listOf(MyEnum.A, MyEnum.B, MyEnum.C)),
            params
        )
    }

    @Test
    fun testEnumParamNull() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<List<MyEnum?>>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val message: RequestMessage = handler.parseMessage(
            ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + "\"params\": [[1, 2, null]],\n"
                    + "\"method\":\"foo\"\n"
                    + "}")
        ) as RequestMessage

        val params = handler.deserializeParams("foo", message.params)
        assertEquals(
            listOf(
            listOf(MyEnum.A, MyEnum.B, null)),
            params
        )
    }

    @Test
    fun testResponseErrorData() {
        val handler = MessageJsonHandler(Json.Default, emptyMap())
        val message = handler.parseMessage(
            ("{\"jsonrpc\":\"2.0\","
                    + "\"id\":\"2\",\n"
                    + "\"error\": { \"code\": -32001, \"message\": \"foo\",\n"
                    + "    \"data\": { \"uri\": \"file:/foo\", \"version\": 5, \"list\": [\"a\", \"b\", \"c\"] }\n"
                    + "  }\n"
                    + "}")
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
                json.append(mutatedProperties.get(k))
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
                // layer that shows the version of the json used -- you may
                // need to turn off "Filter Stack Trace" in JUnit view in Eclipse
                // to see the underlying error.
                throw AssertionError("Failed with this input json: " + jsonString, e)
            } catch (e: AssertionError) {
                throw AssertionError("Failed with this input json: " + jsonString, e)
            }
        }
    }

    @Test
    fun testThePermutationsTest() {
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
    fun testRequest_AllOrders() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<Location>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val properties = arrayOf<String>(
            "\"jsonrpc\":\"2.0\"",
            "\"id\":2",
            "\"method\":\"foo\"",
            "\"params\": {\"uri\": \"dummy://mymodel.mydsl\"}"
        )
        testAllPermutations(properties) { json: String ->
            val message: RequestMessage = handler.parseMessage(json) as RequestMessage
            val params = handler.deserializeParams("foo", message.params)

            assertEquals(
                "dummy://mymodel.mydsl",
                (params[0] as Location).uri
            )
        }
    }

    @Test
    fun testNormalResponse_AllOrders() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Location>(),
            typeOf<Void>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val properties = arrayOf<String>(
            "\"jsonrpc\":\"2.0\"",
            "\"id\":2",
            "\"result\": {\"uri\": \"dummy://mymodel.mydsl\"}"
        )
        testAllPermutations(properties) { json: String ->
            val message = handler.parseMessage(json) as ResponseMessage.Result
            val result = handler.deserializeResult("foo", message.result) as Location

            assertEquals(
                "dummy://mymodel.mydsl",
                (result as Location).uri
            )
        }
    }

    @Test
    fun testErrorResponse_AllOrders() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Location>(),
            typeOf<Void>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val properties = arrayOf<String>(
            "\"jsonrpc\":\"2.0\"",
            "\"id\":2",
            "\"message\": \"failed\"",
            "\"error\": {\"code\": 123456, \"message\": \"failed\", \"data\": {\"uri\": \"failed\"}}"
        )
        testAllPermutations(properties) { json: String ->
            val message = handler.parseMessage(json) as ResponseMessage.Error
            assertEquals("failed", message.error.message)
            val data: Any = message.error.data!!
            val expected: JsonObject = buildJsonObject { put("uri", JsonPrimitive("failed")) }
            assertEquals(expected, data)
        }
    }

    @Test
    fun testNotification_AllOrders() {
        val supportedMethods: MutableMap<String, JsonRpcMethod> = LinkedHashMap<String, JsonRpcMethod>()
        supportedMethods["foo"] = JsonRpcMethod.request(
            "foo",
            typeOf<Void>(),
            typeOf<Location>()
        )
        val handler = MessageJsonHandler(Json.Default, supportedMethods)
        handler.methodProvider = MethodProvider { "foo" }
        val properties = arrayOf<String>(
            "\"jsonrpc\":\"2.0\"",
            "\"method\":\"foo\"",
            "\"params\": {\"uri\": \"dummy://mymodel.mydsl\"}"
        )
        testAllPermutations(properties) { json: String ->
            val message: NotificationMessage = handler.parseMessage(json) as NotificationMessage
            val params = handler.deserializeParams("foo", message.params)

            assertEquals(
                "dummy://mymodel.mydsl",
                (params[0] as Location).uri
            )
        }
    }

    @Test
    fun testWrapPrimitive_JsonRpc2_0() {
        val handler: MessageJsonHandler = createSimpleRequestHandler(
            typeOf<String>(),
            typeOf<String>()
        )
        val request =
            RequestMessage(MessageId.NumberId(1), handler.methodProvider!!.resolveMethod(null)!!, JsonParams.ArrayParams(JsonArray(listOf(JsonPrimitive("param")))))
        // check primitive was wrapped into array
        assertEquals(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"testMethod\",\"params\":[\"param\"]}",
            handler.serialize(request)
        )
    }

    @Test
    fun testUnwrapPrimitive_JsonRpc2_0() {
        val handler: MessageJsonHandler = createSimpleRequestHandler(
            typeOf<String>(),
            typeOf<String>()
        )
        handler.methodProvider = MethodProvider { "testMethod" }
        val request = ("{\n"
                + "  \"jsonrpc\": \"2.0\",\n"
                + "  \"id\": 1,\n"
                + "  \"method\": \"testMethod\",\n"
                + "  \"params\": [\n"
                + "      \"param\"\n"
                + "  ]\n"
                + "}")
        // Check parse - unwrap primitive
        val message: RequestMessage = handler.parseMessage(request) as RequestMessage
        val params = handler.deserializeParams("testMethod", message.params)
        assertEquals(listOf("param"), params)
    }

//    @Test
//    fun testWrapArray_JsonRpc2_0() {
//        val handler: MessageJsonHandler =
//            createSimpleRequestHandler(typeOf<String>(), typeOf<List<Boolean>>())
//        val request = RequestMessage(
//            MessageId.NumberId(1),
//            handler.methodProvider!!.resolveMethod(null)!!,
//            listOf(listOf(true, false))
//        )
//
//        // check primitive was wrapped into array
//        assertEquals(
//            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"testMethod\",\"params\":[[true,false]]}",
//            handler.serialize(request)
//        )
//    }

    @Test
    fun testUnwrapArray_JsonRpc2_0() {
        val handler: MessageJsonHandler =
            createSimpleRequestHandler(typeOf<String>(), typeOf<List<Boolean>>())
        val request = ("{\n"
                + "  \"jsonrpc\": \"2.0\",\n"
                + "  \"id\": 1,\n"
                + "  \"method\": \"testMethod\",\n"
                + "  \"params\": [\n"
                + "    [\n"
                + "      true, false\n"
                + "    ]\n"
                + "  ]\n"
                + "}")
        val message: RequestMessage = handler.parseMessage(request) as RequestMessage

        // Check parse - unwrap array
        val params = handler.deserializeParams("testMethod", message.params)
        assertEquals(listOf(listOf(true, false)), params)
    }

    @Test
    fun testWrapMultipleParams_JsonRpc2_0() {
        val handler: MessageJsonHandler = createSimpleRequestHandler(
            typeOf<String>(),
            typeOf<Boolean>(),
            typeOf<String>()
        )
        handler.methodProvider = MethodProvider { "testMethod" }

        val request = RequestMessage(
            MessageId.NumberId(1),
            handler.methodProvider!!.resolveMethod(null)!!,
            JsonParams.array(JsonPrimitive(true), JsonPrimitive( "param2"))
        )
        // Check serialize - wrap primitive wrapper
        assertEquals(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"testMethod\",\"params\":[true,\"param2\"]}",
            handler.serialize(request)
        )
    }

    @Test
    fun testUnwrapMultipleParams_JsonRpc2_0() {
        val handler: MessageJsonHandler = createSimpleRequestHandler(
            typeOf<String>(),
            typeOf<Boolean>(),
            typeOf<String>()
        )
        handler.methodProvider = MethodProvider { "testMethod" }
        val request = ("{\n"
                + "  \"jsonrpc\": \"2.0\",\n"
                + "  \"id\": 1,\n"
                + "  \"method\": \"testMethod\",\n"
                + "  \"params\": [\n"
                + "      true,\n"
                + "      \"param2\"\n"
                + "  ]\n"
                + "}")
        val message: RequestMessage = handler.parseMessage(request) as RequestMessage

        // Check parse - unwrap array
        val params = handler.deserializeParams("testMethod", message.params)
        assertEquals(listOf(true, "param2"), params)
    }

    @Test
    fun testWrapMultipleParamsWithArray_JsonRpc2_0() {
        val handler: MessageJsonHandler = createSimpleRequestHandler(
            typeOf<String>(), typeOf<List<Boolean>>(),
            typeOf<String>()
        )
        val request = RequestMessage(
            MessageId.NumberId(1),
            handler.methodProvider!!.resolveMethod(null)!!,
            JsonParams.array(JsonArray(listOf(JsonPrimitive(true), JsonPrimitive(false))), JsonPrimitive("param2"))
        )
        // Check serialize - wrap primitive wrapper
        assertEquals(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"testMethod\",\"params\":[[true,false],\"param2\"]}",
            handler.serialize(request)
        )
    }

    @Test
    fun testUnwrapMultipleParamsWithArray_JsonRpc2_0() {
        val handler: MessageJsonHandler = createSimpleRequestHandler(
            typeOf<String>(), typeOf<List<Boolean>>(),
            typeOf<String>()
        )
        handler.methodProvider = MethodProvider { "testMethod" }
        val request = ("{\n"
                + "  \"jsonrpc\": \"2.0\",\n"
                + "  \"id\": 1,\n"
                + "  \"method\": \"testMethod\",\n"
                + "  \"params\": [\n"
                + "    [\n"
                + "      true,\n"
                + "      false\n"
                + "    ],\n"
                + "    \"param2\"\n"
                + "  ]\n"
                + "}")
        val message: RequestMessage = handler.parseMessage(request) as RequestMessage

        // Check parse - unwrap array
        val params = handler.deserializeParams("testMethod", message.params)
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
            val handler = MessageJsonHandler(Json.Default, mapOf(Pair(requestMethod.methodName, requestMethod)))
            handler.methodProvider = MethodProvider { requestMethod.methodName }
            return handler
        }
    }
}
