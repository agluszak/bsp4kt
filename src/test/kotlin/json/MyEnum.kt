package json

import com.jetbrains.bsp.BspEnum
import com.jetbrains.bsp.json.serializers.EnumAsIntSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable(with = MyEnum.Companion::class)
enum class MyEnum(override val value: Int): BspEnum {
    A(1),
    B(2),
    C(3);

    companion object : KSerializer<MyEnum> by EnumAsIntSerializer(MyEnum::class)
}

