package com.whitelabel.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.AnalyticsEvents
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.common.onFailure
import com.whitelabel.core.common.onSuccess
import com.whitelabel.core.domain.AppConfigRepository
import com.whitelabel.core.domain.LanguageRepository
import com.whitelabel.core.domain.SessionRepository
import com.whitelabel.core.domain.SessionState
import com.whitelabel.core.domain.ThemePreference
import com.whitelabel.core.domain.UserPreferencesRepository
import com.whitelabel.core.presentation.UiText
import com.whitelabel.core.presentation.toUiText
import com.whitelabel.feature.settings.domain.NotificationKind
import com.whitelabel.feature.settings.domain.NotificationSettings
import com.whitelabel.feature.settings.domain.SettingsDataSource
import com.whitelabel.feature.settings.domain.SettingsPatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SettingsState(
    /** The theme in effect: the user's choice, else the app config's default. */
    val theme: ThemePreference = ThemePreference.SYSTEM,
    /** The app's own language, or `null` to follow the device. */
    val language: String? = null,
    /** Languages the backend supports; empty means no restriction. */
    val remoteLanguages: List<String> = emptyList(),
    val signedIn: Boolean = false,
    val notifications: NotificationSettings? = null,
    val notificationsError: UiText? = null,
    /** A one-off message (a toggle that couldn't be saved). */
    val message: UiText? = null,
    val confirmSignOut: Boolean = false,
    val appName: String = "",
    val versionName: String = "",
    val supportEmail: String? = null,
    val privacyUrl: String = "",
    val termsUrl: String = "",
    val showDiagnostics: Boolean = false,
)

sealed interface SettingsAction {
    data class OnThemeSelect(val theme: ThemePreference) : SettingsAction
    data class OnLanguageSelect(val languageTag: String?) : SettingsAction
    data class OnNotificationToggle(val kind: NotificationKind, val enabled: Boolean) : SettingsAction
    data object OnRetryNotifications : SettingsAction
    data object OnMessageShown : SettingsAction
    data object OnSignOutClick : SettingsAction
    data object OnSignOutConfirm : SettingsAction
    data object OnSignOutDismiss : SettingsAction

    /** The screen is visible again: the language may have been changed in the system's settings. */
    data object OnScreenResumed : SettingsAction
}

/** The chosen language without its region (`fr-CA` → `fr`), as the app offers languages, not regions. */
private fun LanguageRepository.currentLanguage(): String? =
    primaryLanguage(current())

/** The app's languages that the backend also supports; all of them when the backend sets no list. */
fun offeredLanguages(options: List<String>, remote: List<String>): List<String> =
    if (remote.isEmpty()) options else options.filter { it in remote }

/**
 * Theme and language are device settings: applied at once, offline and signed out too, and sent to
 * the server only as a best effort. Notifications live on the server: a toggle shows at once, its
 * request runs in order with the others, and a failure puts back what the server last confirmed,
 * unless a newer toggle of the same kind is still on its way.
 */
