@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package com.whitelabel.core.mockapi

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.concurrent.Volatile
import kotlinx.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * An in-memory backend that answers like `backend-api` and follows its contract, so an app runs
 * with no server (`-Pwhitelabel.mockApi=true`) and end-to-end tests run hermetically.
 *
 * It keeps users, sessions, settings and a catalog in memory. Access tokens last
 * [FakeData.ACCESS_TTL_SECONDS] by [now]; refresh tokens are single use and rotate. Tests steer it
 * with [expireAccessTokens], [revokeSessions], [offline] and [features]. Every response it gives
 * is checked against the contract by `FakeBackendContractTest`.
 */
class FakeBackend internal constructor(private val now: () -> Instant) {

    constructor() : this({ Clock.System.now() })


    /** One answered request: lower-case [method], the full [path], and the response. */
    data class Exchange(val method: String, val path: String, val status: Int, val body: String)

    /** Remote feature flags returned by `app/config`; `catalog=false` also switches the catalog off. Set them before the app starts. */
    val features: MutableMap<String, Boolean> = mutableMapOf("catalog" to true, "profile" to true)

    /** When true, every request fails as if the device had no connection. */
    @Volatile var offline: Boolean = false

    private val lock = SynchronizedObject()
    private val users = FakeData.users().toMutableList()
    private val items = FakeData.items()
    private val access = mutableMapOf<String, Access>()
    private val refresh = mutableMapOf<String, String>() // refresh token → session id
    private val sessions = mutableMapOf<String, Session>()
    private val resetTokens = mutableMapOf<String, String>() // token → user id
    private val log = mutableListOf<Exchange>()
    private val sent = mutableListOf<String>()

    /** `"METHOD /path"` of every request, in order, including ones answered offline. */
    val requests: List<String> get() = synchronized(lock) { sent.toList() }

    /** Every answered request with its response. */
    val exchanges: List<Exchange> get() = synchronized(lock) { log.toList() }

    /** The token from the latest `auth/forgot-password`, which a real backend would email. */
    var lastResetToken: String? = null
        private set

    val engine: HttpClientEngine = MockEngine { request -> answer(request) }

    /** Makes every issued access token expired; the next call gets `TOKEN_EXPIRED`. */
    fun expireAccessTokens(): Unit = synchronized(lock) {
        access.entries.forEach { it.setValue(it.value.copy(expiresAt = now().minus(1.seconds))) }
    }

    /** Ends every session, as a sign-out elsewhere or an admin would; tokens become unknown. */
    fun revokeSessions(): Unit = synchronized(lock) {
        access.clear()
        refresh.clear()
        sessions.clear()
    }

    private data class Access(val userId: String, val sessionId: String, val expiresAt: Instant)
    private data class Session(val id: String, val userId: String, val createdAt: Instant, var lastUsedAt: Instant, val userAgent: String?)
    private class Reply(val status: Int, val body: JsonObject)

    private suspend fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData {
        val method = request.method.value.lowercase()
        val path = request.url.encodedPath
        synchronized(lock) { sent += "${request.method.value} $path" }
        if (offline) throw IOException("FakeBackend is offline")
        val body = request.body.toByteArray().decodeToString().takeIf { it.isNotBlank() }
            ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: JsonObject(emptyMap())
        val reply = synchronized(lock) { route(method, path.removePrefix(API), request, body) }
        val text = reply.body.toString()
        synchronized(lock) { log += Exchange(method, path, reply.status, text) }
        return respond(text, HttpStatusCode.fromValue(reply.status), headersOf(HttpHeaders.ContentType, "application/json"))
    }

    private fun route(method: String, path: String, request: HttpRequestData, body: JsonObject): Reply {
        val query = request.url.parameters
        return when {
            method == "get" && path == "/health" -> ok(buildJsonObject { put("status", "ok"); put("db", "up") })
            method == "get" && path == "/app/config" -> ok(config())
            method == "post" && path == "/auth/register" -> register(body, request)
            method == "post" && path == "/auth/login" -> login(body, request)
            method == "post" && path == "/auth/refresh" -> refreshTokens(body)
            method == "post" && path == "/auth/logout" -> logout(body)
            method == "post" && path == "/auth/forgot-password" -> forgotPassword(body)
            method == "post" && path == "/auth/reset-password" -> resetPassword(body)
            else -> authenticated(request) { user, session -> secured(method, path, user, session, query, body) }
        }
    }

