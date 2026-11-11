package com.whitelabel.core.testing

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * The vendored backend contract (`api/openapi.json`) for tests: its examples, and validation of
 * JSON against its schemas. Paths are as the spec writes them, e.g. `/api/v1/catalog`.
 *
 * The file comes from the system property `whitelabel.openapi`, which the convention plugins set on
 * every test task; without it (e.g. a test run from the IDE) the nearest `api/openapi.json` above
 * the working directory is used.
 */
object OpenApiSpec {

    private val mapper = ObjectMapper()
    private val text: String by lazy { specFile().readText() }
    private val root: JsonNode by lazy { mapper.readTree(text) }
    private val schemas = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)

    val json: JsonObject by lazy { Json.parseToJsonElement(text).jsonObject }

    /** Every (path, method) the spec defines, methods in lower case. */
    fun operations(): List<Pair<String, String>> =
        root.path("paths").properties().flatMap { (path, ops) ->
            ops.properties().map { it.key }.filter { it in METHODS }.map { path to it }
        }

    /** The spec path an actual request path matches, e.g. `/api/v1/catalog/abc` → `/api/v1/catalog/{id}`. */
    fun templateFor(actualPath: String): String? =
        root.path("paths").properties().map { it.key }.firstOrNull { template ->
            Regex("^" + template.split('/').joinToString("/") { if (it.startsWith("{")) "[^/]+" else Regex.escape(it) } + "$")
                .matches(actualPath)
        }

    /** Whether the operation needs a bearer token (its `security` is non-empty). */
    fun requiresBearer(path: String, method: String): Boolean =
        operation(path, method).path("security").let { it.isArray && it.size() > 0 }

    /** Why these query parameters don't match the operation's declared ones; empty when they do. */
    fun queryParameterErrors(path: String, method: String, query: Map<String, List<String>>): List<String> {
        val declared = operation(path, method).path("parameters").elements().asSequence()
            .filter { it.path("in").asText() == "query" }
            .associateBy { it.path("name").asText() }
        return query.flatMap { (name, values) ->
            val parameter = declared[name] ?: return@flatMap listOf("query parameter '$name' is not in the contract for $method $path")
            val schema = parameter.path("schema")
            values.flatMap { value -> validate(schema, mapper.writeValueAsString(typed(schema, value)), "$method $path query '$name'").map { "query '$name'=$value: $it" } }
        }
    }

    /** A query string value as the JSON type its schema declares, so `page=2` checks as an integer. */
    private fun typed(schema: JsonNode, value: String): JsonNode = when (schema.path("type").asText()) {
        "integer" -> value.toLongOrNull()?.let { mapper.nodeFactory.numberNode(it) } ?: mapper.nodeFactory.textNode(value)
        "number" -> value.toDoubleOrNull()?.let { mapper.nodeFactory.numberNode(it) } ?: mapper.nodeFactory.textNode(value)
        "boolean" -> value.toBooleanStrictOrNull()?.let { mapper.nodeFactory.booleanNode(it) } ?: mapper.nodeFactory.textNode(value)
        else -> mapper.nodeFactory.textNode(value)
    }

    /** The response statuses documented for an operation. */
    fun documentedStatuses(path: String, method: String): List<Int> =
        operation(path, method).path("responses").properties().mapNotNull { it.key.toIntOrNull() }

    fun responseExample(path: String, method: String, status: Int): String =
        responseExampleOrNull(path, method, status)
            ?: throw AssertionError("The spec has no example for $method $path $status")

    fun responseExampleOrNull(path: String, method: String, status: Int): String? =
        example(response(path, method, status).path("content").path(JSON))

    /**
     * The smallest response the schema allows, built from the example: only required properties are
     * kept, and every nullable one is `null`. Decoding it proves the app doesn't rely on what the
     * contract leaves optional.
     */
    fun minimalResponse(path: String, method: String, status: Int): String {
        val example = mapper.readTree(responseExample(path, method, status))
        return mapper.writeValueAsString(minimal(example, response(path, method, status).path("content").path(JSON).path("schema")))
    }

    private fun minimal(node: JsonNode, schemaNode: JsonNode): JsonNode {
        val schema = resolve(schemaNode)
        val properties = schema.path("properties")
        return when {
            node.isObject && properties.isObject -> {
                val required = schema.path("required").elements().asSequence().map { it.asText() }.toSet()
                mapper.createObjectNode().apply {
                    node.properties().filter { it.key in required }.forEach { (key, value) ->
                        val property = resolve(properties.path(key))
                        set<JsonNode>(key, if (property.allowsNull()) mapper.nullNode() else minimal(value, property))
                    }
                }
            }
            node.isArray -> mapper.createArrayNode().apply { node.forEach { add(minimal(it, schema.path("items"))) } }
            else -> node
        }
    }

    private fun resolve(schema: JsonNode): JsonNode {
        val ref = schema.path("\$ref").asText("")
        if (!ref.startsWith("#/")) return schema
        return ref.removePrefix("#/").split('/').fold(root) { node, key -> node.path(key) }.let(::resolve)
    }

    private fun JsonNode.allowsNull(): Boolean {
        val type = path("type")
        return (type.isArray && type.any { it.asText() == "null" }) || type.asText() == "null"
    }

    fun requestExample(path: String, method: String): String? =
        example(operation(path, method).path("requestBody").path("content").path(JSON))

    /** Why [body] doesn't match the response schema; empty when it does. */
    fun responseErrors(path: String, method: String, status: Int, body: String): List<String> =
        validate(response(path, method, status).path("content").path(JSON).path("schema"), body, "$method $path $status response")

    /** Why [body] doesn't match the request body schema; empty when it does. */
    fun requestErrors(path: String, method: String, body: String): List<String> =
        validate(operation(path, method).path("requestBody").path("content").path(JSON).path("schema"), body, "$method $path request")

    private fun operation(path: String, method: String): JsonNode {
        val node = root.path("paths").path(path).path(method.lowercase())
        if (node.isMissingNode) throw AssertionError("The spec has no operation ${method.lowercase()} $path")
        return node
    }

    private fun response(path: String, method: String, status: Int): JsonNode {
        val node = operation(path, method).path("responses").path(status.toString())
        if (node.isMissingNode) throw AssertionError("The spec documents no $status for $method $path")
        return node
    }

    private fun example(media: JsonNode): String? {
        media.get("example")?.let { return mapper.writeValueAsString(it) }
        return media.path("examples").elements().asSequence().firstOrNull()?.get("value")?.let(mapper::writeValueAsString)
    }

    /**
     * Validates against [schemaNode]. Its `$ref`s point into `#/components/schemas`, so the schema is
     * validated with the spec's `components` carried alongside, where those pointers resolve.
     */
    private fun validate(schemaNode: JsonNode, body: String, what: String): List<String> {
        if (schemaNode.isMissingNode) throw AssertionError("The spec has no schema for the $what")
        val document = (schemaNode.deepCopy<JsonNode>() as ObjectNode).apply { set<JsonNode>("components", root.path("components")) }
        return schemas.getSchema(document).validate(mapper.readTree(body)).map { it.message }
    }

    private fun specFile(): File = specFileFrom(System.getProperty("whitelabel.openapi"), System.getProperty("user.dir"))

    private const val JSON = "application/json"
    private val METHODS = setOf("get", "post", "put", "patch", "delete")
}

/**
 * The contract file: [configured] when set (it must exist), else the nearest `api/openapi.json` above
 * [workingDir]. Failures are [AssertionError]s: inside a fake engine an I/O error would be mapped to
 * "no internet" and hide the real problem.
 */
internal fun specFileFrom(configured: String?, workingDir: String): File {
    if (configured != null) {
        val file = File(configured)
        if (!file.isFile) throw AssertionError("whitelabel.openapi points at $configured, which doesn't exist")
        return file
    }
    var dir: File? = File(workingDir).absoluteFile
    while (dir != null) {
        val candidate = File(dir, "api/openapi.json")
        if (candidate.isFile) return candidate
        dir = dir.parentFile
    }
    throw AssertionError("api/openapi.json not found above $workingDir: set the whitelabel.openapi system property")
}

