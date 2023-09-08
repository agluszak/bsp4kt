package org.jetbrains.jsonrpc4kt.json

import kotlinx.serialization.json.JsonElement

interface JsonDeserialization<T> {
    fun deserialize(json: JsonElement): T
}

interface JsonSerialization {
    fun serializeToJson(): JsonElement
}