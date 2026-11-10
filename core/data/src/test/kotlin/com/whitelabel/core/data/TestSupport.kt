package com.whitelabel.core.data

import com.whitelabel.core.datastore.Sealed
import com.whitelabel.core.datastore.TokenCipher
import com.whitelabel.core.network.HttpClientFactory
import com.whitelabel.core.network.NetworkConfig
import com.whitelabel.core.network.SessionTokenStore
import com.whitelabel.core.network.SessionTokens
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf

/** Identity "encryption": the store's own tests cover the cipher. */
object PlainCipher : TokenCipher {
    override fun encrypt(plain: ByteArray) = Sealed(byteArrayOf(0), plain)
    override fun decrypt(sealed: Sealed) = sealed.ciphertext
}

object NoTokens : SessionTokenStore {
    override suspend fun current(): SessionTokens? = null
    override suspend fun replace(expected: SessionTokens, new: SessionTokens) = false
    override suspend fun endSession(expected: SessionTokens) = Unit
}

fun mockClient(
    tokens: SessionTokenStore = NoTokens,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient =
    HttpClientFactory.create(
        MockEngine(handler),
        NetworkConfig(baseUrl = "https://api.test/api/v1/", tenant = null, userAgent = "test"),
        tokens,
    )

private val json = headersOf(HttpHeaders.ContentType, "application/json")

fun MockRequestHandleScope.ok(data: String): HttpResponseData =
    respond("""{"success":true,"data":$data,"meta":{"requestId":"r"}}""", HttpStatusCode.OK, json)

fun MockRequestHandleScope.fail(code: String, status: HttpStatusCode): HttpResponseData =
    respond("""{"success":false,"error":{"code":"$code","message":"m"},"meta":{"requestId":"r"}}""", status, json)

val HttpRequestData.bodyText: String get() = (body as TextContent).text

const val USER_JSON =
    """{"id":"u1","email":"ada@example.com","firstName":"Ada","lastName":null,"status":"ACTIVE","createdAt":"2026-01-01T00:00:00.000Z"}"""
const val TOKENS_JSON = """{"accessToken":"a1","refreshToken":"r1","tokenType":"Bearer","expiresIn":900}"""
