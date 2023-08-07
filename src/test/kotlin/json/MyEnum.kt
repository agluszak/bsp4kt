package json

import com.jetbrains.jsonrpc4kt.IntEnum
import com.jetbrains.jsonrpc4kt.json.serializers.EnumAsIntSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable(with = MyEnum.Companion::class)
enum class MyEnum(override val value: Int) : IntEnum {
    A(1),
    B(2),
    C(3);

    companion object : KSerializer<MyEnum> by EnumAsIntSerializer(MyEnum::class)
}

