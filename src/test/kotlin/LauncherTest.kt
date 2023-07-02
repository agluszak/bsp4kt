import com.jetbrains.bsp.Launcher
import com.jetbrains.bsp.services.JsonNotification
import com.jetbrains.bsp.services.JsonRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class LauncherTest() {
    internal class Param {
        constructor()
        constructor(message: String?) {
            this.message = message
        }

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
    @Throws(Exception::class)
    fun testDone() {
        val a: A = object : A {
            override fun say(p: Param) {}
        }
        val launcher: Launcher<A> = Launcher.createLauncher(
            a,
            A::class.java, ByteArrayInputStream("".toByteArray()), ByteArrayOutputStream()
        )
        val startListening: Future<*> = launcher.startListening()
        startListening[TIMEOUT, TimeUnit.MILLISECONDS]
        assertTrue(startListening.isDone)
        assertFalse(startListening.isCancelled)
    }

    @Test
    @Throws(Exception::class)
    fun testCanceled() {
        val a: A = object : A {
            override fun say(p: Param) {}
        }
        val launcher: Launcher<A> = Launcher.createLauncher(a, A::class.java, object : InputStream() {
            @Throws(IOException::class)
            override fun read(): Int {
                try {
                    Thread.sleep(100)
                } catch (e: InterruptedException) {
                    throw RuntimeException(e)
                }
                return '\n'.code
            }
        }, ByteArrayOutputStream())
        val startListening: Future<*> = launcher.startListening()
        startListening.cancel(true)
        assertTrue(startListening.isDone)
        assertTrue(startListening.isCancelled)
    }

//    @Test
//    @Throws(Exception::class)
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
//    @Throws(Exception::class)
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
