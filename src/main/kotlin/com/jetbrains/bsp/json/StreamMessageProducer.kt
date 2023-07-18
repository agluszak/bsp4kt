package com.jetbrains.bsp.json

import com.jetbrains.bsp.*
import com.jetbrains.bsp.messages.Message
import com.jetbrains.bsp.messages.Message.Companion.CONTENT_LENGTH_HEADER
import com.jetbrains.bsp.messages.Message.Companion.CONTENT_TYPE_HEADER
import kotlinx.serialization.SerializationException
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.reflect.jvm.jvmName


/**
 * A message producer that reads from an input stream and parses messages from JSON.
 */
class StreamMessageProducer(
    var input: InputStream,
    private val jsonHandler: MessageJsonHandler,
    private val issueHandler: MessageIssueHandler? = null
) : MessageProducer, Closeable {

    private var keepRunning = false

    protected class Headers() {
        var contentLength = -1
        var charset = StandardCharsets.UTF_8.name()
    }

    override fun listen(messageConsumer: MessageConsumer) {
        if (keepRunning) {
            throw IllegalStateException("This StreamMessageProducer is already running.")
        }
        keepRunning = true
        try {
            var headerBuilder: StringBuilder? = null
            var debugBuilder: StringBuilder? = null
            var newLine = false
            var headers = Headers()
            while (keepRunning) {
                val c = input.read()
                if (c == -1) {
                    // End of input stream has been reached
                    keepRunning = false
                } else {
                    if (debugBuilder == null) debugBuilder = StringBuilder()
                    debugBuilder.append(c.toChar())
                    if (c == '\n'.code) {
                        if (newLine) {
                            // Two consecutive newlines have been read, which signals the start of the message content
                            if (headers.contentLength < 0) {
                                fireError(
                                    IllegalStateException(
                                        ("Missing header $CONTENT_LENGTH_HEADER in input \"$debugBuilder\"")
                                    )
                                )
                            } else {
                                val result = handleMessage(input, headers, messageConsumer)
                                if (!result) keepRunning = false
                                newLine = false
                            }
                            headers = Headers()
                            debugBuilder = null
                        } else if (headerBuilder != null) {
                            // A single newline ends a header line
                            parseHeader(headerBuilder.toString(), headers)
                            headerBuilder = null
                        }
                        newLine = true
                    } else if (c != '\r'.code) {
                        // Add the input to the current header line
                        if (headerBuilder == null) headerBuilder = StringBuilder()
                        headerBuilder.append(c.toChar())
                        newLine = false
                    }
                }
            } // while (keepRunning)
        } catch (exception: IOException) {
            if (JsonRpcException.indicatesStreamClosed(exception)) {
                // Only log the error if we had intended to keep running
                if (keepRunning) fireStreamClosed(exception)
            } else throw JsonRpcException(exception)
        } finally {
            keepRunning = false
        }
    }

    /**
     * Log an error.
     */
    protected fun fireError(error: Throwable) {
        val message =
            if (error.message != null) error.message else "An error occurred while processing an incoming message."
        LOG.log(Level.SEVERE, message, error)
    }

    /**
     * Report that the stream was closed through an exception.
     */
    protected fun fireStreamClosed(cause: Exception) {
        val message = if (cause.message != null) cause.message else "The input stream was closed."
        LOG.log(Level.INFO, message, cause)
    }

    /**
     * Parse a header attribute and set the corresponding data in the [Headers] fields.
     */
    protected fun parseHeader(line: String, headers: Headers) {
        val sepIndex = line.indexOf(':')
        if (sepIndex >= 0) {
            val key = line.substring(0, sepIndex).trim { it <= ' ' }
            when (key) {
                CONTENT_LENGTH_HEADER -> try {
                    headers.contentLength = line.substring(sepIndex + 1).trim { it <= ' ' }.toInt()
                } catch (e: NumberFormatException) {
                    fireError(e)
                }

                CONTENT_TYPE_HEADER -> {
                    val charsetIndex = line.indexOf("charset=")
                    if (charsetIndex >= 0) headers.charset = line.substring(charsetIndex + 8).trim { it <= ' ' }
                }
            }
        }
    }

    /**
     * Read the JSON content part of a message, parse it, and notify the callback.
     *
     * @return `true` if we should continue reading from the input stream, `false` if we should stop
     */
    @Throws(IOException::class)
    protected fun handleMessage(input: InputStream, headers: Headers, messageConsumer: MessageConsumer): Boolean {
        try {
            val contentLength = headers.contentLength
            val buffer = ByteArray(contentLength)
            var bytesRead = 0
            while (bytesRead < contentLength) {
                val readResult = input.read(buffer, bytesRead, contentLength - bytesRead)
                if (readResult == -1) return false
                bytesRead += readResult
            }
            val content = String(buffer, charset(headers.charset))
            try {
                val message: Message = jsonHandler.parseMessage(content)
                messageConsumer.consume(message)
//            } catch (exception: MessageIssueException) {
//                // An issue was found while parsing or validating the message
//                if (issueHandler != null) issueHandler.handle(
//                    exception.rpcMessage, exception.issues
//                ) else fireError(exception)
//            }
            } catch (exception: SerializationException) {
                if (issueHandler != null) issueHandler.handle(
                    exception.issues
                ) else fireError(exception)
            }
        } catch (exception: Exception) {
            // UnsupportedEncodingException can be thrown by String constructor
            // JsonParseException can be thrown by jsonHandler
            // We also catch arbitrary exceptions that are thrown by message consumers in order to keep this thread alive
            fireError(exception)
        }
        return true
    }

    override fun close() {
        keepRunning = false
    }

    companion object {
        private val LOG = Logger.getLogger(StreamMessageProducer::class.jvmName)
    }
}
