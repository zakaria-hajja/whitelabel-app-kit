package com.whitelabel.core.shell

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.whitelabel.core.shell.model.ContentGate
import com.whitelabel.core.shell.model.ShellScreen
import com.whitelabel.core.shell.model.ShellState
import com.whitelabel.core.shell.model.shellScreen
import kotlinx.coroutines.launch
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.shell.model.ShellViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import com.whitelabel.core.analytics.AnalyticsEvent
import com.whitelabel.core.analytics.AnalyticsEvents
import com.whitelabel.core.branding.BrandConfig
import com.whitelabel.core.consent.Consent
import com.whitelabel.core.branding.Environment
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.domain.SessionState
import com.whitelabel.core.domain.ThemePreference

/**
 * The launcher activity of every app: splash until the shell is ready, the theme the user chose
 * (or the config's default), and the app's definition in [ShellNavHost]. An `AppCompatActivity`
 * so the per-app language also applies below Android 13.
 */
class FoundationActivity : AppCompatActivity() {

    private val definition: AppDefinition by inject()
    private val brand: BrandConfig by inject()
    private val analytics: Analytics by inject()
    private val consent: Consent by inject()

    private val viewModel: ShellViewModel by viewModel()
    private var consentRequested = false

    /**
     * The app's lock, when it binds one ([AppDefinitionBuilder.contentGate]); null runs the shell as it
     * always did. It is the instance [FoundationApplication] resolved and the process lifecycle drives,
     * never a second Koin lookup. A lock screen without that instance fails here instead of opening.
     */
    private val contentGate: ContentGate? by lazy {
        (application as FoundationApplication).contentGate.also { checkContentGate(definition, it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // A recreated activity (rotation, process death) already handed its link over.
        takeDeepLink(intent, deliver = savedInstanceState == null)
        val hasGate = definition.gateEntry != null
        val gate = contentGate
        splash.setKeepOnScreenCondition {
            if (gate == null) {
                val keep = !viewModel.state.value.showsContent(hasGate)
                // UMP must run on the main thread, after the first screen shows (not behind the splash).
                // This condition is read on the main thread before each draw; the first time it lets the
                // splash go, the post runs after that frame, when this activity is resumed. Consent
                // gathers once per process, so a recreated activity asking again does nothing.
                if (shouldGatherConsent(definition.needsConsent, keep, consentRequested)) {
                    consentRequested = true
                    window.decorView.post { consent.gatherOnLaunch() }
                }
                keep
            } else {
                // The splash also waits for the lock to decide. The lock screen lets it go, but launch
                // consent waits for the content (lock first, then consent).
                val splashState = gatedSplash(shellScreen(viewModel.state.value.showsContent(hasGate), gate.state.value))
                if (shouldGatherConsent(definition.needsConsent, splashState.splashKept, consentRequested)) requestConsent()
                splashState.keep
            }
        }
        if (gate != null) {
            // Collected for the activity's whole life (Main.immediate: the first value applies now), so
            // the flag is set before onPause takes the recents snapshot.
            lifecycleScope.launch {
                var secureSetByGate = false
                gate.protectsAppSwitcher.collect {
                    secureSetByGate = applyRecentsProtection(recentsProtection(Build.VERSION.SDK_INT, it), secureSetByGate)
                }
            }
        }
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val deepLink by viewModel.pendingDeepLink.collectAsStateWithLifecycle()
            val darkTheme = when (state.theme) {
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
            }
            AppTheme(darkTheme = darkTheme, stylePack = definition.stylePack) {
                if (gate == null) {
                    if (state.showsContent(hasGate)) {
                        ShellNavHost(
                            definition = definition,
                            visibleTabs = definition.tabs.filter { it.routeId in state.visibleTabRoutes },
                            deepLinkScheme = brand.deepLinkScheme,
                            signedIn = state.session == SessionState.SignedIn,
                            deepLink = deepLink,
                            onDeepLinkConsumed = viewModel::onDeepLinkConsumed,
                            diagnostics = brand.environment == Environment.DEVELOPMENT,
                            onScreenView = { screen ->
                                analytics.track(AnalyticsEvent(AnalyticsEvents.SCREEN_VIEW, mapOf("screen" to screen)))
                            },
                        )
                    }
                } else {
                    GatedShell(gate, state, hasGate, deepLink)
                }
            }
        }
    }

    /**
     * The shell behind the app's lock: the splash until the lock decides, then the lock screen
     * **instead of** the app, or the app. `collectAsState` (not `…WithLifecycle`) so a lock set while
     * stopped is already composed when the app returns. The nav controller sits above [ShellRoot], so
     * the back stack survives a relock.
     */
    @Composable
    private fun GatedShell(gate: ContentGate, state: ShellState, hasGate: Boolean, deepLink: String?) {
        val gateState by gate.state.collectAsState()
        val navController = rememberNavController()
        ShellRoot(
            screen = shellScreen(state.showsContent(hasGate), gateState),
            lockScreen = checkNotNull(definition.lockScreen),
            onContentShown = ::requestConsentIfDue,
        ) {
            ShellNavHost(
                definition = definition,
                visibleTabs = definition.tabs.filter { it.routeId in state.visibleTabRoutes },
                deepLinkScheme = brand.deepLinkScheme,
                signedIn = state.session == SessionState.SignedIn,
                deepLink = deepLink,
                onDeepLinkConsumed = viewModel::onDeepLinkConsumed,
                navController = navController,
                diagnostics = brand.environment == Environment.DEVELOPMENT,
                onScreenView = { screen ->
                    analytics.track(AnalyticsEvent(AnalyticsEvents.SCREEN_VIEW, mapOf("screen" to screen)))
                },
            )
        }
    }

    /** Gathers launch consent after the frame that shows content, when this activity is resumed. */
    private fun requestConsent() {
        consentRequested = true
        window.decorView.post { consent.gatherOnLaunch() }
    }

    /**
     * Gate path: the user unlocked. After a cold start the splash is gone by then and its condition
     * isn't polled any more, so the content screen asks. Consent gathers once per process.
     */
    private fun requestConsentIfDue() {
        if (shouldGatherConsent(definition.needsConsent, splashKept = false, alreadyRequested = consentRequested)) requestConsent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeDeepLink(intent, deliver = true)
    }

    /**
     * Moves the link out of the intent into the shell, which opens it or holds it until sign-in.
     * Left in the intent, Navigation would open it by itself and bypass the sign-in gate.
     */
    private fun takeDeepLink(intent: Intent, deliver: Boolean) {
        val link = intent.data ?: return
        if (deliver) viewModel.onDeepLink(link.toString())
        setIntent(Intent(intent).setData(null))
    }
}

/** Consent is gathered once the splash lets go, and only by an app that needs it (Firebase or ads). */
internal fun shouldGatherConsent(needsConsent: Boolean, splashKept: Boolean, alreadyRequested: Boolean): Boolean =
    needsConsent && !splashKept && !alreadyRequested

/** With a content gate: whether the system splash stays ([keep]) and what [shouldGatherConsent] sees ([splashKept]). */
internal data class GatedSplash(val keep: Boolean, val splashKept: Boolean)

/** The lock screen lets the splash go but still holds launch consent: only the content releases it. */
internal fun gatedSplash(screen: ShellScreen): GatedSplash =
    GatedSplash(keep = screen == ShellScreen.SPLASH, splashKept = screen != ShellScreen.CONTENT)
