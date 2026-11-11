package com.whitelabel.feature.settings.presentation

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.AnalyticsEvents
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.branding.RuntimeDefaults
import com.whitelabel.core.common.DataError
import com.whitelabel.core.common.EmptyResult
import com.whitelabel.core.common.Result
import com.whitelabel.core.domain.AppConfig
import com.whitelabel.core.domain.AppConfigRepository
import com.whitelabel.core.domain.LanguageRepository
import com.whitelabel.core.domain.Registration
import com.whitelabel.core.domain.SessionRepository
import com.whitelabel.core.domain.SessionState
import com.whitelabel.core.domain.ThemePreference
import com.whitelabel.core.domain.UserPreferencesRepository
import com.whitelabel.feature.settings.domain.NotificationSettings
import com.whitelabel.feature.settings.domain.SettingsDataSource
import com.whitelabel.feature.settings.domain.SettingsPatch
import com.whitelabel.feature.settings.domain.NotificationKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

private val brand = BrandConfig(
    tenant = null, appName = "Sample", applicationId = "com.example.sample", versionName = "1.2.3",
    environment = Environment.DEVELOPMENT, apiBaseUrl = "https://api.test/api/v1/", deepLinkScheme = "sample",
    runtimeDefaults = RuntimeDefaults("help@sample.example", "https://p", "https://t", emptySet()),
)

private class FakeSession(initial: SessionState) : SessionRepository {
    override val state = MutableStateFlow(initial)
    var signOuts = 0
    override suspend fun signIn(email: String, password: String): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun register(registration: Registration): EmptyResult<DataError> = Result.Success(Unit)
    override suspend fun signOut() { signOuts++; state.value = SessionState.SignedOut }
}

private class FakePreferences : UserPreferencesRepository {
    override val theme = MutableStateFlow<ThemePreference?>(null)
    override suspend fun setTheme(theme: ThemePreference) { this.theme.value = theme }
}

private class FakeLanguage : LanguageRepository {
    var tag: String? = null
    override fun current() = tag
    override fun set(languageTag: String?) { tag = languageTag }
}

private class FakeConfig : AppConfigRepository {
    override val config = MutableStateFlow(
        AppConfig(emptyMap(), "help@remote.example", "https://remote/privacy", "https://remote/terms", ThemePreference.DARK, listOf("en")),
    )
    override suspend fun current() = config.value
    override suspend fun refresh(): EmptyResult<DataError> = Result.Success(Unit)
}

private class FakeSource : SettingsDataSource {
    val patches = mutableListOf<SettingsPatch>()
    var gets = 0
    var settings: Result<NotificationSettings, DataError> = Result.Success(NotificationSettings(push = true, email = false, marketing = false))
    var patchAnswer: suspend (SettingsPatch) -> EmptyResult<DataError> = { Result.Success(Unit) }
    override suspend fun get(): Result<NotificationSettings, DataError> { gets++; return settings }
    override suspend fun patch(patch: SettingsPatch): EmptyResult<DataError> { patches += patch; return patchAnswer(patch) }
}

