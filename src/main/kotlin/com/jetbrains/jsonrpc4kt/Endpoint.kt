package com.jetbrains.jsonrpc4kt

/**
 * An endpoint is a generic interface that accepts jsonrpc requests and notifications.
 */
interface Endpoint {
    suspend fun request(method: String, params: List<Any?>): Any?

    fun notify(method: String, params: List<Any?>)
}