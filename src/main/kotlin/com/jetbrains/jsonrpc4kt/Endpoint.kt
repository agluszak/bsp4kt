package com.jetbrains.jsonrpc4kt

import java.util.concurrent.CompletableFuture

/**
 * An endpoint is a generic interface that accepts jsonrpc requests and notifications.
 */
interface Endpoint {
    fun request(method: String, params: List<Any?>): CompletableFuture<*>

    fun notify(method: String, params: List<Any?>)
}