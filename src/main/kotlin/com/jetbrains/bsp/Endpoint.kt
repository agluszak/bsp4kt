package com.jetbrains.bsp

import com.jetbrains.bsp.messages.JsonParams
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.CompletableFuture

/**
 * An endpoint is a generic interface that accepts jsonrpc requests and notifications.
 */
interface Endpoint {
    fun request(method: String, params: JsonParams?): CompletableFuture<JsonElement>

    fun notify(method: String, params: JsonParams?)
}