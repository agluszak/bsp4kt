package com.jetbrains.jsonrpc4kt.json

import com.jetbrains.jsonrpc4kt.JsonRpcException
import com.jetbrains.jsonrpc4kt.messages.Message
import com.jetbrains.jsonrpc4kt.messages.Message.Companion.CONTENT_LENGTH_HEADER
import com.jetbrains.jsonrpc4kt.messages.Message.Companion.CONTENT_TYPE_HEADER
import com.jetbrains.jsonrpc4kt.messages.Message.Companion.CRLF
import com.jetbrains.jsonrpc4kt.messages.Message.Companion.JSON_MIME_TYPE
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets


/**
 * A message consumer that serializes messages to JSON and sends them to an output stream.
 */
class StreamMessageConsumer(
    private val output: OutputStream,
    private val jsonHandler: MessageJsonHandler,
    private val messageChannel: ReceiveChannel<Message>,
    private val encoding: String = StandardCharsets.UTF_8.name()
) {
    private val mutex = Mutex()

    fun start(coroutineScope: CoroutineScope): Job =
        coroutineScope.launch {
            println("consumer listening")
            for (message in messageChannel) {
                println("consumer got message $message")
                try {
                    val content = jsonHandler.serializeMessage(message)
                    val contentBytes = content.toByteArray(charset(encoding))
                    val contentLength = contentBytes.size
                    val header = getHeader(contentLength)
                    val headerBytes = header.toByteArray(StandardCharsets.US_ASCII)
                    println("output writing" + content)
                    withContext(Dispatchers.IO) {
                        mutex.withLock {
                            output.write(headerBytes)
                            output.write(contentBytes)
                            output.flush()
                        }
                    }
                    println("output WROTE" + content)
                } catch (exception: IOException) {
                    throw JsonRpcException(exception)
                }
            }
            println("consumer closing")
            mutex.withLock {
                output.close()
            }
        }

    /**
     * Construct a header to be prepended to the actual content. This implementation writes
     * `Content-Length` and `Content-Type` attributes according to the LSP specification.
     */
    private fun getHeader(contentLength: Int): String {
        val headerBuilder = StringBuilder()
        appendHeader(headerBuilder, CONTENT_LENGTH_HEADER, contentLength).append(CRLF)
        if (StandardCharsets.UTF_8.name() != encoding) {
            appendHeader(headerBuilder, CONTENT_TYPE_HEADER, JSON_MIME_TYPE)
            headerBuilder.append("; charset=").append(encoding).append(CRLF)
        }
        headerBuilder.append(CRLF)
        return headerBuilder.toString()
    }

    /**
     * Append a header attribute to the given builder.
     */
    private fun appendHeader(builder: StringBuilder, name: String?, value: Any?): StringBuilder {
        return builder.append(name).append(": ").append(value)
    }
}