class SettingsViewModel(
    private val session: SessionRepository,
    private val preferences: UserPreferencesRepository,
    private val languages: LanguageRepository,
    appConfig: AppConfigRepository,
    brand: BrandConfig,
    private val source: SettingsDataSource,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsState(
            language = languages.currentLanguage(),
            appName = brand.appName,
            versionName = brand.versionName,
            showDiagnostics = brand.environment == Environment.DEVELOPMENT,
        ),
    )
    val state = _state.asStateFlow()

    private val notificationWrites = Mutex()

    /** What the server last confirmed; the switches may be ahead of it while requests run. */
    private var confirmed: NotificationSettings? = null

    /** The newest toggle per kind, so a failure only reverts when no newer toggle of that kind follows. */
    private val latestToggle = mutableMapOf<NotificationKind, Int>()
    private var toggleCount = 0

    init {
        viewModelScope.launch {
            combine(preferences.theme, appConfig.config) { theme, config -> theme to config }.collect { (theme, config) ->
                _state.update {
                    it.copy(
                        theme = theme ?: config.defaultTheme,
                        remoteLanguages = config.supportedLanguages,
                        supportEmail = config.supportEmail,
                        privacyUrl = config.privacyPolicyUrl,
                        termsUrl = config.termsUrl,
                    )
                }
            }
        }
        viewModelScope.launch {
            session.state.collect { sessionState ->
                val signedIn = sessionState == SessionState.SignedIn
                _state.update { if (signedIn) it.copy(signedIn = true) else it.copy(signedIn = false, notifications = null) }
                if (signedIn && _state.value.notifications == null) loadNotifications()
            }
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.OnThemeSelect -> selectTheme(action.theme)
            is SettingsAction.OnLanguageSelect -> selectLanguage(action.languageTag)
            is SettingsAction.OnNotificationToggle -> toggle(action.kind, action.enabled)
            SettingsAction.OnRetryNotifications -> viewModelScope.launch { loadNotifications() }
            SettingsAction.OnMessageShown -> _state.update { it.copy(message = null) }
            SettingsAction.OnSignOutClick -> _state.update { it.copy(confirmSignOut = true) }
            SettingsAction.OnSignOutDismiss -> _state.update { it.copy(confirmSignOut = false) }
            SettingsAction.OnSignOutConfirm -> signOut()
            SettingsAction.OnScreenResumed -> _state.update { it.copy(language = languages.currentLanguage()) }
        }
    }

    private fun selectTheme(theme: ThemePreference) {
        _state.update { it.copy(theme = theme) }
        viewModelScope.launch {
            preferences.setTheme(theme)
            if (_state.value.signedIn) source.patch(SettingsPatch(theme = theme.name))
        }
    }

    private fun selectLanguage(languageTag: String?) {
        languages.set(languageTag)
        _state.update { it.copy(language = languageTag) }
        // The server has no "follow the device" value, so only a chosen language is sent.
        if (languageTag != null && _state.value.signedIn) {
            viewModelScope.launch { source.patch(SettingsPatch(language = languageTag)) }
        }
    }

    private suspend fun loadNotifications() {
        _state.update { it.copy(notificationsError = null) }
        source.get()
            .onSuccess { settings ->
                confirmed = settings
                _state.update { it.copy(notifications = settings) }
            }
            .onFailure { error -> _state.update { it.copy(notificationsError = error.toUiText()) } }
    }

    private fun toggle(kind: NotificationKind, enabled: Boolean) {
        if (_state.value.notifications == null) return
        val toggle = ++toggleCount
        latestToggle[kind] = toggle
        _state.update { it.copy(notifications = it.notifications?.with(kind, enabled)) }
        viewModelScope.launch {
            notificationWrites.withLock {
                source.patch(kind.patch(enabled))
                    .onSuccess { confirmed = confirmed?.with(kind, enabled) }
                    .onFailure { error ->
                        // A newer toggle of this kind will set the server (and the switch) itself.
                        val serverValue = confirmed?.valueOf(kind)
                        _state.update {
                            val revert = latestToggle[kind] == toggle && serverValue != null
                            it.copy(
                                notifications = if (revert) it.notifications?.with(kind, checkNotNull(serverValue)) else it.notifications,
                                message = error.toUiText(),
                            )
                        }
                    }
            }
        }
    }

    private fun signOut() {
        _state.update { it.copy(confirmSignOut = false) }
        viewModelScope.launch {
            session.signOut()
            analytics.track(AnalyticsEvent(AnalyticsEvents.LOGOUT))
        }
    }
}

private fun NotificationSettings.with(kind: NotificationKind, enabled: Boolean) = when (kind) {
    NotificationKind.PUSH -> copy(push = enabled)
    NotificationKind.EMAIL -> copy(email = enabled)
    NotificationKind.MARKETING -> copy(marketing = enabled)
}

private fun NotificationSettings.valueOf(kind: NotificationKind) = when (kind) {
    NotificationKind.PUSH -> push
    NotificationKind.EMAIL -> email
    NotificationKind.MARKETING -> marketing
}

private fun NotificationKind.patch(enabled: Boolean) = when (this) {
    NotificationKind.PUSH -> SettingsPatch(push = enabled)
    NotificationKind.EMAIL -> SettingsPatch(email = enabled)
    NotificationKind.MARKETING -> SettingsPatch(marketing = enabled)
}
