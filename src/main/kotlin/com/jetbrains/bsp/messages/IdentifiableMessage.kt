package com.jetbrains.bsp.messages

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder

typealias MessageId = @Serializable(EitherSerializer::class) Either<Int, String>

val MessageId.asString: String
    get() = when (this) {
        is Either.Left -> value.toString()
        is Either.Right -> value
    }

abstract class IdentifiableMessage : Message() {
    abstract val id: MessageId
}

class EitherSerializer<L, R>(private val leftSerializer: KSerializer<L>, private val rightSerializer: KSerializer<R>) :
    KSerializer<Either<L, R>> {
    override val descriptor: SerialDescriptor = TODO()

    override fun deserialize(decoder: Decoder): Either<L, R> {
        require(decoder is JsonDecoder) { "only works in JSON format" }
        val element = decoder.decodeJsonElement()

        return try {
            decoder.json.decodeFromJsonElement(rightSerializer, element).right()
        } catch (_: SerializationException) {
            decoder.json.decodeFromJsonElement(leftSerializer, element).left()
        }
    }


    override fun serialize(encoder: Encoder, value: Either<L, R>) {
        when (value) {
            is Either.Left -> encoder.encodeSerializableValue(leftSerializer, value.value)
            is Either.Right -> encoder.encodeSerializableValue(rightSerializer, value.value)
        }
    }
}

