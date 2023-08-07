package com.jetbrains.jsonrpc4kt.json

import com.jetbrains.jsonrpc4kt.*
import com.jetbrains.jsonrpc4kt.messages.Message
import com.jetbrains.jsonrpc4kt.messages.Message.Companion.CONTENT_LENGTH_HEADER
import com.jetbrains.jsonrpc4kt.messages.Message.Companion.CONTENT_TYPE_HEADER
import kotlinx.serialization.SerializationException
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException
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
) : MessageProducer, Closeable {

    private var keepRunning = false

    data class Headers(
        var contentLength: Int = -1,
        var charset: String = StandardCharsets.UTF_8.name()
    )

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
                                logError(
                                    IllegalStateException(
                                        ("Missing header $CONTENT_LENGTH_HEADER in input \"$debugBuilder\"")
                                    )
                                )
                            } else {
                                val result = handleMessage(input, headers, messageConsumer)
                                if (!result) keepRunning = false
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
            }
        } catch (exception: IOException) {
            if (JsonRpcException.indicatesStreamClosed(exception)) {
                // Only log the error if we had intended to keep running
                if (keepRunning) logStreamClosed(exception)
            } else throw JsonRpcException(exception)
        } finally {
            keepRunning = false
        }
    }

    private fun logException(exception: Exception) {
        LOG.log(Level.WARNING, "An error occurred while processing an incoming message", exception)
    }

    /**
     * Log an error.
     */
    private fun logError(error: Throwable) {
        val message =
            if (error.message != null) error.message else "An error occurred while processing an incoming message."
        LOG.log(Level.SEVERE, message, error)
    }

    /**
     * Report that the stream was closed through an exception.
     */
    private fun logStreamClosed(cause: Exception) {
        val message = if (cause.message != null) cause.message else "The input stream was closed."
        LOG.log(Level.INFO, message, cause)
    }

    /**
     * Parse a header attribute and set the corresponding data in the [Headers] fields.
     */
    private fun parseHeader(line: String, headers: Headers) {
        val sepIndex = line.indexOf(':')
        if (sepIndex >= 0) {
            val key = line.substring(0, sepIndex).trim { it <= ' ' }
            when (key) {
                CONTENT_LENGTH_HEADER -> try {
                    headers.contentLength = line.substring(sepIndex + 1).trim { it <= ' ' }.toInt()
                } catch (e: NumberFormatException) {
                    logError(e)
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
    private fun handleMessage(input: InputStream, headers: Headers, messageConsumer: MessageConsumer): Boolean {
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
            val message: Message = jsonHandler.deserializeMessage(content)
            messageConsumer.consume(message)
        } catch (e: UnsupportedEncodingException) {
            // UnsupportedEncodingException can be thrown by String constructor
            logException(e)
        } catch (e: SerializationException) {
            // SerializationException can be thrown by jsonHandler
            logException(e)
        } catch (e: Exception) {
            // We catch arbitrary exceptions that are thrown by message consumers in order to keep this thread alive
            logError(e)
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
