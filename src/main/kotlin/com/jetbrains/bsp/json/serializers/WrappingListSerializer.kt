package com.jetbrains.bsp.json.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTransformingSerializer

class WrappingListSerializer<T>(private val elementSerializer: KSerializer<T>) : JsonTransformingSerializer<List<T>>(
    ListSerializer(elementSerializer)
) {
    // If response is not an array, then it is a single object that should be wrapped into the array
    override fun transformDeserialize(element: JsonElement): JsonElement  {
        return  if (element !is JsonArray) JsonArray(listOf(element)) else element
    }


    override fun transformSerialize(element: JsonElement): JsonElement {
        if (element !is JsonArray) return element
        return element.singleOrNull() ?: element
    }
}
