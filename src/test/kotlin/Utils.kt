import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

fun String.removePrefixOrThrow(prefix: String): String {
    if (!this.startsWith(prefix)) {
        throw IllegalArgumentException("String $this does not start with prefix $prefix")
    }
    return this.removePrefix(prefix)
}

fun String.removeContentLengthAndParse(length: Int): JsonElement {
    val content = this.removePrefixOrThrow("Content-Length: $length\r\n\r\n")
    return Json.parseToJsonElement(content)
}