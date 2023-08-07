package com.jetbrains.jsonrpc4kt

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException
import java.nio.channels.ClosedChannelException

/**
 * An exception thrown when accessing the JSON-RPC communication channel fails.
 */
class JsonRpcException(cause: Throwable?) : RuntimeException(cause) {
    companion object {
        /**
         * Whether the given exception indicates that the currently accessed stream has been closed.
         */
        fun indicatesStreamClosed(thr: Throwable?): Boolean {
            return (thr is InterruptedIOException
                    || thr is ClosedChannelException || thr is IOException && ("Stream closed" == thr.message || "Pipe closed" == thr.message)) || thr is SocketException && ("Connection reset" == thr.message || "Socket closed" == thr.message || "Broken pipe (Write failed)" == thr.message) || thr is JsonRpcException && indicatesStreamClosed(
                thr.cause
            )
        }
    }
}
