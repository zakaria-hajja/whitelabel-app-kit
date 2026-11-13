package com.whitelabel.core.ios

import com.whitelabel.core.domain.DebugGuestAccount
import com.whitelabel.core.shell.model.DefinitionSpec
import com.whitelabel.core.shell.model.FeatureSpec
import com.whitelabel.feature.catalog.CatalogOptions
import com.whitelabel.feature.settings.SettingsRoutes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import org.koin.mp.KoinPlatform

/** What the iOS app calls to start the shared graph and get its ViewModels. */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedEntryIosTest {

    private val dir = testDir()
    private val settingsOnly = DefinitionSpec(listOf(FeatureSpec("settings", null, SettingsRoutes.ROOT, false, false, false)), null)

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() {
        SharedGraph.stop()
        SharedGraph.clearDebugGuest()
        FileSystem.SYSTEM.deleteRecursively(dir)
        Dispatchers.resetMain()
    }

    @Test fun `every accessor gives its view model`() {
        SharedGraph.start(testPlatform(dir), settingsOnly, emptyList(), useMockApi = false)
        SharedGraph.shellViewModel()
        SharedGraph.settingsViewModel()
        SharedGraph.homeViewModel()
        SharedGraph.profileViewModel()
        SharedGraph.changePasswordViewModel()
        SharedGraph.loginViewModel(email = null)
        SharedGraph.registerViewModel()
        SharedGraph.forgotPasswordViewModel(email = "ada@example.com")
        SharedGraph.resetPasswordViewModel(token = "t1")
        SharedGraph.catalogListViewModel(CatalogOptions())
        SharedGraph.catalogDetailViewModel(id = "i1")
    }

    @Test fun `reset for tests gives a new graph`() {
        SharedGraph.start(testPlatform(dir), settingsOnly, emptyList(), useMockApi = false)
        val before = SharedGraph.shellViewModel()
        SharedGraph.resetForTests()
        val after = SharedGraph.shellViewModel()
        assertNotSame(before, after)
        assertFalse(after.state.value.ready && after.state.value.visibleTabRoutes.isEmpty())
    }

    @Test fun `starting twice keeps the first graph`() {
        SharedGraph.start(testPlatform(dir), settingsOnly, emptyList(), useMockApi = false)
        SharedGraph.start(testPlatform(dir), settingsOnly, emptyList(), useMockApi = false)
        assertTrue(SharedGraph.isStarted)
    }

    @Test fun `an invalid definition fails with its rule`() {
        val noTabs = DefinitionSpec(listOf(FeatureSpec("a", null, null, false, false, false)), null)
        val error = assertFailsWith<IllegalArgumentException> { SharedGraph.start(testPlatform(dir), noTabs, emptyList(), useMockApi = false) }
        assertEquals("An app definition needs at least one tab", error.message)
    }

    @Test fun `the mock api without the mock module fails naming the gradle property`() {
        val error = assertFailsWith<IllegalStateException> { SharedGraph.start(testPlatform(dir), settingsOnly, emptyList(), useMockApi = true) }
        assertTrue(error.message!!.contains("-Pwhitelabel.mockApi=true"), error.message)
    }

    @Test fun `a debug guest is bound on the real backend`() {
        SharedGraph.useDebugGuest("me@x.test", "pw")
        SharedGraph.start(testPlatform(dir), settingsOnly, emptyList(), useMockApi = false)
        assertEquals(DebugGuestAccount("me@x.test", "pw"), KoinPlatform.getKoin().get<DebugGuestAccount>())
    }

    @Test fun `no guest unless one is set`() {
        SharedGraph.useDebugGuest("me@x.test", "pw")
        SharedGraph.clearDebugGuest()
        SharedGraph.start(testPlatform(dir), settingsOnly, emptyList(), useMockApi = false)
        assertNull(KoinPlatform.getKoin().getOrNull<DebugGuestAccount>())
    }
}
