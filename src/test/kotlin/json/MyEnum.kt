package json

import kotlinx.serialization.Serializable

@Serializable
enum class MyEnum(val value: Int) {
    A(1),
    B(2),
    C(3);
}

