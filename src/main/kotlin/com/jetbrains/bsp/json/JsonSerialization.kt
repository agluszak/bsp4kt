package com.jetbrains.bsp.json

import kotlinx.serialization.json.JsonElement

interface JsonDeserialization<T> {
    fun deserialize(json: JsonElement): T
}

interface JsonSerialization {
    fun serializeToJson(): JsonElement
}