    private fun secured(
        method: String,
        path: String,
        user: FakeData.User,
        session: String,
        query: io.ktor.http.Parameters,
        body: JsonObject,
    ): Reply = when {
        method == "get" && path == "/auth/me" -> ok(buildJsonObject {
            put("user", user.json())
            put("tenant", tenant())
            putJsonObject("session") {
                put("id", session)
                put("expiresAt", now().plus((30L * 86_400).seconds).toString())
            }
        })
        method == "get" && path == "/auth/sessions" -> ok(buildJsonObject {
            put("sessions", JsonArray(sessions.values.filter { it.userId == user.id }.map { it.json(current = it.id == session) }))
        })
        method == "delete" && path.startsWith("/auth/sessions/") -> {
            val id = path.removePrefix("/auth/sessions/")
            if (sessions[id]?.userId != user.id) error(404, "NOT_FOUND") else { endSession(id); ok(JsonObject(emptyMap())) }
        }
        method == "get" && path == "/users/me" -> ok(user.json())
        method == "patch" && path == "/users/me" -> updateProfile(user, body)
        method == "delete" && path == "/users/me" -> when {
            body.string("password").isNullOrEmpty() -> validation("password" to "Too small: expected string to have >=1 characters")
            body.string("password") != user.password -> error(401, "INVALID_CREDENTIALS")
            else -> {
                users.remove(user)
                sessions.values.filter { it.userId == user.id }.map { it.id }.forEach(::endSession)
                ok(JsonObject(emptyMap()))
            }
        }
        method == "patch" && path == "/users/me/password" -> changePassword(user, session, body)
        method == "get" && path == "/settings" -> ok(user.settings())
        method == "patch" && path == "/settings" -> updateSettings(user, body)
        method == "get" && path == "/catalog" -> if (features["catalog"] == false) error(404, "FEATURE_DISABLED") else catalog(query)
        method == "get" && path.startsWith("/catalog/") -> when {
            features["catalog"] == false -> error(404, "FEATURE_DISABLED")
            else -> items.firstOrNull { it.id == path.removePrefix("/catalog/") }?.let { ok(it.json()) } ?: error(404, "NOT_FOUND")
        }
        else -> error(404, "NOT_FOUND")
    }

    // --- auth ---

    private fun register(body: JsonObject, request: HttpRequestData): Reply {
        val email = body.string("email").orEmpty().trim().lowercase()
        val password = body.string("password").orEmpty()
        val problems = buildList {
            if (!EMAIL.matches(email)) add("email" to "Invalid email address")
            if (password.length < MIN_PASSWORD) add("password" to "Too small: expected string to have >=$MIN_PASSWORD characters")
        }
        if (problems.isNotEmpty()) return validation(*problems.toTypedArray())
        if (users.any { it.email == email }) return error(409, "EMAIL_ALREADY_REGISTERED")
        val user = FakeData.User(
            id = Uuid.random().toString(),
            email = email,
            password = password,
            firstName = body.string("firstName"),
            lastName = body.string("lastName"),
            createdAt = now(),
        )
        users += user
        return Reply(201, success(signedIn(user, request)))
    }

    private fun login(body: JsonObject, request: HttpRequestData): Reply {
        val email = body.string("email").orEmpty().trim().lowercase()
        val password = body.string("password").orEmpty()
        if (password.isEmpty()) return validation("password" to "Too small: expected string to have >=1 characters")
        val user = users.firstOrNull { it.email == email && it.password == password } ?: return error(401, "INVALID_CREDENTIALS")
        return ok(signedIn(user, request))
    }

    private fun refreshTokens(body: JsonObject): Reply {
        val token = body.string("refreshToken").orEmpty()
        if (token.isEmpty()) return validation("refreshToken" to "Too small: expected string to have >=1 characters")
        // Single use: a reused or unknown token is refused.
        val sessionId = refresh.remove(token) ?: return error(401, "INVALID_REFRESH_TOKEN")
        val session = sessions[sessionId] ?: return error(401, "INVALID_REFRESH_TOKEN")
        access.entries.removeAll { it.value.sessionId == sessionId }
        session.lastUsedAt = now()
        return ok(buildJsonObject { put("tokens", issue(session)) })
    }

    private fun logout(body: JsonObject): Reply {
        val token = body.string("refreshToken").orEmpty()
        if (token.isEmpty()) return validation("refreshToken" to "Too small: expected string to have >=1 characters")
        refresh[token]?.let(::endSession) // An unknown token still answers 200, as the backend does.
        return ok(JsonObject(emptyMap()))
    }

    private fun forgotPassword(body: JsonObject): Reply {
        val email = body.string("email").orEmpty().trim().lowercase()
        if (!EMAIL.matches(email)) return validation("email" to "Invalid email address")
        // 202 whether or not the account exists, so the answer reveals nothing.
        issueToken(email)
        return Reply(202, success(JsonObject(emptyMap())))
    }

