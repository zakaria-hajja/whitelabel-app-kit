package com.whitelabel.core.ios.mock

import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.branding.RuntimeDefaults
import com.whitelabel.core.datastore.Sealed
import com.whitelabel.core.datastore.StorePaths
import com.whitelabel.core.datastore.TokenCipher
import com.whitelabel.core.domain.LanguageRepository
import com.whitelabel.core.domain.SessionRepository
import com.whitelabel.core.domain.SessionState
import com.whitelabel.core.ios.IosPlatform
import com.whitelabel.core.ios.SharedGraph
import com.whitelabel.core.mockapi.FakeData
import com.whitelabel.core.shell.model.DefinitionSpec
import com.whitelabel.core.shell.model.FeatureSpec
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.mp.KoinPlatform
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

/** In Debug builds the app signs in against the in-memory fake, as Android's E2E tests do. */
class IosMockTest {

    private val dir = (NSTemporaryDirectory() + "ios-mock-" + NSUUID().UUIDString).toPath().also { FileSystem.SYSTEM.createDirectories(it) }

    @AfterTest fun tearDown() {
        SharedGraph.stop()
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    @Test fun `with the mock enabled the app signs in against the fake`() = runTest {
        IosMock.enable()
        val platform = IosPlatform(
            tokenCipher = object : TokenCipher {
                override fun encrypt(plain: ByteArray) = Sealed(ByteArray(12), plain)
                override fun decrypt(sealed: Sealed) = sealed.ciphertext
            },
            language = object : LanguageRepository { override fun current(): String? = null; override fun set(languageTag: String?) = Unit },
            storePaths = StorePaths { name -> dir / "$name.json" },
            brand = BrandConfig(null, "Showcase", "com.whitelabel.showcase", "1.0", Environment.DEVELOPMENT, "https://api.showcase.example/api/v1/", "showcase",
                RuntimeDefaults(null, "", "", emptySet())),
            logger = {},
        )
        val definition = DefinitionSpec(listOf(FeatureSpec("auth", null, null, false, true, false), FeatureSpec("settings", null, "settings", false, false, false)), null)
        SharedGraph.start(platform, definition, emptyList(), useMockApi = true)
        val session = KoinPlatform.getKoin().get<SessionRepository>()
        val result = session.signIn("alice@shared.test", FakeData.PASSWORD)
        assertEquals(true, result is com.whitelabel.core.common.Result.Success, "sign-in failed: $result")
        // Real time (not the test's virtual clock). The state follows the token store, so it may still show the SignedOut it read before the
        // sign-in wrote the tokens: wait for SignedIn rather than taking the first known state.
        assertEquals(SessionState.SignedIn, withContext(Dispatchers.Default) { withTimeout(5_000) { session.state.first { it == SessionState.SignedIn } } })
        assertEquals(true, IosMock.fakeBackend().requests.any { it.endsWith("/auth/login") })
    }
}
