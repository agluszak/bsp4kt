package com.jetbrains.jsonrpc4kt

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
    fun <R> computeAsync(code: Function1<CancelChecker, R>): CompletableFuture<R> {
        val start: CompletableFuture<CancelChecker> = CompletableFuture<CancelChecker>()
        val result = start.thenApplyAsync(code)
        start.complete(FutureCancelChecker(result))
        return result
    }

    /**
     * A utility method to create a [CompletableFuture] with cancellation support.
     *
     * @param code a function that accepts a [CancelChecker] and returns the to be computed value
     * @return a future
     */
    fun <R> computeAsync(executor: Executor, code: Function1<CancelChecker, R>): CompletableFuture<R> {
        val start: CompletableFuture<CancelChecker> = CompletableFuture<CancelChecker>()
        val result = start.thenApplyAsync(code, executor)
        start.complete(FutureCancelChecker(result))
        return result
    }

    class FutureCancelChecker(private val future: CompletableFuture<*>) : CancelChecker {
        override fun checkCanceled() {
            if (future.isCancelled) throw CancellationException()
        }

        override val isCanceled: Boolean
            get() = future.isCancelled
    }
}
