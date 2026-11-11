package com.whitelabel.core.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.branding.RuntimeDefaults
import com.whitelabel.core.common.Result
import com.whitelabel.core.data.config.AppConfigCache
import com.whitelabel.core.data.config.DefaultAppConfigRepository
import com.whitelabel.core.data.session.DefaultSessionRepository
import com.whitelabel.core.data.session.LocalSession
import com.whitelabel.core.data.user.DefaultUserRepository
import com.whitelabel.core.data.user.UserCache
import com.whitelabel.core.datastore.StoredTokens
import com.whitelabel.core.datastore.TokenStore
import com.whitelabel.core.domain.Registration
import com.whitelabel.core.testing.ContractBackend
import com.whitelabel.core.testing.OpenApiSpec
import com.whitelabel.core.testing.exampleValue
import java.io.File
import okio.Path.Companion.toOkioPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ContractTest {

    @TempDir
    lateinit var dir: File

    private val job = Job()
    private val storeScope = CoroutineScope(Dispatchers.IO + job)
    private val backend = ContractBackend()
    private val tokenStore by lazy { TokenStore(File(dir, "session.json").toOkioPath(), PlainCipher, storeScope) }
    private val userCache by lazy { UserCache(File(dir, "user.json").toOkioPath(), storeScope) }

    @AfterEach fun tearDown() = job.cancel()

    private fun example(path: String, method: String, status: Int, pointer: String) = OpenApiSpec.exampleValue(path, method, status, pointer)

    @Test fun `post api v1 auth login matches the contract`() = runTest {
        val sessions = DefaultSessionRepository(backend.client(), LocalSession(tokenStore, userCache), backgroundScope)
        assertThat(sessions.signIn("ada@example.com", "secret pass")).isEqualTo(Result.Success(Unit))
        assertThat(userCache.user.first()?.email).isEqualTo(example("/api/v1/auth/login", "post", 200, "data.user.email"))
        assertThat(tokenStore.tokens.first()?.refreshToken).isEqualTo(example("/api/v1/auth/login", "post", 200, "data.tokens.refreshToken"))
        backend.assertRequestMatches("post", "/api/v1/auth/login")
    }

    @Test fun `post api v1 auth register matches the contract`() = runTest {
        val sessions = DefaultSessionRepository(backend.client(), LocalSession(tokenStore, userCache), backgroundScope)
        assertThat(sessions.register(Registration("ada@example.com", "0123456789", "Ada", null))).isEqualTo(Result.Success(Unit))
        assertThat(userCache.user.first()?.id).isEqualTo(example("/api/v1/auth/register", "post", 201, "data.user.id"))
        backend.assertRequestMatches("post", "/api/v1/auth/register")
    }

    @Test fun `post api v1 auth logout matches the contract`() = runTest {
        tokenStore.write(StoredTokens("a", "r"))
        val sessions = DefaultSessionRepository(backend.client(), LocalSession(tokenStore, userCache), backgroundScope)
        sessions.signOut()
        testScheduler.advanceUntilIdle()
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            while (backend.requests.none { it.specPath == "/api/v1/auth/logout" }) kotlinx.coroutines.delay(10)
        }
        backend.assertRequestMatches("post", "/api/v1/auth/logout")
    }

    @Test fun `get and patch api v1 users me match the contract`() = runTest {
        val users = DefaultUserRepository(backend.client(), userCache)
        assertThat(users.refresh()).isEqualTo(Result.Success(Unit))
        assertThat(userCache.user.first()?.email).isEqualTo(example("/api/v1/users/me", "get", 200, "data.email"))
        assertThat(users.update(firstName = null, lastName = "Lovelace")).isEqualTo(Result.Success(Unit))
        backend.assertRequestMatches("patch", "/api/v1/users/me")
    }

    @Test fun `get api v1 app config matches the contract`() = runTest {
        val brand = BrandConfig(null, "Sample", "com.example", "1.0", Environment.DEVELOPMENT, "https://api.test/api/v1/", "sample",
            RuntimeDefaults(null, "https://p", "https://t", emptySet()))
        val repository = DefaultAppConfigRepository(backend.client(), brand, AppConfigCache(File(dir, "config.json").toOkioPath(), storeScope), backgroundScope)
        assertThat(repository.refresh()).isEqualTo(Result.Success(Unit))
        assertThat(repository.current().termsUrl).isEqualTo(example("/api/v1/app/config", "get", 200, "data.legal.termsUrl"))
    }

    @Test fun `the smallest valid responses still decode`() = runTest {
        val minimal = ContractBackend(minimal = true)
        val sessions = DefaultSessionRepository(minimal.client(), LocalSession(tokenStore, userCache), backgroundScope)
        assertThat(sessions.signIn("ada@example.com", "secret pass")).isEqualTo(Result.Success(Unit))
        assertThat(userCache.user.first()?.firstName).isEqualTo(null)
        assertThat(DefaultUserRepository(minimal.client(), userCache).refresh()).isEqualTo(Result.Success(Unit))
        val brand = BrandConfig(null, "Sample", "com.example", "1.0", Environment.DEVELOPMENT, "https://api.test/api/v1/", "sample",
            RuntimeDefaults("help@sample.example", "https://p", "https://t", emptySet()))
        val config = DefaultAppConfigRepository(minimal.client(), brand, AppConfigCache(File(dir, "config-min.json").toOkioPath(), storeScope), backgroundScope)
        assertThat(config.refresh()).isEqualTo(Result.Success(Unit))
        assertThat(config.current().supportEmail).isEqualTo("help@sample.example") // the contract makes support.email optional
    }
}
