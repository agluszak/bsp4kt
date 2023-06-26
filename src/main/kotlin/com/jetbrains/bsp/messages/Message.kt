package com.jetbrains.bsp.messages

import kotlinx.serialization.Serializable

@Serializable
abstract class Message {
    val jsonrpc: String = JSONRPC_VERSION

companion object {
    const val JSONRPC_VERSION = "2.0"
    const val CONTENT_LENGTH_HEADER = "Content-Length"
    const val CONTENT_TYPE_HEADER = "Content-Type"
    const val JSON_MIME_TYPE = "application/json"
    const val CRLF = "\r\n"
}
}