    /** What a reset email would carry for [email], or null for an unknown account (tests open the reset link with it). */
    fun issueResetToken(email: String): String? = synchronized(lock) { issueToken(email.trim().lowercase()) }

    private fun issueToken(email: String): String? = users.firstOrNull { it.email == email }?.let { user ->
        val token = Uuid.random().toString()
        resetTokens[token] = user.id
        lastResetToken = token
        token
    }

    private fun resetPassword(body: JsonObject): Reply {
        val token = body.string("token").orEmpty()
        val password = body.string("newPassword").orEmpty()
        if (password.length < MIN_PASSWORD) return validation("newPassword" to "Too small: expected string to have >=$MIN_PASSWORD characters")
        val user = resetTokens.remove(token)?.let { id -> users.firstOrNull { it.id == id } } ?: return error(400, "INVALID_RESET_TOKEN")
        user.password = password
        sessions.values.filter { it.userId == user.id }.map { it.id }.forEach(::endSession)
        return ok(JsonObject(emptyMap()))
    }

    private fun changePassword(user: FakeData.User, session: String, body: JsonObject): Reply {
        val newPassword = body.string("newPassword").orEmpty()
        if (newPassword.length < MIN_PASSWORD) return validation("newPassword" to "Too small: expected string to have >=$MIN_PASSWORD characters")
        if (body.string("currentPassword") != user.password) return error(401, "INVALID_CREDENTIALS")
        user.password = newPassword
        // Other sessions end; this one stays signed in.
        sessions.values.filter { it.userId == user.id && it.id != session }.map { it.id }.forEach(::endSession)
        return ok(JsonObject(emptyMap()))
    }

    private fun signedIn(user: FakeData.User, request: HttpRequestData): JsonObject {
        val session = Session(Uuid.random().toString(), user.id, now(), now(), request.headers[HttpHeaders.UserAgent])
        sessions[session.id] = session
        return buildJsonObject {
            put("user", user.json())
            put("tokens", issue(session))
        }
    }

    private fun issue(session: Session): JsonObject {
        val accessToken = "fake-access-" + Uuid.random()
        val refreshToken = "fake-refresh-" + Uuid.random()
        access[accessToken] = Access(session.userId, session.id, now().plus((FakeData.ACCESS_TTL_SECONDS).seconds))
        refresh[refreshToken] = session.id
        return buildJsonObject {
            put("accessToken", accessToken)
            put("refreshToken", refreshToken)
            put("tokenType", "Bearer")
            put("expiresIn", FakeData.ACCESS_TTL_SECONDS)
        }
    }

    private fun endSession(id: String) {
        sessions.remove(id)
        access.entries.removeAll { it.value.sessionId == id }
        refresh.entries.removeAll { it.value == id }
    }

    private inline fun authenticated(request: HttpRequestData, block: (FakeData.User, String) -> Reply): Reply {
        val token = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()
            ?: return error(401, "UNAUTHENTICATED")
        val grant = access[token] ?: return error(401, "UNAUTHENTICATED")
        if (now() >= grant.expiresAt) return error(401, "TOKEN_EXPIRED")
        val user = users.firstOrNull { it.id == grant.userId } ?: return error(401, "UNAUTHENTICATED")
        sessions[grant.sessionId]?.lastUsedAt = now()
        return block(user, grant.sessionId)
    }

    // --- profile and settings ---

    private fun updateProfile(user: FakeData.User, body: JsonObject): Reply {
        val problems = listOf("firstName", "lastName").mapNotNull { field ->
            val value = body[field] as? JsonPrimitive ?: return@mapNotNull null
            if (value !is JsonNull && value.content.isEmpty()) field to "Too small: expected string to have >=1 characters" else null
        }
        if (problems.isNotEmpty()) return validation(*problems.toTypedArray())
        if ("firstName" in body) user.firstName = body.string("firstName")
        if ("lastName" in body) user.lastName = body.string("lastName")
        return ok(user.json())
    }

    private fun updateSettings(user: FakeData.User, body: JsonObject): Reply {
        val theme = body.string("theme")
        if (theme != null && theme !in THEMES) return validation("theme" to "Invalid option: expected one of \"SYSTEM\"|\"LIGHT\"|\"DARK\"")
        val language = body.string("language")
        if (language != null && language.length !in 2..10) return validation("language" to "Too small: expected string to have >=2 characters")
        theme?.let { user.theme = it }
        language?.let { user.language = it }
        (body["notifications"] as? JsonObject)?.forEach { (key, value) ->
            val on = (value as? JsonPrimitive)?.booleanOrNull
            if (key in user.notifications && on != null) user.notifications[key] = on
        }
        return ok(user.settings())
    }

    // --- catalog ---

