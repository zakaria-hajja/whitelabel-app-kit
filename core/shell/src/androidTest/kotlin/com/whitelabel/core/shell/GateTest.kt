package com.whitelabel.core.shell

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.whitelabel.core.designsystem.theme.AppTheme
import com.whitelabel.core.shell.test.R
import kotlinx.serialization.Serializable
import org.junit.Rule
import org.junit.Test

@Serializable data object GateLoginRoute
@Serializable data object GateHomeRoute
@Serializable data object GateOtherRoute

class GateTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val gate = FeatureEntry(
        id = "auth",
        gate = Gate(GateLoginRoute),
        routes = {
            composable<GateLoginRoute>(deepLinks = listOf(navDeepLink { uriPattern = "test://reset" })) { Text("Login screen") }
        },
    )
    private val home = FeatureEntry(
        id = "home",
        requiresSignIn = true,
        tab = Tab(GateHomeRoute, R.string.test_tab_one, Icons.Default.Home),
        routes = { composable<GateHomeRoute> { Text("Home screen") } },
    )
    private val other = FeatureEntry(
        id = "other",
        tab = Tab(GateOtherRoute, R.string.test_tab_two, Icons.Default.Star),
        routes = {
            composable<GateOtherRoute>(deepLinks = listOf(navDeepLink { uriPattern = "test://other" })) { Text("Other screen") }
        },
    )
    private val definition = appDefinition {
        add(gate)
        add(home)
        add(other)
    }

    private var signedIn by mutableStateOf(false)
    private var deepLink by mutableStateOf<String?>(null)
    private var consumed = 0
    private lateinit var nav: NavHostController

    private var diagnostics = false

    private fun show() {
        rule.setContent {
            nav = rememberNavController()
            AppTheme {
                ShellNavHost(
                    definition = definition,
                    visibleTabs = definition.tabs,
                    deepLinkScheme = "test",
                    signedIn = signedIn,
                    deepLink = deepLink,
                    onDeepLinkConsumed = { consumed++; deepLink = null },
                    navController = nav,
                    diagnostics = diagnostics,
                )
            }
        }
    }

    @Test
    fun signedOut_startsAtGateRoute() {
        show()
        rule.onNodeWithText("Login screen").assertExists()
        rule.onNodeWithText("Home screen").assertDoesNotExist()
    }

    @Test
    fun signIn_switchesToMainWithNoBackStack() {
        show()
        signedIn = true
        rule.waitForIdle()
        rule.onNodeWithText("Home screen").assertExists()
        rule.runOnIdle { assertThat(nav.previousBackStackEntry).isNull() }
    }

    @Test
    fun signOut_returnsToGateAndDropsMainStack() {
        signedIn = true
        show()
        rule.onNodeWithText("Tab two").performClick()
        rule.onNodeWithText("Other screen").assertExists()
        signedIn = false
        rule.waitForIdle()
        rule.onNodeWithText("Login screen").assertExists()
        rule.runOnIdle { assertThat(nav.previousBackStackEntry).isNull() }
    }

    @Test
    fun mainDeepLinkWhileSignedOut_opensAfterSignIn() {
        show()
        deepLink = "test://other"
        rule.waitForIdle()
        rule.onNodeWithText("Login screen").assertExists()
        rule.runOnIdle { assertThat(consumed).isEqualTo(0) }
        signedIn = true
        rule.waitForIdle()
        rule.onNodeWithText("Other screen").assertExists()
        rule.runOnIdle { assertThat(consumed).isEqualTo(1) }
    }

    @Test
    fun authDeepLinkWhileSignedIn_isIgnored() {
        signedIn = true
        show()
        deepLink = "test://reset"
        rule.waitForIdle()
        rule.onNodeWithText("Home screen").assertExists()
        rule.onNodeWithText("Login screen").assertDoesNotExist()
        rule.runOnIdle { assertThat(consumed).isEqualTo(1) }
    }


    @Test
    fun diagnostics_registeredOnlyWhenEnabled() {
        signedIn = true
        show()
        rule.runOnIdle { assertThat(nav.graph.findNode<MainGraph>()?.let { (it as androidx.navigation.NavGraph).findNode<com.whitelabel.core.shell.diagnostics.DiagnosticsRoute>() }).isNull() }
    }

    @Test
    fun diagnostics_registeredInMainGraphWhenEnabled() {
        signedIn = true
        diagnostics = true
        show()
        rule.runOnIdle {
            val main = nav.graph.findNode<MainGraph>() as androidx.navigation.NavGraph
            assertThat(main.findNode<com.whitelabel.core.shell.diagnostics.DiagnosticsRoute>() != null).isEqualTo(true)
        }
    }
}
