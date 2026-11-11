package com.whitelabel.apps.showcase.e2e

import android.content.Intent
import android.net.Uri
import com.whitelabel.core.testing.android.waitForText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
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
import com.whitelabel.core.testing.android.FreshAppRule
import com.whitelabel.core.testing.android.robots.SettingsRobot
import com.whitelabel.core.testing.android.robots.SignInRobot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.koin.core.context.GlobalContext

/** Showcase's main flows, end to end, against the in-memory backend. Each test starts signed out. */
class ShowcaseE2ETest {

    private val compose = createEmptyComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(FreshAppRule()).around(compose)

    /** This test's fake: read from the graph each time, since every test starts a new one. */
    private val backend: FakeBackend get() = GlobalContext.get().get()

    private val signIn = SignInRobot(compose, appName = "Showcase")
    private val home = HomeRobot(compose)
    private val catalog = CatalogRobot(compose)
    private val profile = ProfileRobot(compose)
    private val settings = SettingsRobot(compose)
    private var scenario: ActivityScenario<FoundationActivity>? = null

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

    /** Finishes every activity still alive (the deep-link test's isn't owned by a scenario) and waits until it's gone. */
    private fun finishActivities() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val live = listOf(Stage.PRE_ON_CREATE, Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.RESTARTED)
        fun alive() = live.flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
        instrumentation.runOnMainSync { alive().forEach { it.finish() } }
        val deadline = System.currentTimeMillis() + 5_000
        while (true) {
            var remaining = 0
            instrumentation.runOnMainSync { remaining = alive().size }
            if (remaining == 0) break
            check(System.currentTimeMillis() < deadline) { "$remaining activities still alive after the test" }
            Thread.sleep(50)
        }
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
        catalog.doesNotSee("Item 25") // the top of the unfiltered list
        catalog.filter("Music")
        catalog.showsInOrder("Item 19", "Item 15", "Item 11")
        catalog.doesNotSee("Item 18")
        catalog.doesNotSee("Item 23") // Music, but doesn't match "Item 1"
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
        val from = backend.exchanges.size
        backend.expireAccessTokens()
        home.pullToRefresh("Alice")
        val expected = listOf("get users/me 401 TOKEN_EXPIRED", "post auth/refresh 200", "get users/me 200")
        compose.waitUntil(5_000) { sessionCalls(from).size >= expected.size }
        // The original request was retried with the new pair and succeeded; nothing signed out.
        assertEquals(expected, sessionCalls(from))
        assertFalse(backend.requests.toString(), backend.requests.any { it.endsWith("/auth/logout") })
        home.greets("Alice")
    }

    @Test fun revokedSession_returnsToSignIn() {
        launch()
        signInAsAlice()
        val from = backend.exchanges.size
        backend.revokeSessions()
        launch()
        signIn.isShown()
        // Signed out because the backend refused the session, not for any other reason.
        assertEquals(
            listOf("get users/me 401 UNAUTHENTICATED", "post auth/refresh 401 INVALID_REFRESH_TOKEN"),
            sessionCalls(from),
        )
    }

    @Test fun relaunch_withAValidSession_staysSignedIn() {
        launch()
        signInAsAlice()
        launch()
        home.greets("Alice")
        signIn.doesNotSee("Sign in to Showcase")
    }

    /** The `users/me` and `auth/refresh` calls since exchange [from]: `"<method> <path> <status> [<code>]"`. */
    private fun sessionCalls(from: Int): List<String> = backend.exchanges.drop(from)
        .filter { it.path.endsWith("/users/me") || it.path.endsWith("/auth/refresh") }
        .map { e ->
            val code = Regex("\"code\":\"([A-Z_]+)\"").find(e.body)?.groupValues?.get(1)
            listOfNotNull(e.method, e.path.removePrefix("/api/v1/"), e.status.toString(), code).joinToString(" ")
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

    /** A reset link's token reaches the reset screen through navigation and SavedStateHandle. */
    @Test fun resetLink_carriesItsTokenToTheReset() {
        val token = checkNotNull(backend.issueResetToken("alice@shared.test"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("showcase://reset-password?token=$token"))
            .setClassName(context, FoundationActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        launchWith(link)
        compose.waitForText("Choose a new password")
        compose.onNodeWithText("New password").performTextInput("Another-pass-1")
        compose.onNodeWithText("Save password").performClick()
        compose.waitForText("Your password has been changed. Sign in with your new password.")
    }
}
