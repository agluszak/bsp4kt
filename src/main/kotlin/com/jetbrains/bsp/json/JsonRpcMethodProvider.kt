package com.jetbrains.bsp.json

/**
 * Provides [JsonRpcMethod]. Can be implemented by [Endpoint]s to
 * provide information about the supported methods.
 */
interface JsonRpcMethodProvider {
    fun supportedMethods(): Map<String, JsonRpcMethod>
}
