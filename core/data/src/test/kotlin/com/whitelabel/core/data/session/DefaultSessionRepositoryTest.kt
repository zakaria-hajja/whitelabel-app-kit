package com.whitelabel.core.data.session

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.whitelabel.core.common.ApiErrorCode
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.Result
import com.whitelabel.core.data.PlainCipher
import com.whitelabel.core.data.TOKENS_JSON
import com.whitelabel.core.data.USER_JSON
import com.whitelabel.core.data.bodyText
import com.whitelabel.core.data.fail
import com.whitelabel.core.data.mockClient
import com.whitelabel.core.data.ok
import com.whitelabel.core.data.user.UserCache
import com.whitelabel.core.data.user.UserDto
import com.whitelabel.core.network.getResult
import com.whitelabel.core.datastore.StoredTokens
import com.whitelabel.core.datastore.TokenStore
import com.whitelabel.core.domain.Registration
import com.whitelabel.core.domain.SessionState
import com.whitelabel.core.domain.User
import com.whitelabel.core.network.SessionTokens
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DefaultSessionRepositoryTest {

    @TempDir
    lateinit var dir: File

    private val storeJob = Job()
    private val storeScope = CoroutineScope(Dispatchers.IO + storeJob)
    private val tokenStore by lazy { TokenStore(File(dir, "session.json"), PlainCipher, storeScope) }
    private val userCache by lazy { UserCache(File(dir, "user.json"), storeScope) }
    private val requests = mutableListOf<HttpRequestData>()

    @AfterEach fun tearDown() = storeJob.cancel()

    private fun TestScope.repository(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = DefaultSessionRepository(
        client = mockClient { requests += it; handler(it) },
        session = LocalSession(tokenStore, userCache),
        scope = backgroundScope,
    )

    @Test fun `state is unknown then signed out with no stored tokens`() = runTest {
        val repository = repository { error("no call expected") }
        assertThat(repository.state.value).isEqualTo(SessionState.Unknown)
        assertThat(repository.state.first { it != SessionState.Unknown }).isEqualTo(SessionState.SignedOut)
    }

    @Test fun `sign in stores user and tokens and flips to signed in`() = runTest {
        val repository = repository { ok("""{"user":$USER_JSON,"tokens":$TOKENS_JSON}""") }
        assertThat(repository.signIn(" ada@example.com ", "pass word")).isEqualTo(Result.Success(Unit))
        assertThat(requests.single().url.encodedPath).isEqualTo("/api/v1/auth/login")
        assertThat(requests.single().bodyText).isEqualTo("""{"email":"ada@example.com","password":"pass word"}""")
        assertThat(repository.state.first { it != SessionState.Unknown }).isEqualTo(SessionState.SignedIn)
        assertThat(tokenStore.tokens.first()).isEqualTo(StoredTokens("a1", "r1"))
        assertThat(userCache.user.first()).isEqualTo(User("u1", "ada@example.com", "Ada", null))
    }

    @Test fun `wrong password returns invalid credentials and stays signed out`() = runTest {
        val repository = repository { fail("INVALID_CREDENTIALS", HttpStatusCode.Unauthorized) }
        assertThat(repository.signIn("ada@example.com", "nope"))
            .isEqualTo(Result.Error(DataError.Api(ApiErrorCode.INVALID_CREDENTIALS, 401)))
        assertThat(repository.state.first { it != SessionState.Unknown }).isEqualTo(SessionState.SignedOut)
    }

    @Test fun `register omits blank names from the body`() = runTest {
        val repository = repository { ok("""{"user":$USER_JSON,"tokens":$TOKENS_JSON}""") }
        repository.register(Registration("ada@example.com", "0123456789", firstName = "  Ada ", lastName = "   "))
        val body = requests.single().bodyText
        assertThat(requests.single().url.encodedPath).isEqualTo("/api/v1/auth/register")
        assertThat(body).contains(""""firstName":"Ada"""")
        assertThat(body).doesNotContain("lastName")
        assertThat(repository.state.first { it != SessionState.Unknown }).isEqualTo(SessionState.SignedIn)
    }

    @Test fun `sign out clears local data even when logout fails`() = runTest {
        tokenStore.write(StoredTokens("a1", "r1"))
        val repository = repository { throw IOException("offline") }
        repository.signOut()
        assertThat(repository.state.first { it != SessionState.Unknown }).isEqualTo(SessionState.SignedOut)
        assertThat(userCache.user.first()).isNull()
        testScheduler.advanceUntilIdle()
        assertThat(requests.single().bodyText).isEqualTo("""{"refreshToken":"r1"}""")
    }

    @Test fun `ended session clears the cached user`() = runTest {
        tokenStore.write(StoredTokens("a1", "r1"))
        val local = LocalSession(tokenStore, userCache)
        StoreSessionTokens(local).run {
            assertThat(current()).isEqualTo(SessionTokens("a1", "r1"))
            replace(SessionTokens("a1", "r1"), SessionTokens("a2", "r2"))
            assertThat(current()).isEqualTo(SessionTokens("a2", "r2"))
            endSession(SessionTokens("a2", "r2"))
            assertThat(current()).isNull()
        }
        assertThat(userCache.user.first()).isNull()
    }

    @Test fun `rejected refresh through the client signs out and clears the user`() = runTest {
        val local = LocalSession(tokenStore, userCache)
        local.start(UserDto("u1", "ada@example.com", "Ada", null), StoredTokens("a1", "r1"))
        val client = mockClient(StoreSessionTokens(local)) { request ->
            if (request.url.encodedPath.endsWith("auth/refresh")) {
                fail("INVALID_REFRESH_TOKEN", HttpStatusCode.Unauthorized)
            } else {
                fail("TOKEN_EXPIRED", HttpStatusCode.Unauthorized)
            }
        }
        client.getResult<UserDto>("users/me")
        assertThat(tokenStore.tokens.first()).isNull()
        assertThat(userCache.user.first()).isNull()
    }

    @Test fun `refresh finishing after sign out keeps the user signed out`() = runTest {
        val local = LocalSession(tokenStore, userCache)
        local.start(UserDto("u1", "ada@example.com", "Ada", null), StoredTokens("a1", "r1"))
        val client = mockClient(StoreSessionTokens(local)) { request ->
            if (request.url.encodedPath.endsWith("auth/refresh")) {
                local.clear() // the user signs out while the refresh is in flight
                ok(TOKENS_JSON.replace("a1", "a2").replace("r1", "r2"))
            } else if (request.headers[HttpHeaders.Authorization] == "Bearer a2") {
                ok(USER_JSON)
            } else {
                fail("TOKEN_EXPIRED", HttpStatusCode.Unauthorized)
            }
        }
        client.getResult<UserDto>("users/me")
        assertThat(tokenStore.tokens.first()).isNull()
    }

    @Test fun `stale rejection does not end a newer session`() = runTest {
        val local = LocalSession(tokenStore, userCache)
        local.start(UserDto("u1", "ada@example.com", "Ada", null), StoredTokens("a1", "r1"))
        val tokens = StoreSessionTokens(local)
        tokens.endSession(SessionTokens("old", "old-refresh"))
        assertThat(tokenStore.tokens.first()).isEqualTo(StoredTokens("a1", "r1"))
        assertThat(tokens.replace(SessionTokens("old", "old-refresh"), SessionTokens("x", "y"))).isEqualTo(false)
        assertThat(tokenStore.tokens.first()).isEqualTo(StoredTokens("a1", "r1"))
    }
}
