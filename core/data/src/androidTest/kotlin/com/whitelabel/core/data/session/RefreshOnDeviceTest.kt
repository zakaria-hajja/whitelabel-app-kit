package com.whitelabel.core.data.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.data.user.UserCache
import com.whitelabel.core.datastore.KeystoreTokenCipher
import com.whitelabel.core.datastore.StoredTokens
import com.whitelabel.core.datastore.TokenStore
import com.whitelabel.core.network.HttpClientFactory
import com.whitelabel.core.network.NetworkConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole refresh chain with the real Keystore cipher: an expired access token is refreshed and
 * the new pair stored. The refresh response has the contract's shape (`data.tokens`).
 */
@RunWith(AndroidJUnit4::class)
class RefreshOnDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "refresh-test").apply { deleteRecursively(); mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
    }

    @Test fun expiredToken_refreshesAndStoresTheNewPair() = runBlocking {
        val tokenStore = TokenStore(File(dir, "session.json"), KeystoreTokenCipher("refresh_test_key"), scope)
        val session = LocalSession(tokenStore, UserCache(File(dir, "user.json"), scope))
        tokenStore.write(StoredTokens("old-access", "old-refresh"))
        val json = headersOf(HttpHeaders.ContentType, "application/json")
        val client = HttpClientFactory.create(
            MockEngine { request ->
                when {
                    request.url.encodedPath.endsWith("auth/refresh") -> respond(
                        """{"success":true,"data":{"tokens":{"accessToken":"new-access","refreshToken":"new-refresh","tokenType":"Bearer","expiresIn":900}},"meta":{"requestId":"r"}}""",
                        HttpStatusCode.OK, json,
                    )
                    request.headers[HttpHeaders.Authorization] == "Bearer new-access" -> respond("""{"success":true,"data":{},"meta":{"requestId":"r"}}""", HttpStatusCode.OK, json)
                    else -> respond("""{"success":false,"error":{"code":"TOKEN_EXPIRED","message":"m"},"meta":{"requestId":"r"}}""", HttpStatusCode.Unauthorized, json)
                }
            },
            NetworkConfig("https://api.test/api/v1/", tenant = null, userAgent = "test"),
            StoreSessionTokens(session),
        )
        client.get("users/me")
        assertThat(tokenStore.tokens.first()).isEqualTo(StoredTokens("new-access", "new-refresh"))
    }
}
