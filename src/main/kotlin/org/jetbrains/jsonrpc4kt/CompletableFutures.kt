package org.jetbrains.jsonrpc4kt

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlin.coroutines.cancellation.CancellationException

object CompletableFutures {
    /**
     * A utility method to create a [CompletableFuture] with cancellation support.
     *
     * @param code a function that accepts a [CancelChecker] and returns the to be computed value
     * @return a future
     */
    fun <R> computeAsync(code: Function1<org.jetbrains.jsonrpc4kt.CancelChecker, R>): CompletableFuture<R> {
        val start: CompletableFuture<org.jetbrains.jsonrpc4kt.CancelChecker> =
            CompletableFuture<org.jetbrains.jsonrpc4kt.CancelChecker>()
        val result = start.thenApplyAsync(code)
        start.complete(org.jetbrains.jsonrpc4kt.CompletableFutures.FutureCancelChecker(result))
        return result
    }

    /**
     * A utility method to create a [CompletableFuture] with cancellation support.
     *
     * @param code a function that accepts a [CancelChecker] and returns the to be computed value
     * @return a future
     */
    fun <R> computeAsync(
        executor: Executor,
        code: Function1<org.jetbrains.jsonrpc4kt.CancelChecker, R>
    ): CompletableFuture<R> {
        val start: CompletableFuture<org.jetbrains.jsonrpc4kt.CancelChecker> =
            CompletableFuture<org.jetbrains.jsonrpc4kt.CancelChecker>()
        val result = start.thenApplyAsync(code, executor)
        start.complete(org.jetbrains.jsonrpc4kt.CompletableFutures.FutureCancelChecker(result))
        return result
    }

    class FutureCancelChecker(private val future: CompletableFuture<*>) : org.jetbrains.jsonrpc4kt.CancelChecker {
        override fun checkCanceled() {
            if (future.isCancelled) throw CancellationException()
        }

        override val isCanceled: Boolean
            get() = future.isCancelled
    }
}
