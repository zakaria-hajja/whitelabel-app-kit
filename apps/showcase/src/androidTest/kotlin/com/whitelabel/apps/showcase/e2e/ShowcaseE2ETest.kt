package com.whitelabel.apps.showcase.e2e

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.whitelabel.apps.showcase.e2e.robots.CatalogRobot
import com.whitelabel.apps.showcase.e2e.robots.HomeRobot
import com.whitelabel.apps.showcase.e2e.robots.ProfileRobot
import com.whitelabel.core.mockapi.FakeBackend
import com.whitelabel.core.mockapi.FakeData
import com.whitelabel.core.shell.FoundationActivity
import com.whitelabel.core.testing.android.EndAppScopeRule
import com.whitelabel.core.testing.android.ResetAppDataRule
import com.whitelabel.core.testing.android.robots.SettingsRobot
import com.whitelabel.core.testing.android.robots.SignInRobot
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** Showcase's main flows, end to end, against the in-memory backend. Each test starts signed out. */
@HiltAndroidTest
class ShowcaseE2ETest {

    private val hilt = HiltAndroidRule(this)
    private val compose = createEmptyComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(ResetAppDataRule()).around(hilt).around(EndAppScopeRule()).around(compose)

    @Inject lateinit var backend: FakeBackend

    private val signIn = SignInRobot(compose, appName = "Showcase")
    private val home = HomeRobot(compose)
    private val catalog = CatalogRobot(compose)
    private val profile = ProfileRobot(compose)
    private val settings = SettingsRobot(compose)
    private var scenario: ActivityScenario<FoundationActivity>? = null

    @Before fun inject() = hilt.inject()

    @After fun close() {
        scenario?.close()
        finishActivities()
    }

    /** Starts the app; tests set up [backend] first, since remote flags are read at start. */
    private fun launch() {
        scenario?.close()
        scenario = ActivityScenario.launch(FoundationActivity::class.java)
    }

    /**
     * Cold-starts the app with [link], as a browser would. Not through ActivityScenario: the shell
     * takes the link out of the activity's intent to hold it until sign-in, and the scenario then
     * no longer recognises the activity as the one it launched.
     */
    private fun launchWith(link: Intent) {
        InstrumentationRegistry.getInstrumentation().targetContext.startActivity(link)
    }

    private fun finishActivities() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
    }

    private fun signInAsAlice() {
        signIn.signIn("alice@shared.test", FakeData.PASSWORD)
        home.greets("Alice")
    }

    @Test fun signIn_greetsTheUser_andSignOutReturnsToSignIn() {
        launch()
        signInAsAlice()
        home.openTab("Settings")
        settings.signOut()
        signIn.isShown()
    }

    @Test fun wrongPassword_staysOnSignIn_withTheMessage() {
        launch()
        signIn.signIn("alice@shared.test", "not-the-password")
        signIn.showsWrongCredentials()
    }

    @Test fun register_landsOnHome() {
        launch()
        signIn.register("new@shared.test", "a-long-password", "Nadia")
        home.greets("Nadia")
    }

    @Test fun catalog_searchFilterSortAndOpenAnItem() {
        launch()
        signInAsAlice()
        home.openTab("Catalog")
        catalog.search("Item 1")
        catalog.showsInOrder("Item 19", "Item 18", "Item 17") // newest first
        catalog.doesNotSee("Item 2")
        catalog.filter("Music")
        catalog.showsInOrder("Item 19", "Item 15", "Item 11")
        catalog.doesNotSee("Item 18")
        catalog.sortBy("Title A–Z")
        catalog.showsInOrder("Item 11", "Item 15", "Item 19")
        catalog.open("Item 11")
        catalog.showsDetail("Sample item 11")
    }

    @Test fun catalog_switchedOff_hidesTabAndShortcut() {
        backend.features["catalog"] = false
        launch()
        signInAsAlice()
        assertFalse(home.hasTab("Catalog"))
        assertFalse(home.hasShortcut("Browse the catalog"))
    }

    @Test fun profile_editName_andHomeGreetsTheNewName() {
        launch()
        signInAsAlice()
        home.openTab("Profile")
        profile.changeFirstName("Ada")
        profile.openTab("Home")
        home.greets("Ada")
    }

    @Test fun changePassword_wrongCurrent_staysSignedIn() {
        launch()
        signInAsAlice()
        home.openTab("Profile")
        profile.changePassword(current = "not-the-password", new = "Another-pass-1")
        profile.showsWrongCurrentPassword()
        profile.doesNotSee("Sign in to Showcase")
        assertFalse(backend.requests.toString(), backend.requests.any { it.endsWith("/auth/logout") })
    }

    @Test fun expiredAccessToken_refreshesSilently() {
        launch()
        signInAsAlice()
        val before = backend.requests.count { it == REFRESH }
        backend.expireAccessTokens()
        home.pullToRefresh("Alice")
        compose.waitUntil(5_000) { backend.requests.count { it == REFRESH } > before }
        home.greets("Alice")
        signIn.doesNotSee("Sign in to Showcase")
        assertEquals(backend.requests.toString(), before + 1, backend.requests.count { it == REFRESH })
    }

    @Test fun revokedSession_returnsToSignIn() {
        launch()
        signInAsAlice()
        backend.revokeSessions()
        launch()
        signIn.isShown()
    }

    @Test fun catalogDeepLink_whileSignedOut_opensAfterSignIn() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("showcase://catalog/${FakeData.itemId(7)}"))
            .setClassName(context, FoundationActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // as a browser sends it
        launchWith(link)
        signIn.signIn("alice@shared.test", FakeData.PASSWORD)
        catalog.showsDetail("Sample item 7")
    }

    private companion object {
        const val REFRESH = "POST /api/v1/auth/refresh"
    }
}
