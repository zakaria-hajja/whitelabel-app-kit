package com.whitelabel.core.shell

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import androidx.lifecycle.SavedStateHandle
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.branding.RuntimeDefaults
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.EmptyResult
import com.whitelabel.core.common.Result
import com.whitelabel.core.domain.AppConfig
import com.whitelabel.core.domain.AppConfigRepository
import com.whitelabel.core.domain.Registration
import com.whitelabel.core.domain.SessionRepository
import com.whitelabel.core.domain.SessionState
import com.whitelabel.core.domain.ThemePreference
import com.whitelabel.core.domain.User
import com.whitelabel.core.domain.UserPreferencesRepository
import com.whitelabel.core.domain.UserRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

private val brand = BrandConfig(
    tenant = null, appName = "Sample", applicationId = "com.example.sample", versionName = "1.0.0",
    environment = Environment.DEVELOPMENT, apiBaseUrl = "https://api.test/api/v1/", deepLinkScheme = "sample",
    runtimeDefaults = RuntimeDefaults(null, "https://p", "https://t", enabledFeatures = setOf("b")),
)

private fun config(features: Map<String, Boolean> = emptyMap(), theme: ThemePreference = ThemePreference.SYSTEM) =
    AppConfig(features, null, "https://p", "https://t", theme, emptyList())

/** [config] is what observers see; [stored] is what storage holds, which [config] may lag behind. */
private class FakeAppConfig(initial: AppConfig = config()) : AppConfigRepository {
    override val config = MutableStateFlow(initial)
    var stored: AppConfig = initial
    var onRefresh: suspend () -> AppConfig? = { null }
    override suspend fun current(): AppConfig = stored
    override suspend fun refresh(): EmptyResult<DataError> {
        onRefresh()?.let { stored = it; config.value = it }
        return Result.Success(Unit)
    }
}

private class FakePreferences : UserPreferencesRepository {
    override val theme = MutableStateFlow<ThemePreference?>(null)
    override suspend fun setTheme(theme: ThemePreference) { this.theme.value = theme }
}

private class FakeSession(initial: SessionState = SessionState.SignedOut) : SessionRepository {
    override val state = MutableStateFlow(initial)
    override suspend fun signIn(email: String, password: String): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun register(registration: Registration): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun signOut() { state.value = SessionState.SignedOut }
}

private class FakeUsers : UserRepository {
    var refreshes = 0
    override val user: Flow<User?> = MutableStateFlow(null)
    override suspend fun refresh(): EmptyResult<DataError> { refreshes++; return Result.Success(Unit) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {

    private val definition = appDefinition {
        add(entry("a", RouteA))
        add(entry("b", RouteB, flagKey = "b"))
    }
    private val appConfig = FakeAppConfig()
    private val preferences = FakePreferences()
    private val session = FakeSession()
    private val users = FakeUsers()
    private val savedState = SavedStateHandle()

    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        brand: BrandConfig = com.whitelabel.core.shell.brand,
        definition: AppDefinition = this@ShellViewModelTest.definition,
    ): ShellViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return ShellViewModel(definition, brand, appConfig, preferences, session, users, savedState)
    }

    private val ShellViewModel.current: ShellState get() = state.value

    @Test fun `not ready until the config refresh returns`() = runTest {
        val answer = CompletableDeferred<AppConfig>()
        appConfig.onRefresh = { answer.await() }
        val viewModel = viewModel()
        runCurrent()
        assertThat(viewModel.current.ready).isFalse()
        answer.complete(config(features = mapOf("b" to false)))
        runCurrent()
        assertThat(viewModel.current.ready).isTrue()
        assertThat(viewModel.current.visibleTabs.map { it.route }).containsExactly(RouteA)
    }

    @Test fun `slow config gives up after two seconds with the cached flags`() = runTest {
        appConfig.onRefresh = { awaitCancellation() }
        val viewModel = viewModel()
        advanceTimeBy(1_999)
        assertThat(viewModel.current.ready).isFalse()
        advanceTimeBy(2)
        assertThat(viewModel.current.ready).isTrue()
        // No remote value for "b": the app's defaults.features list it, so it is on.
        assertThat(viewModel.current.visibleTabs.map { it.route }).containsExactly(RouteA, RouteB)
    }

    @Test fun `tabs use the stored config even before observers catch up`() = runTest {
        appConfig.onRefresh = { null }
        appConfig.stored = config(features = mapOf("b" to false)) // cached from an earlier launch, not yet emitted
        val viewModel = viewModel()
        runCurrent()
        assertThat(viewModel.current.visibleTabs.map { it.route }).containsExactly(RouteA)
    }

    @Test fun `tabs stay fixed when the config changes later`() = runTest {
        val viewModel = viewModel()
        runCurrent()
        appConfig.config.value = config(features = mapOf("b" to false))
        runCurrent()
        assertThat(viewModel.current.visibleTabs.map { it.route }).containsExactly(RouteA, RouteB)
    }

    @Test fun `theme falls back to the config default`() = runTest {
        appConfig.config.value = config(theme = ThemePreference.DARK)
        appConfig.stored = appConfig.config.value
        val viewModel = viewModel()
        runCurrent()
        assertThat(viewModel.current.theme).isEqualTo(ThemePreference.DARK)
        preferences.theme.value = ThemePreference.LIGHT
        runCurrent()
        assertThat(viewModel.current.theme).isEqualTo(ThemePreference.LIGHT)
    }

    @Test fun `signed in start refreshes the user once`() = runTest {
        session.state.value = SessionState.SignedIn
        val viewModel = viewModel()
        runCurrent()
        session.state.value = SessionState.SignedOut
        runCurrent()
        session.state.value = SessionState.SignedIn
        runCurrent()
        assertThat(users.refreshes).isEqualTo(1)
        assertThat(viewModel.current.session).isEqualTo(SessionState.SignedIn)
    }


    @Test fun `held deep link survives process death until consumed`() = runTest {
        viewModel().onDeepLink("sample://catalog/1")
        val restored = ShellViewModel(definition, brand, appConfig, preferences, session, users, savedState)
        assertThat(restored.pendingDeepLink.value).isEqualTo("sample://catalog/1")
        restored.onDeepLinkConsumed()
        assertThat(restored.pendingDeepLink.value).isEqualTo(null)
    }

    @Test fun `backendless app never refreshes the config and is ready at once`() = runTest {
        var refreshes = 0
        appConfig.onRefresh = { refreshes++; awaitCancellation() }
        val viewModel = viewModel(brand = brand.copy(apiBaseUrl = null))
        runCurrent()
        assertThat(viewModel.current.ready).isTrue()
        assertThat(refreshes).isEqualTo(0)
    }

    @Test fun `backendless app with a gated feature fails at startup`() = runTest {
        val gated = appDefinition {
            add(entry("auth", gate = Gate(RouteC)))
            add(entry("a", RouteA))
        }
        assertFailure { viewModel(brand = brand.copy(apiBaseUrl = null), definition = gated) }
            .isInstanceOf(IllegalStateException::class)
            .messageContains("auth call the backend")
    }
}
