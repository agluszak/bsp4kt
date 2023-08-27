package json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import org.jetbrains.jsonrpc4kt.json.serializers.EnumAsIntSerializer

@Serializable(with = MyEnum.Companion::class)
enum class MyEnum(override val value: Int) : org.jetbrains.jsonrpc4kt.IntEnum {
    A(1),
    B(2),
    C(3);

    companion object : KSerializer<MyEnum> by EnumAsIntSerializer(MyEnum::class)
}

