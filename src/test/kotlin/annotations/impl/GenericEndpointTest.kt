package annotations.impl

import LogMessageAccumulator
import com.jetbrains.bsp.services.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate
import java.util.logging.Level

class GenericEndpointTest {
    class Foo : MyIf, OtherThing {
        var calls = 0
        override fun myNotification() {
            calls++
        }

        override fun doDelegate(): OtherThing {
            return this
        }
    }

    class Bar {
        var calls = 0

        @JsonNotification
        fun barrr() {
            calls++
        }
    }

    interface MyIf {
        @JsonNotification
        fun myNotification()

        @JsonDelegate
        fun doDelegate(): OtherThing?
    }

    @JsonSegment("other")
    interface OtherThing {
        @JsonNotification
        fun myNotification()
    }

    @Test
    fun testSimple() {
        val foo = Foo()
        val endpoint = GenericEndpoint(foo)
        endpoint.notify("myNotification", null)
        endpoint.notify("other/myNotification", null)
        assertEquals(2, foo.calls)
    }

    @Test
    fun testMultiServices() {
        val foo: Foo = Foo()
        val bar: Bar = Bar()
        val endpoint = GenericEndpoint(foo, bar)
        endpoint.notify("myNotification", null)
        endpoint.notify("barrr", null)
        endpoint.notify("other/myNotification", null)
        assertEquals(2, foo.calls)
        assertEquals(1, bar.calls)
    }

    @Test
    fun testUnexpectedParams() {
        val logMessages = LogMessageAccumulator()
        try {
            logMessages.registerTo(GenericEndpoint::class.java)
            val foo: Foo = Foo()
            val endpoint = GenericEndpoint(foo)
            assertEquals(0, foo.calls)
            endpoint.notify("myNotification", Any())
            assertEquals(1, foo.calls)
        } finally {
            logMessages.unregister()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testZeroParams_01() {
        testZeroParams("foo") { m: String -> m.contains("Unexpected params 'foo'") }
    }

    @Test
    @Throws(Exception::class)
    fun testZeroParams_02() {
        testZeroParams(null)
    }

    @Test
    @Throws(Exception::class)
    fun testZeroParams_03() {
        testZeroParams(
            mutableListOf("foo")
        ) { m: String -> m.contains("Unexpected params '[foo]'") }
    }

    @Test
    @Throws(Exception::class)
    fun testZeroParams_04() {
        testZeroParams(
            mutableListOf("foo", "bar")
        ) { m: String -> m.contains("Unexpected params '[foo, bar]'") }
    }

    @Throws(Exception::class)
     fun testZeroParams(params: Any?, predicate: Predicate<String>? = null) {
        var logMessages: LogMessageAccumulator? = null
        try {
            if (predicate != null) {
                logMessages = LogMessageAccumulator()
                logMessages.registerTo(GenericEndpoint::class.java)
            }
            val endpoint = GenericEndpoint(object : Any() {
                @JsonNotification
                fun myNotification() {
                }
            })
            endpoint.notify("myNotification", params)
            logMessages?.await { r -> Level.WARNING === r.level && predicate?.test(r.message) == true }
        } finally {
            logMessages?.unregister()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testSingleParams_01() {
        testSingleParams("foo", "foo")
    }

    @Test
    @Throws(Exception::class)
    fun testSingleParams_02() {
        testSingleParams(null, null)
    }

    @Test
    @Throws(Exception::class)
    fun testSingleParams_03() {
        testSingleParams(mutableListOf("foo"), "foo")
    }

    @Test
    @Throws(Exception::class)
    fun testSingleParams_04() {
        testSingleParams(
            mutableListOf("foo", "bar"), "foo"
        ) { m: String -> m.contains("Unexpected params 'bar'") }
    }

    @Throws(Exception::class)
     fun testSingleParams(params: Any?, expectedString: String?, predicate: Predicate<String>? = null) {
        var logMessages: LogMessageAccumulator? = null
        try {
            if (predicate != null) {
                logMessages = LogMessageAccumulator()
                logMessages.registerTo(GenericEndpoint::class.java)
            }
            val endpoint = GenericEndpoint(object : Any() {
                @JsonRequest
                fun getStringValue(stringValue: String?): CompletableFuture<String> {
                    return CompletableFuture.completedFuture(stringValue)
                }
            })
            assertEquals(expectedString, endpoint.request("getStringValue", params).get())
            logMessages?.await { r -> Level.WARNING === r.level && predicate?.test(r.message) == true }

        } finally {
            logMessages?.unregister()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMultiParams_01() {
        testMultiParams(mutableListOf("foo", 1), "foo", 1)
    }

    @Test
    @Throws(Exception::class)
    fun testMultiParams_02() {
        testMultiParams(mutableListOf("foo"), "foo", null)
    }

    @Test
    @Throws(Exception::class)
    fun testMultiParams_03() {
        testMultiParams(
            mutableListOf("foo", 1, "bar", 2), "foo", 1
        ) { m: String -> m.contains("Unexpected params 'bar', '2'") }
    }

    @Test
    @Throws(Exception::class)
    fun testMultiParams_04() {
        testMultiParams("foo", "foo", null)
    }

    @Throws(Exception::class)
     fun testMultiParams(
        params: Any?, expectedString: String?, expectedInt: Int?, predicate: Predicate<String>? = null
    ) {
        var logMessages: LogMessageAccumulator? = null
        try {
            if (predicate != null) {
                logMessages = LogMessageAccumulator()
                logMessages.registerTo(GenericEndpoint::class.java)
            }
            val endpoint = GenericEndpoint(object : Any() {
                var stringValue: String? = null
                var intValue: Int? = null

                @JsonRequest
                fun getStringValue(): CompletableFuture<String?> {
                    return CompletableFuture.completedFuture(stringValue)
                }

                @JsonRequest
                fun getIntValue(): CompletableFuture<Int?> {
                    return CompletableFuture.completedFuture(intValue)
                }

                @JsonNotification
                fun myNotification(stringValue: String?, intValue: Int?) {
                    this.stringValue = stringValue
                    this.intValue = intValue
                }
            })
            endpoint.notify("myNotification", params)
            logMessages?.await { r -> Level.WARNING === r.level && predicate?.test(r.message) == true }

            assertEquals(expectedString, endpoint.request("getStringValue", null).get())
            assertEquals(expectedInt, endpoint.request("getIntValue", null).get())
        } finally {
            logMessages?.unregister()
        }
    }
}
