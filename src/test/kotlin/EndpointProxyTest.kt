import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.jetbrains.jsonrpc4kt.services.JsonNotification
import org.jetbrains.jsonrpc4kt.services.JsonRequest
import org.jetbrains.jsonrpc4kt.services.ServiceEndpoints
import kotlin.test.Test
import kotlin.test.assertEquals

class EndpointProxyTest {
    interface TestInterface {
        @JsonNotification
        fun testNotification()

        @JsonRequest
        suspend fun testRequest(): String
    }

    class TestClassImpl : TestInterface {
        var calls = 0

        override fun testNotification() {
            calls++
        }

        override suspend fun testRequest(): String {
            calls++
            return "testRequest"
        }
    }

    class TestImplAwaiter : TestInterface {
        val deferred = CompletableDeferred<String>()

        override fun testNotification() {
            deferred.complete("testNotification")
        }

        override suspend fun testRequest(): String {
            return deferred.await()
        }
    }

    @Test
    fun simpleTest() = runTest {
        val testClass = TestClassImpl()
        val endpoint = ServiceEndpoints.toEndpoint(testClass)
        val serviceObject = ServiceEndpoints.toServiceObject(endpoint, TestInterface::class)

        serviceObject.testNotification()
        assertEquals(1, testClass.calls)
        val result = serviceObject.testRequest()
        assertEquals("testRequest", result)
        assertEquals(2, testClass.calls)
    }

    @Test
    fun awaitTest() = runTest {
        val testClass = TestImplAwaiter()
        val endpoint = ServiceEndpoints.toEndpoint(testClass)
        val serviceObject = ServiceEndpoints.toServiceObject(endpoint, TestInterface::class)

        val result = async { serviceObject.testRequest() }

        serviceObject.testNotification()
        assertEquals("testNotification", result.await())
    }

}