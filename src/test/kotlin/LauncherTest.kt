import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.jetbrains.jsonrpc4kt.services.JsonNotification
import org.jetbrains.jsonrpc4kt.services.JsonRequest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.*
import java.util.concurrent.CompletableFuture
import kotlin.test.assertFailsWith

class LauncherTest {
    val newlines = "\r\n"

    internal class Param {

        var message: String? = null
    }

    internal interface A {
        @JsonNotification
        fun say(p: Param)
    }

    internal interface B {
        @JsonRequest
        fun ask(p: Param): CompletableFuture<String>
    }

    @Test
    fun testEmpty() = runTest {
        val a: A = object : A {
            override fun say(p: Param) {}
        }
        val launcher = org.jetbrains.jsonrpc4kt.Launcher(
            ByteArrayInputStream("".toByteArray()),
            ByteArrayOutputStream(),
            a,
            A::class,
            this
        )
        val startListening = launcher.start()
        startListening.join()
        assertTrue(startListening.isCompleted)
        assertFalse(startListening.isCancelled)
    }

    @Test
    fun cancellationClosesInputStream() = runTest {
        val a: A = object : A {
            override fun say(p: Param) {}
        }
        val input = PipedInputStream()
        val outputStream = PipedOutputStream(input)
        val launcher = org.jetbrains.jsonrpc4kt.Launcher(input, outputStream, a, A::class, this)
        val startListening = launcher.start()
        delay(100)
        startListening.cancelAndJoin()
        assertTrue(startListening.isCompleted)
        assertTrue(startListening.isCancelled)
        assertFailsWith<IOException> { input.read() }
    }

    @Test
    fun testCompleted() = runTest {
        val a: A = object : A {
            override fun say(p: Param) {}
        }

        val inputStream =
            ByteArrayInputStream("""Content-Length: 49$newlines{"jsonrpc": "2.0", "method": "foobar", "id": "1"}""".toByteArray())
        val outputStream = ByteArrayOutputStream()

        val launcher = org.jetbrains.jsonrpc4kt.Launcher(inputStream, outputStream, a, A::class, this)
        val startListening = launcher.start()

        startListening.join()
        assertTrue(startListening.isCompleted)
        assertFalse(startListening.isCancelled)
    }

    @Test
    fun testCanceled() = runTest {
        val a: A = object : A {
            override fun say(p: Param) {}
        }
        val input = object : InputStream() {
            override fun read(): Int {
                Thread.sleep(1000)
                return '\n'.code
            }
        }
        val launcher = org.jetbrains.jsonrpc4kt.Launcher(input, ByteArrayOutputStream(), a, A::class, this)
        val startListening = launcher.start()
        startListening.cancel()
        startListening.join()
        assertTrue(startListening.isCompleted)
        assertTrue(startListening.isCancelled)
    }

//    @Test
//    
//    fun testCustomGson() {
//        val a: A = object : A {
//            override fun say(p: Param) {}
//        }
//        val out = ByteArrayOutputStream()
//        val typeAdapter: TypeAdapter<Param> = object : TypeAdapter<Param?>() {
//            @Throws(IOException::class)
//            fun write(out: JsonWriter, value: Param?) {
//                out.beginObject()
//                out.name("message")
//                out.value("bar")
//                out.endObject()
//            }
//
//            @Throws(IOException::class)
//            fun read(`in`: JsonReader?): Param? {
//                return null
//            }
//        }
//        val launcher: Launcher<A> = Launcher.createIoLauncher(a,
//            A::class.java, ByteArrayInputStream("".toByteArray()), out,
//            Executors.newCachedThreadPool(), { c -> c }
//        ) { gsonBuilder ->
//            gsonBuilder.registerTypeAdapter(
//                Param::class.java,
//                typeAdapter
//            )
//        }
//        val remoteProxy: A = launcher.getRemoteProxy()
//        remoteProxy.say(Param("foo"))
//        assertEquals(
//            "Content-Length: 59\r\n\r\n"
//                    + "{\"jsonrpc\":\"2.0\",\"method\":\"say\",\"params\":{\"message\":\"bar\"}}",
//            out.toString()
//        )
//    }

    // TODO: not needed
//    @Test
//    
//    fun testMultipleServices() {
//        val paramA = arrayOfNulls<String>(1)
//        val a: A = object : A {
//            override fun say(p: Param) {
//                paramA[0] = p.message
//            }
//        }
//        val paramB = arrayOfNulls<String>(1)
//        val b: B = object : B {
//            override fun ask(p: Param): CompletableFuture<String> {
//                paramB[0] = p.message
//                return CompletableFuture.completedFuture("echo " + p.message)
//            }
//        }
//        val inputMessages = ("Content-Length: 60\r\n\r\n"
//                + "{\"jsonrpc\":\"2.0\",\"method\":\"say\",\"params\":{\"message\":\"foo1\"}}"
//                + "Content-Length: 69\r\n\r\n"
//                + "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"ask\",\"params\":{\"message\":\"bar1\"}}")
//        val `in` = ByteArrayInputStream(inputMessages.toByteArray())
//        val out = ByteArrayOutputStream()
//        val launcher: Launcher<Any> = Launcher.createLauncher(
//            listOf(a, b), listOf(
//                A::class.java,
//                B::class.java
//            ),
//            `in`, out
//        )
//        launcher.startListening().get(TIMEOUT, TimeUnit.MILLISECONDS)
//        assertEquals("foo1", paramA[0])
//        assertEquals("bar1", paramB[0])
//        val remoteProxy: Any = launcher.remoteProxy
//        (remoteProxy as A).say(Param("foo2"))
//        (remoteProxy as B).ask(Param("bar2"))
//        assertEquals(
//            ("Content-Length: 47\r\n\r\n"
//                    + "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":\"echo bar1\"}"
//                    + "Content-Length: 60\r\n\r\n"
//                    + "{\"jsonrpc\":\"2.0\",\"method\":\"say\",\"params\":{\"message\":\"foo2\"}}"
//                    + "Content-Length: 69\r\n\r\n"
//                    + "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"ask\",\"params\":{\"message\":\"bar2\"}}"),
//            out.toString()
//        )
//    }

    companion object {
        private val TIMEOUT: Long = 2000
    }
}
