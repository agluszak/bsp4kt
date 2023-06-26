package com.jetbrains.bsp

import java.util.concurrent.CompletableFuture

/**
 * An endpoint is a generic interface that accepts jsonrpc requests and notifications.
 */
interface Endpoint {
    fun request(method: String, parameter: Any?): CompletableFuture<*>

    fun notify(method: String, parameter: Any?)
}