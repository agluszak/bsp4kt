package com.jetbrains.bsp.json.serializers

import com.jetbrains.bsp.BspEnum
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

class EnumAsIntSerializer<T>(
    private val enumClass: KClass<T>
) : KSerializer<T> where T : Enum<T>, T : BspEnum {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(enumClass.jvmName, PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeInt(value.value)
    }

    override fun deserialize(decoder: Decoder): T {
        val v = decoder.decodeInt()
        return enumClass.java.enumConstants.find { it.value == v }
            ?: error("No enum constant with value $v for enum class $enumClass")
    }
}