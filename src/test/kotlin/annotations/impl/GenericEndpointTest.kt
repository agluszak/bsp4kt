package annotations.impl

import LogMessageAccumulator
import com.jetbrains.jsonrpc4kt.RemoteEndpoint
import com.jetbrains.jsonrpc4kt.services.GenericEndpoint
import com.jetbrains.jsonrpc4kt.services.JsonNotification
import com.jetbrains.jsonrpc4kt.services.JsonRequest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
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
        LogMessageAccumulator(RemoteEndpoint::class).use {
            val foo = Foo()
            val endpoint = GenericEndpoint(foo)
            assertEquals(0, foo.calls)
            endpoint.notify("myNotification", listOf(Any()))
            assertEquals(1, foo.calls)
            endpoint.notify("myNotification", listOf(Any(), Any()))
            // TODO: check for log message
        }
    }

    @Test
    fun testZeroParams_01() {
        testZeroParams(listOf("foo")) { m: String -> m.contains("Unexpected additional params '[foo]'") }
    }

    @Test
    fun testZeroParams_02() {
        testZeroParams(listOf(null))
    }

    @Test
    fun testZeroParams_03() {
        testZeroParams(
            mutableListOf("foo")
        ) { m: String -> m.contains("Unexpected additional params '[foo]'") }
    }

    @Test
    fun testZeroParams_04() {
        testZeroParams(
            mutableListOf("foo", "bar")
        ) { m: String -> m.contains("Unexpected additional params '[foo, bar]'") }
    }


    fun testZeroParams(params: List<Any?>, predicate: Predicate<String>? = null) {
        LogMessageAccumulator(GenericEndpoint::class).use { logMessages ->
            val endpoint = GenericEndpoint(object : Any() {
                @JsonNotification
                fun myNotification() {
                }
            })
            endpoint.notify("myNotification", params)
            predicate?.let { logMessages.await { r -> Level.WARNING === r.level && predicate.test(r.message) } }
        }
    }

    @Test
    fun testSingleParams_01() {
        testSingleParams(listOf("foo"), "foo")
    }

    @Test
    fun testSingleParams_02() {
        testSingleParams(listOf(null), null)
    }

    @Test
    fun testSingleParams_03() {
        testSingleParams(mutableListOf("foo"), "foo")
    }

    @Test
    fun testSingleParams_04() {
        testSingleParams(
            mutableListOf("foo", "bar"), "foo"
        ) { m: String -> m.contains("Unexpected additional params '[bar]'") }
    }


    fun testSingleParams(params: List<Any?>, expectedString: String?, predicate: Predicate<String>? = null) = runTest {
        LogMessageAccumulator(GenericEndpoint::class).use { logMessages ->
            val endpoint = GenericEndpoint(object : Any() {
                @JsonRequest
                suspend fun getStringValue(stringValue: String): String {
                    return stringValue
                }
            })
            assertEquals(expectedString, endpoint.request("getStringValue", params))
            predicate?.let { logMessages.await { r -> Level.WARNING === r.level && predicate.test(r.message) } }
        }
    }

    @Test
    fun testMultiParams_01() {
        testMultiParams(mutableListOf("foo", 1), "foo", 1)
    }

    @Test
    fun testMultiParams_02() {
        testMultiParams(mutableListOf("foo"), "foo", null)
    }

    @Test
    fun testMultiParams_03() {
        testMultiParams(
            mutableListOf("foo", 1, "bar", 2), "foo", 1
        ) { m: String -> m.contains("Unexpected additional params '[bar, 2]'") }
    }

    @Test
    fun testMultiParams_04() {
        testMultiParams(listOf("foo"), "foo", null)
    }


    fun testMultiParams(
        params: List<Any?>, expectedString: String?, expectedInt: Int?, predicate: Predicate<String>? = null
    ) = runTest {
        LogMessageAccumulator(GenericEndpoint::class).use { logMessages ->
            val endpoint = GenericEndpoint(object : Any() {
                var stringValue: String? = null
                var intValue: Int? = null

                @JsonRequest
                suspend fun getStringValue(): String? {
                    return stringValue
                }

                @JsonRequest
                suspend fun getIntValue(): Int? {
                    return intValue
                }

                @JsonNotification
                fun myNotification(stringValue: String?, intValue: Int?) {
                    this.stringValue = stringValue
                    this.intValue = intValue
                }
            })
            endpoint.notify("myNotification", params)
            predicate?.let { logMessages.await { r -> Level.WARNING === r.level && predicate.test(r.message) } }

            assertEquals(expectedString, endpoint.request("getStringValue", listOf(null)))
            assertEquals(expectedInt, endpoint.request("getIntValue", listOf(null)))
        }
    }
}
