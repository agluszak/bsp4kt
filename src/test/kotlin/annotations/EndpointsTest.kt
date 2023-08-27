package annotations


import kotlinx.coroutines.test.runTest
import org.jetbrains.jsonrpc4kt.json.JsonRpcMethod
import org.jetbrains.jsonrpc4kt.services.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.function.Consumer
import kotlin.reflect.typeOf
import kotlin.test.Ignore


class EndpointsTest {
    @JsonSegment("foo")
    interface Foo {
        @JsonRequest
        suspend fun doStuff(arg: String): String?

        @JsonNotification
        fun myNotification(someArg: String)

    }


    @JsonSegment("bar")
    interface Bar {
        @JsonRequest
        suspend fun doStuff2(arg: String, arg2: Int): String?

        @JsonNotification
        fun myNotification2(someArg: String, someArg2: Int)

        @JsonNotification("hubba")
        fun hubba(someArg: String, someArg2: Int)
    }

    @Test
    fun testProxy_01() = runTest {
        val endpoint: org.jetbrains.jsonrpc4kt.Endpoint = object : org.jetbrains.jsonrpc4kt.Endpoint {
            override suspend fun request(method: String, params: List<Any?>): Any {
                assertEquals("foo/doStuff", method)
                assertEquals("param", params[0])
                return "result"
            }

            override fun notify(method: String, params: List<Any?>) {
                assertEquals("foo/myNotification", method)
                assertEquals("notificationParam", params[0])
            }
        }
        val foo: Foo = ServiceEndpoints.toServiceObject(endpoint, Foo::class)
        foo.myNotification("notificationParam")
        assertEquals("result", foo.doStuff("param"))
    }

    @Test
    fun testProxy_02() = runTest {
        val endpoint: org.jetbrains.jsonrpc4kt.Endpoint = object : org.jetbrains.jsonrpc4kt.Endpoint {
            override suspend fun request(method: String, params: List<Any?>): Any {
                assertEquals("bar/doStuff2", method)
                assertEquals("[param, 2]", params.toString())
                return "result"
            }

            override fun notify(method: String, params: List<Any?>) {
                assertEquals("bar/myNotification2", method)
                assertEquals("[notificationParam, 1]", params.toString())
            }
        }
        val bar: Bar = ServiceEndpoints.toServiceObject(endpoint, Bar::class)
        bar.myNotification2("notificationParam", 1)
        assertEquals("result", bar.doStuff2("param", 2))
    }

    @Test
    @Ignore // TODO: do we want to support this?
    fun testBackAndForth() = runTest {
        val endpoint: org.jetbrains.jsonrpc4kt.Endpoint = object : org.jetbrains.jsonrpc4kt.Endpoint {
            override suspend fun request(method: String, params: List<Any?>): Any? {
                assertEquals("foo/doStuff", method)
                assertEquals("param", params[0])
                return "result"
            }

            override fun notify(method: String, params: List<Any?>) {
                assertEquals("foo/myNotification", method)
                assertEquals("notificationParam", params[0])
            }
        }
        val intermediateFoo: Foo = ServiceEndpoints.toServiceObject(endpoint, Foo::class)
        val secondEndpoint: org.jetbrains.jsonrpc4kt.Endpoint = ServiceEndpoints.toEndpoint(intermediateFoo)
        val foo: Foo = ServiceEndpoints.toServiceObject(secondEndpoint, Foo::class)
        foo.myNotification("notificationParam")
        assertEquals("result", foo.doStuff("param"))
    }

    @Test
    fun testRpcMethods_01() {
        val methods: Map<String, JsonRpcMethod> = ServiceEndpoints.getSupportedMethods(Foo::class)
        assertEquals("foo/doStuff", methods["foo/doStuff"]!!.methodName)
        assertEquals(typeOf<String>(), methods["foo/doStuff"]!!.parameterTypes[0])
        assertFalse(methods["foo/doStuff"]!!.isNotification)
        assertEquals("foo/myNotification", methods["foo/myNotification"]!!.methodName)
        assertEquals(typeOf<String>(), methods["foo/myNotification"]!!.parameterTypes[0])
        assertTrue(methods["foo/myNotification"]!!.isNotification)
    }

    @Test
    fun testRpcMethods_02() {
        val methods: Map<String, JsonRpcMethod> = ServiceEndpoints.getSupportedMethods(Bar::class)
        val requestMethod: JsonRpcMethod = methods["bar/doStuff2"]!!
        assertEquals("bar/doStuff2", requestMethod.methodName)
        assertEquals(2, requestMethod.parameterTypes.size)
        assertEquals(typeOf<String>(), requestMethod.parameterTypes[0])
        assertEquals(typeOf<Int>(), requestMethod.parameterTypes[1])
        assertFalse(requestMethod.isNotification)
        val notificationMethod: JsonRpcMethod = methods["bar/myNotification2"]!!
        assertEquals("bar/myNotification2", notificationMethod.methodName)
        assertEquals(2, notificationMethod.parameterTypes.size)
        assertEquals(typeOf<String>(), notificationMethod.parameterTypes[0])
        assertEquals(typeOf<Int>(), notificationMethod.parameterTypes[1])
        assertTrue(notificationMethod.isNotification)
        val delegateMethod: JsonRpcMethod = methods["bar/hubba"]!!
        assertEquals("bar/hubba", delegateMethod.methodName)
        assertEquals(2, delegateMethod.parameterTypes.size)
        assertEquals(typeOf<String>(), delegateMethod.parameterTypes[0])
        assertEquals(typeOf<Int>(), delegateMethod.parameterTypes[1])
        assertTrue(delegateMethod.isNotification)
    }

    @JsonSegment("consumer")
    interface StringConsumer : Consumer<String?> {
        @JsonNotification
        override fun accept(message: String?)
    }

    @Test
    fun lsp4jIssue106() {
        val foo: Foo = ServiceEndpoints.toServiceObject(GenericEndpoint(Any()), Foo::class)
        assertEquals(foo, foo)
    }

    @Test
    fun lsp4jIssue107() {
        val methods: Map<String, JsonRpcMethod> = ServiceEndpoints.getSupportedMethods(
            StringConsumer::class
        )
        val method: JsonRpcMethod = methods["consumer/accept"]!!
        assertEquals("consumer/accept", method.methodName)
        assertEquals(1, method.parameterTypes.size)
        assertEquals(typeOf<String?>(), method.parameterTypes[0])
        assertTrue(method.isNotification)
    }
}
