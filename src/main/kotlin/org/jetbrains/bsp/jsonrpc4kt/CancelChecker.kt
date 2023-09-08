package org.jetbrains.jsonrpc4kt

import java.util.concurrent.CancellationException


/**
 * Used for processing requests with cancellation support.
 */
interface CancelChecker {
    /**
     * Throw a [CancellationException] if the currently processed request
     * has been canceled.
     */
    fun checkCanceled()
    val isCanceled: Boolean
        /**
         * Check for cancellation without throwing an exception.
         */
        get() {
            try {
                checkCanceled()
            } catch (ce: CancellationException) {
                return true
            }
            return false
        }
}

