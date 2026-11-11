package com.whitelabel.feature.settings.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.common.Result
import com.whitelabel.core.network.HttpClientFactory
import com.whitelabel.core.network.NetworkConfig
import com.whitelabel.core.network.SessionTokenStore
import com.whitelabel.core.network.SessionTokens
import com.whitelabel.feature.settings.domain.NotificationSettings
import com.whitelabel.feature.settings.domain.SettingsPatch
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

private object SignedIn : SessionTokenStore {
    override suspend fun current() = SessionTokens("access", "refresh")
    override suspend fun replace(expected: SessionTokens, new: SessionTokens) = false
    override suspend fun endSession(expected: SessionTokens) = Unit
}

private const val SETTINGS =
    """{"success":true,"data":{"theme":"DARK","language":"fr","notifications":{"push":true,"email":false,"marketing":false}},"meta":{"requestId":"r"}}"""

class KtorSettingsDataSourceTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun source() = KtorSettingsDataSource(
        HttpClientFactory.create(
            MockEngine { requests += it; respond(SETTINGS, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) },
            NetworkConfig("https://api.test/api/v1/", tenant = null, userAgent = "test"),
            SignedIn,
        ),
    )

    @Test fun `get reads the notification settings`() = runTest {
        assertThat(source().get()).isEqualTo(Result.Success(NotificationSettings(push = true, email = false, marketing = false)))
        assertThat(requests.single().url.encodedPath).isEqualTo("/api/v1/settings")
    }

    @Test fun `patch sends only the fields that changed`() = runTest {
        source().patch(SettingsPatch(push = false))
        source().patch(SettingsPatch(theme = "DARK", language = "fr"))
        assertThat(requests.map { it.method }).isEqualTo(listOf(HttpMethod.Patch, HttpMethod.Patch))
        assertThat((requests[0].body as TextContent).text).isEqualTo("""{"notifications":{"push":false}}""")
        assertThat((requests[1].body as TextContent).text).isEqualTo("""{"theme":"DARK","language":"fr"}""")
    }
}