private class FakeAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()
    override fun track(event: AnalyticsEvent) { events += event }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val preferences = FakePreferences()
    private val language = FakeLanguage()
    private val config = FakeConfig()
    private val source = FakeSource()
    private val analytics = FakeAnalytics()

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(session: FakeSession = FakeSession(SessionState.SignedIn)) =
        SettingsViewModel(session, preferences, language, config, brand, source, analytics)

    @Test fun `about shows the app identity and the remote links`() = runTest {
        val state = viewModel().state.value
        assertThat(state.appName).isEqualTo("Sample")
        assertThat(state.versionName).isEqualTo("1.2.3")
        assertThat(state.supportEmail).isEqualTo("help@remote.example")
        assertThat(state.privacyUrl).isEqualTo("https://remote/privacy")
        assertThat(state.showDiagnostics).isTrue()
    }

    @Test fun `theme change applies locally and patches when signed in`() = runTest {
        val viewModel = viewModel()
        assertThat(viewModel.state.value.theme).isEqualTo(ThemePreference.DARK) // the config's default
        viewModel.onAction(SettingsAction.OnThemeSelect(ThemePreference.LIGHT))
        assertThat(preferences.theme.value).isEqualTo(ThemePreference.LIGHT)
        assertThat(viewModel.state.value.theme).isEqualTo(ThemePreference.LIGHT)
        assertThat(source.patches).containsExactly(SettingsPatch(theme = "LIGHT"))
    }

    @Test fun `language change applies locally and patches when signed in`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(SettingsAction.OnLanguageSelect("fr"))
        assertThat(language.tag).isEqualTo("fr")
        assertThat(viewModel.state.value.language).isEqualTo("fr")
        viewModel.onAction(SettingsAction.OnLanguageSelect(null))
        assertThat(language.tag).isNull()
        assertThat(source.patches).containsExactly(SettingsPatch(language = "fr"))
    }

    @Test fun `a language changed outside the app shows when the screen is back`() = runTest {
        val viewModel = viewModel()
        viewModel.onAction(SettingsAction.OnLanguageSelect("fr"))
        language.tag = "en" // changed in the system's app-language settings
        viewModel.onAction(SettingsAction.OnScreenResumed)
        assertThat(viewModel.state.value.language).isEqualTo("en")
    }

    @Test fun `a regional language selects its language`() = runTest {
        language.tag = "fr-CA"
        assertThat(viewModel().state.value.language).isEqualTo("fr")
    }

    @Test fun `signed out hides notifications and sends no request`() = runTest {
        val viewModel = viewModel(FakeSession(SessionState.SignedOut))
        viewModel.onAction(SettingsAction.OnThemeSelect(ThemePreference.DARK))
        assertThat(viewModel.state.value.signedIn).isFalse()
        assertThat(viewModel.state.value.notifications).isNull()
        assertThat(source.gets).isEqualTo(0)
        assertThat(source.patches).isEmpty()
    }

    @Test fun `failed toggle reverts and shows the error`() = runTest {
        source.patchAnswer = { Result.Error(DataError.Network.NO_INTERNET) }
        val viewModel = viewModel()
        viewModel.onAction(SettingsAction.OnNotificationToggle(NotificationKind.EMAIL, true))
        assertThat(viewModel.state.value.notifications?.email).isEqualTo(false)
        assertThat(viewModel.state.value.message).isNotNull()
    }

    @Test fun `rapid toggles leave the server at the last value`() = runTest {
        val first = CompletableDeferred<Unit>()
        source.patchAnswer = { if (source.patches.size == 1) first.await(); Result.Success(Unit) }
        val viewModel = viewModel()
        viewModel.onAction(SettingsAction.OnNotificationToggle(NotificationKind.PUSH, false))
        viewModel.onAction(SettingsAction.OnNotificationToggle(NotificationKind.PUSH, true))
        viewModel.onAction(SettingsAction.OnNotificationToggle(NotificationKind.PUSH, false))
        assertThat(source.patches.size).isEqualTo(1) // the others wait their turn
        first.complete(Unit)
        assertThat(source.patches).containsExactly(
            SettingsPatch(push = false),
            SettingsPatch(push = true),
            SettingsPatch(push = false),
        )
        assertThat(viewModel.state.value.notifications?.push).isEqualTo(false)
    }

    @Test fun `a failed toggle followed by newer ones ends where the server does`() = runTest {
        // push starts on. Off (fails), on (ok), off (ok): the server ends off, and so must the switch.
        val first = CompletableDeferred<Unit>()
        source.patchAnswer = { patch ->
            if (source.patches.size == 1) {
                first.await()
                Result.Error(DataError.Network.NO_INTERNET)
            } else {
                Result.Success(Unit)
            }
        }
        val viewModel = viewModel()
        viewModel.onAction(SettingsAction.OnNotificationToggle(NotificationKind.PUSH, false))
        viewModel.onAction(SettingsAction.OnNotificationToggle(NotificationKind.PUSH, true))
        viewModel.onAction(SettingsAction.OnNotificationToggle(NotificationKind.PUSH, false))
        first.complete(Unit)
        assertThat(source.patches.last()).isEqualTo(SettingsPatch(push = false))
        assertThat(viewModel.state.value.notifications?.push).isEqualTo(false)
    }

    @Test fun `the latest toggle failing restores what the server last confirmed`() = runTest {
        source.patchAnswer = { patch -> if (patch.push == false) Result.Error(DataError.Network.NO_INTERNET) else Result.Success(Unit) }
        val viewModel = viewModel()
        viewModel.onAction(SettingsAction.OnNotificationToggle(NotificationKind.PUSH, false)) // fails: server still on
        assertThat(viewModel.state.value.notifications?.push).isEqualTo(true)
        assertThat(viewModel.state.value.message).isNotNull()
    }

    @Test fun `sign out asks first then signs out`() = runTest {
        val session = FakeSession(SessionState.SignedIn)
        val viewModel = viewModel(session)
        viewModel.onAction(SettingsAction.OnSignOutClick)
        assertThat(viewModel.state.value.confirmSignOut).isTrue()
        assertThat(session.signOuts).isEqualTo(0)
        viewModel.onAction(SettingsAction.OnSignOutConfirm)
        assertThat(session.signOuts).isEqualTo(1)
        assertThat(viewModel.state.value.confirmSignOut).isFalse()
        assertThat(analytics.events).containsExactly(AnalyticsEvent(AnalyticsEvents.LOGOUT))
    }

    @Test fun `languages are the options filtered by the remote list`() {
        assertThat(offeredLanguages(listOf("en", "fr"), remote = listOf("en"))).containsExactly("en")
        assertThat(offeredLanguages(listOf("en", "fr"), remote = emptyList())).containsExactly("en", "fr")
        assertThat(offeredLanguages(listOf("en", "fr"), remote = listOf("de"))).isEmpty()
    }
}
