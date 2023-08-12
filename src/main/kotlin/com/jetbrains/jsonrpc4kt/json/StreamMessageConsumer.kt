package com.jetbrains.jsonrpc4kt.json

import com.jetbrains.jsonrpc4kt.JsonRpcException
import com.jetbrains.jsonrpc4kt.MessageConsumer
import com.jetbrains.jsonrpc4kt.messages.Message
import com.jetbrains.jsonrpc4kt.messages.Message.Companion.CONTENT_LENGTH_HEADER
import com.jetbrains.jsonrpc4kt.messages.Message.Companion.CONTENT_TYPE_HEADER
import com.jetbrains.jsonrpc4kt.messages.Message.Companion.CRLF
import com.jetbrains.jsonrpc4kt.messages.Message.Companion.JSON_MIME_TYPE
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.locks.ReentrantLock


/**
 * A message consumer that serializes messages to JSON and sends them to an output stream.
 */
class StreamMessageConsumer(
    var output: OutputStream?, private val encoding: String, private val jsonHandler: MessageJsonHandler
) : MessageConsumer {
    private val outputLock = ReentrantLock()
    constructor(output: OutputStream?, jsonHandler: MessageJsonHandler) : this(
        output, StandardCharsets.UTF_8.name(), jsonHandler
    )

    override fun consume(message: Message) {
        try {
            val content = jsonHandler.serializeMessage(message)
            val contentBytes = content.toByteArray(charset(encoding))
            val contentLength = contentBytes.size
            val header = getHeader(contentLength)
            val headerBytes = header.toByteArray(StandardCharsets.US_ASCII)
            outputLock.lock()
            try {
                output!!.write(headerBytes)
                output!!.write(contentBytes)
                output!!.flush()
            } finally {
                outputLock.unlock()
            }
        } catch (exception: IOException) {
            throw JsonRpcException(exception)
        }
    }

    /**
     * Construct a header to be prepended to the actual content. This implementation writes
     * `Content-Length` and `Content-Type` attributes according to the LSP specification.
     */
    protected fun getHeader(contentLength: Int): String {
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
    protected fun appendHeader(builder: StringBuilder, name: String?, value: Any?): StringBuilder {
        return builder.append(name).append(": ").append(value)
    }
}
