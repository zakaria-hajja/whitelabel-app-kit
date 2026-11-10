package com.whitelabel.core.shell

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.AnalyticsEvents
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.domain.SessionState
import com.whitelabel.core.domain.ThemePreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The launcher activity of every app: splash until the shell is ready, the theme the user chose
 * (or the config's default), and the app's definition in [ShellNavHost].
 */
@AndroidEntryPoint
class FoundationActivity : ComponentActivity() {

    @Inject lateinit var definition: AppDefinition
    @Inject lateinit var brand: BrandConfig
    @Inject lateinit var analytics: Analytics

    private val viewModel: ShellViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val hasGate = definition.gateEntry != null
        splash.setKeepOnScreenCondition {
            val state = viewModel.state.value
            !state.ready || (hasGate && state.session == SessionState.Unknown)
        }
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val darkTheme = when (state.theme) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }
            AppTheme(darkTheme = darkTheme) {
                if (state.ready) {
                    ShellNavHost(
                        definition = definition,
                        visibleTabs = state.visibleTabs,
                        deepLinkScheme = brand.deepLinkScheme,
                        onScreenView = { screen ->
                            analytics.track(AnalyticsEvent(AnalyticsEvents.SCREEN_VIEW, mapOf("screen" to screen)))
                        },
                    )
                }
            }
        }
    }
}
