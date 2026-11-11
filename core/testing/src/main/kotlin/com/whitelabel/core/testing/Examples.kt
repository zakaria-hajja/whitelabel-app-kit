package com.whitelabel.core.testing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A value from an example, by a dotted path into its JSON, e.g. `data.tokens.accessToken` or `data.0.title`. */
fun OpenApiSpec.exampleValue(path: String, method: String, status: Int, pointer: String): String {
    var node: JsonElement = Json.parseToJsonElement(responseExample(path, method, status))
    pointer.split('.').forEach { key -> node = if (node is JsonObject) node.jsonObject.getValue(key) else node.jsonArray[key.toInt()] }
    return node.jsonPrimitive.content
}
