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
    class Foo : MyIf {
        var calls = 0
        override fun myNotification() {
            calls++
        }

        override fun myOtherNotification() {
            calls++
        }

    }

    open class Bar {
        var calls = 0

        @JsonNotification
        fun barrr() {
            calls++
        }
    }

    class FooBar : Bar(), MyIf {
        var fooCalls = 0

        override fun myNotification() {
            fooCalls++
        }

        override fun myOtherNotification() {
            fooCalls++
        }

    }

    interface MyIf {
        @JsonNotification
        fun myNotification()

        @JsonNotification("other/myNotification")
        fun myOtherNotification()
    }

    @Test
    fun testSimple() {
        val foo = Foo()
        val endpoint = GenericEndpoint(foo)
        endpoint.notify("myNotification", listOf())
        endpoint.notify("other/myNotification", listOf())
        assertEquals(2, foo.calls)
    }

    @Test
    fun testMultiServices() {
        val foobar = FooBar()
        val endpoint = GenericEndpoint(foobar)
        endpoint.notify("myNotification", listOf())
        endpoint.notify("barrr", listOf())
        endpoint.notify("other/myNotification", listOf())
        assertEquals(1, foobar.calls)
        assertEquals(2, foobar.fooCalls)
    }

    @Test
    fun testUnexpectedParams() {
        val logMessages = LogMessageAccumulator()
        try {
            logMessages.registerTo(GenericEndpoint::class)
            val foo: Foo = Foo()
            val endpoint = GenericEndpoint(foo)
            assertEquals(0, foo.calls)
            endpoint.notify("myNotification", listOf(Any()))
            assertEquals(1, foo.calls)
        } finally {
            logMessages.unregister()
        }
    }

    @Test
    @Throws(Exception::class)
    fun testZeroParams_01() {
        testZeroParams(listOf("foo")) { m: String -> m.contains("Unexpected params '[foo]'") }
    }

    @Test
    @Throws(Exception::class)
    fun testZeroParams_02() {
        testZeroParams(listOf(null))
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
     fun testZeroParams(params: List<Any?>, predicate: Predicate<String>? = null) {
        var logMessages: LogMessageAccumulator? = null
        try {
            if (predicate != null) {
                logMessages = LogMessageAccumulator()
                logMessages.registerTo(GenericEndpoint::class)
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
        testSingleParams(listOf("foo"), "foo")
    }

    @Test
    @Throws(Exception::class)
    fun testSingleParams_02() {
        testSingleParams(listOf(null), null)
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
        ) { m: String -> m.contains("Unexpected params '[bar]'") }
    }

    @Throws(Exception::class)
     fun testSingleParams(params: List<Any?>, expectedString: String?, predicate: Predicate<String>? = null) {
        var logMessages: LogMessageAccumulator? = null
        try {
            if (predicate != null) {
                logMessages = LogMessageAccumulator()
                logMessages.registerTo(GenericEndpoint::class)
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
        ) { m: String -> m.contains("Unexpected params '[bar, 2]'") }
    }

    @Test
    @Throws(Exception::class)
    fun testMultiParams_04() {
        testMultiParams(listOf("foo"), "foo", null)
    }

    @Throws(Exception::class)
     fun testMultiParams(
        params: List<Any?>, expectedString: String?, expectedInt: Int?, predicate: Predicate<String>? = null
    ) {
        var logMessages: LogMessageAccumulator? = null
        try {
            if (predicate != null) {
                logMessages = LogMessageAccumulator()
                logMessages.registerTo(GenericEndpoint::class)
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

            assertEquals(expectedString, endpoint.request("getStringValue", listOf(null)).get())
            assertEquals(expectedInt, endpoint.request("getIntValue", listOf(null)).get())
        } finally {
            logMessages?.unregister()
        }
    }
}
