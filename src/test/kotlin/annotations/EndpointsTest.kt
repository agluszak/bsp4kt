package annotations

import annotations.EndpointsTest.*
import com.jetbrains.jsonrpc4kt.Endpoint
import com.jetbrains.jsonrpc4kt.json.JsonRpcMethod
import com.jetbrains.jsonrpc4kt.services.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test


import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import kotlin.reflect.typeOf


class EndpointsTest {
    @JsonSegment("foo")
    interface Foo {
        @JsonRequest
        fun doStuff(arg: String): CompletableFuture<String?>

        @JsonNotification
        fun myNotification(someArg: String)

    }


    @JsonSegment("bar")
    interface Bar {
        @JsonRequest
        fun doStuff2(arg: String, arg2: Int): CompletableFuture<String?>

        @JsonNotification
        fun myNotification2(someArg: String, someArg2: Int)

        @JsonNotification("hubba")
        fun hubba(someArg: String, someArg2: Int)
    }

    @Test
    @Throws(Exception::class)
    fun testProxy_01() {
        val endpoint: Endpoint = object : Endpoint {
            override fun request(method: String, params: List<Any?>): CompletableFuture<*> {
                assertEquals("foo/doStuff", method)
                assertEquals("param", params[0])
                return CompletableFuture.completedFuture("result")
            }

            override fun notify(method: String, params: List<Any?>) {
                assertEquals("foo/myNotification", method)
                assertEquals("notificationParam", params[0])
            }
        }
        val foo: Foo = ServiceEndpoints.toServiceObject(endpoint, Foo::class)
        foo.myNotification("notificationParam")
        assertEquals("result", foo.doStuff("param")[TIMEOUT, TimeUnit.MILLISECONDS])
    }

    @Test
    @Throws(Exception::class)
    fun testProxy_02() {
        val endpoint: Endpoint = object : Endpoint {
            override fun request(method: String, params: List<Any?>): CompletableFuture<*> {
                assertEquals("bar/doStuff2", method)
                assertEquals("[param, 2]", params.toString())
                return CompletableFuture.completedFuture("result")
            }

            override fun notify(method: String, params: List<Any?>) {
                assertEquals("bar/myNotification2", method)
                assertEquals("[notificationParam, 1]", params.toString())
            }
        }
        val bar: Bar = ServiceEndpoints.toServiceObject(endpoint, Bar::class)
        bar.myNotification2("notificationParam", 1)
        assertEquals("result", bar.doStuff2("param", 2)[TIMEOUT, TimeUnit.MILLISECONDS])
    }

    @Test
    @Throws(Exception::class)
    fun testBackAndForth() {
        val endpoint: Endpoint = object : Endpoint {
            override fun request(method: String, params: List<Any?>): CompletableFuture<*> {
                assertEquals("foo/doStuff", method)
                assertEquals("param", params[0])
                return CompletableFuture.completedFuture("result")
            }

            override fun notify(method: String, params: List<Any?>) {
                assertEquals("foo/myNotification", method)
                assertEquals("notificationParam", params[0])
            }
        }
        val intermediateFoo: Foo = ServiceEndpoints.toServiceObject(endpoint, Foo::class)
        val secondEndpoint: Endpoint = ServiceEndpoints.toEndpoint(intermediateFoo)
        val foo: Foo = ServiceEndpoints.toServiceObject(secondEndpoint, Foo::class)
        foo.myNotification("notificationParam")
        assertEquals("result", foo.doStuff("param").get(TIMEOUT, TimeUnit.MILLISECONDS))
    }

//    @Test
//    @Throws(Exception::class)
//    fun testMultipleInterfaces() {
//        val requests: MutableMap<String, Any?> = HashMap()
//        val notifications: MutableMap<String, Any?> = HashMap()
//        val endpoint: Endpoint = object : Endpoint {
//            override fun request(method: String, params: List<Any?>): CompletableFuture<*> {
//                requests[method] = params
//                return when (method) {
//                    "foo/doStuff" -> {
//                        assertEquals("paramFoo", params[0])
//                        CompletableFuture.completedFuture("resultFoo")
//                    }
//
//                    "bar/doStuff2" -> {
//                        assertEquals("paramBar", params[0])
//                        assertEquals(77, params[1])
//                        CompletableFuture.completedFuture("resultBar")
//                    }
//
//                    else -> {
//                        fail("Unexpected method: $method")
//                    }
//                }
//            }
//
//            override fun notify(method: String, params: List<Any?>) {
//                notifications[method] = params
//            }
//        }
//        val classLoader = javaClass.classLoader
//        val proxy: Any =
//            ServiceEndpoints.toServiceObject(endpoint, listOf(Foo::class, Bar::class), classLoader)
//        val foo = proxy as Foo
//        foo.myNotification("notificationParamFoo")
//        assertEquals("resultFoo", foo.doStuff("paramFoo")[TIMEOUT, TimeUnit.MILLISECONDS])
//        val bar = proxy as Bar
//        bar.myNotification2("notificationParamBar", 42)
//        assertEquals("resultBar", bar.doStuff2("paramBar", 77)[TIMEOUT, TimeUnit.MILLISECONDS])
//        assertEquals(2, requests.size)
//        assertEquals(2, notifications.size)
//        assertEquals(listOf("notificationParamFoo"), notifications["foo/myNotification"])
//        assertEquals(mutableListOf("notificationParamBar", 42), notifications["bar/myNotification2"])
//    }

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
    fun testIssue106() {
        val foo: Foo = ServiceEndpoints.toServiceObject(GenericEndpoint(Any()), Foo::class)
        assertEquals(foo, foo)
    }

    @Test
    fun testIssue107() {
        val methods: Map<String, JsonRpcMethod> = ServiceEndpoints.getSupportedMethods(
            StringConsumer::class
        )
        val method: JsonRpcMethod = methods["consumer/accept"]!!
        assertEquals("consumer/accept", method.methodName)
        assertEquals(1, method.parameterTypes.size)
        assertEquals(typeOf<String?>(), method.parameterTypes[0])
        assertTrue(method.isNotification)
    }

    companion object {
        private const val TIMEOUT: Long = 2000
    }
}

