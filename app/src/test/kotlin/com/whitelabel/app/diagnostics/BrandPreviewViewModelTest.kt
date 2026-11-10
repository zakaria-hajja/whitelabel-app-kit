package com.whitelabel.app.diagnostics

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.branding.RuntimeDefaults
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Suspends until the test completes [response], so in-between states are observable. */
private class FakeHealthDataSource : HealthDataSource {
    val response = CompletableDeferred<Result<HealthStatus, DataError>>()
    override suspend fun check(): Result<HealthStatus, DataError> = response.await()
}

@OptIn(ExperimentalCoroutinesApi::class)
class BrandPreviewViewModelTest {

    private val brand = BrandConfig(
        tenant = "tenant",
        appName = "Tenant",
        applicationId = "com.example.tenant",
        versionName = "1.0.0",
        environment = Environment.DEVELOPMENT,
        apiBaseUrl = "https://api.tenant.test/api/v1/",
        deepLinkScheme = "tenant",
        runtimeDefaults = RuntimeDefaults(
            supportEmail = null,
            privacyPolicyUrl = "https://tenant.test/privacy",
            termsUrl = "https://tenant.test/terms",
            enabledFeatures = emptySet(),
        ),
    )
    private lateinit var health: FakeHealthDataSource
    private lateinit var viewModel: BrandPreviewViewModel

    @BeforeEach fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        health = FakeHealthDataSource()
        viewModel = BrandPreviewViewModel(brand, health)
    }

    @AfterEach fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun `initial state carries the brand and is idle`() {
        assertThat(viewModel.state.value).isEqualTo(BrandPreviewState(brand = brand))
    }

    @Test fun `ping goes Checking then Up`() = runTest {
        viewModel.state.test {
            assertThat(awaitItem().health).isEqualTo(HealthUi.Idle)
            viewModel.onAction(BrandPreviewAction.OnPingClick)
            assertThat(awaitItem().health).isEqualTo(HealthUi.Checking)
            health.response.complete(Result.Success(HealthStatus(status = "ok", databaseUp = true)))
            assertThat(awaitItem().health).isEqualTo(HealthUi.Up(databaseUp = true))
        }
    }

    @Test fun `ping failure goes Failed with the error`() = runTest {
        viewModel.state.test {
            skipItems(1)
            viewModel.onAction(BrandPreviewAction.OnPingClick)
            assertThat(awaitItem().health).isEqualTo(HealthUi.Checking)
            health.response.complete(Result.Error(DataError.Network.NO_INTERNET))
            assertThat(awaitItem().health).isEqualTo(HealthUi.Failed(DataError.Network.NO_INTERNET))
        }
    }

    @Test fun `theme selection updates state`() = runTest {
        viewModel.state.test {
            assertThat(awaitItem().darkTheme).isNull()
            viewModel.onAction(BrandPreviewAction.OnThemeSelect(dark = true))
            assertThat(awaitItem().darkTheme).isEqualTo(true)
            viewModel.onAction(BrandPreviewAction.OnThemeSelect(dark = false))
            assertThat(awaitItem().darkTheme).isEqualTo(false)
            viewModel.onAction(BrandPreviewAction.OnThemeSelect(dark = null))
            assertThat(awaitItem().darkTheme).isNull()
        }
    }
}