    private fun catalog(query: io.ktor.http.Parameters): Reply {
        val page = query["page"]?.toIntOrNull() ?: 1
        val pageSize = query["pageSize"]?.toIntOrNull() ?: DEFAULT_PAGE_SIZE
        val sort = query["sort"] ?: "createdAt:desc"
        when {
            page < 1 -> return validation("page" to "Too small: expected number to be >=1")
            pageSize !in 1..MAX_PAGE_SIZE -> return validation("pageSize" to "Too big: expected number to be <=$MAX_PAGE_SIZE")
            sort !in SORTS -> return validation("sort" to "Invalid option")
        }
        val q = query["q"]?.trim().orEmpty()
        val category = query["category"]
        val matching = items
            .filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) || it.description?.contains(q, ignoreCase = true) == true }
            .filter { category == null || it.category == category }
            .let { list ->
                when (sort) {
                    "createdAt:asc" -> list.sortedBy { it.createdAt }
                    "title:asc" -> list.sortedBy { it.title }
                    "title:desc" -> list.sortedByDescending { it.title }
                    else -> list.sortedByDescending { it.createdAt }
                }
            }
        val totalPages = (matching.size + pageSize - 1) / pageSize
        val slice = matching.drop((page - 1) * pageSize).take(pageSize)
        return Reply(200, buildJsonObject {
            put("success", true)
            put("data", JsonArray(slice.map { it.json() }))
            putJsonObject("meta") {
                put("requestId", Uuid.random().toString())
                putJsonObject("pagination") {
                    put("page", page)
                    put("pageSize", pageSize)
                    put("totalItems", matching.size)
                    put("totalPages", totalPages)
                }
            }
        })
    }

    // --- shapes ---

    private fun config() = buildJsonObject {
        put("tenant", tenant())
        putJsonObject("app") {
            put("displayName", "Mock backend")
            put("latestVersionCode", 1)
            put("minSupportedVersionCode", 1)
        }
        put("features", JsonObject(features.mapValues { JsonPrimitive(it.value) }))
        putJsonObject("support") { put("email", "support@mock.invalid") }
        putJsonObject("legal") {
            put("privacyPolicyUrl", "https://mock.invalid/privacy")
            put("termsUrl", "https://mock.invalid/terms")
        }
        putJsonObject("runtime") {
            put("defaultLanguage", "en")
            put("supportedLanguages", buildJsonArray { add(JsonPrimitive("en")); add(JsonPrimitive("fr")) })
            put("defaultTheme", "SYSTEM")
        }
    }

    private fun tenant() = buildJsonObject { put("name", "Mock"); put("slug", "mock") }

    private fun FakeData.User.json() = buildJsonObject {
        put("id", id)
        put("email", email)
        put("firstName", firstName)
        put("lastName", lastName)
        put("status", "ACTIVE")
        put("createdAt", createdAt.toString())
    }

    private fun FakeData.User.settings() = buildJsonObject {
        put("theme", theme)
        put("language", language)
        put("notifications", JsonObject(notifications.mapValues { JsonPrimitive(it.value) }))
    }

    private fun FakeData.Item.json() = buildJsonObject {
        put("id", id)
        put("title", title)
        put("description", description)
        put("category", category)
        put("imageUrl", imageUrl)
        put("createdAt", createdAt.toString())
        put("updatedAt", createdAt.toString())
    }

    private fun Session.json(current: Boolean) = buildJsonObject {
        put("id", id)
        put("createdAt", createdAt.toString())
        put("lastUsedAt", lastUsedAt.toString())
        put("userAgent", userAgent)
        put("current", current)
    }

    private fun ok(data: JsonElement) = Reply(200, success(data))

    private fun success(data: JsonElement) = buildJsonObject {
        put("success", true)
        put("data", data)
        putJsonObject("meta") { put("requestId", Uuid.random().toString()) }
    }

    private fun error(status: Int, code: String, details: List<Pair<String, String>> = emptyList()) = Reply(status, buildJsonObject {
        put("success", false)
        putJsonObject("error") {
            put("code", code)
            put("message", if (code == "VALIDATION_ERROR") "Request validation failed" else code)
            if (details.isNotEmpty()) {
                put("details", JsonArray(details.map { (path, message) -> buildJsonObject { put("path", path); put("message", message) } }))
            }
        }
        putJsonObject("meta") { put("requestId", Uuid.random().toString()) }
    })

    private fun validation(vararg details: Pair<String, String>) = error(400, "VALIDATION_ERROR", details.toList())

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull

    private companion object {
        const val API = "/api/v1"
        const val MIN_PASSWORD = 10
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
        val THEMES = setOf("SYSTEM", "LIGHT", "DARK")
        val SORTS = setOf("createdAt:asc", "createdAt:desc", "title:asc", "title:desc")
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
