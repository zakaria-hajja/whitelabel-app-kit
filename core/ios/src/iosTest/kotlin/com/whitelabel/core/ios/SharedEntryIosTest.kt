package com.whitelabel.core.ios

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
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okio.FileSystem

/** What the iOS app calls to start the shared graph and get its ViewModels. */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedEntryIosTest {

    private val dir = testDir()
    private val settingsOnly = DefinitionSpec(listOf(FeatureSpec("settings", null, SettingsRoutes.ROOT, false, false, false)), null)

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() {
        Shared.stop()
        FileSystem.SYSTEM.deleteRecursively(dir)
        Dispatchers.resetMain()
    }

    @Test fun `every accessor gives its view model`() {
        Shared.start(testPlatform(dir), settingsOnly, emptyList(), useMockApi = false)
        Shared.shellViewModel()
        Shared.settingsViewModel()
        Shared.homeViewModel()
        Shared.profileViewModel()
        Shared.changePasswordViewModel()
        Shared.loginViewModel(email = null)
        Shared.registerViewModel()
        Shared.forgotPasswordViewModel(email = "ada@example.com")
        Shared.resetPasswordViewModel(token = "t1")
        Shared.catalogListViewModel(CatalogOptions())
        Shared.catalogDetailViewModel(id = "i1")
    }

    @Test fun `reset for tests gives a new graph`() {
        Shared.start(testPlatform(dir), settingsOnly, emptyList(), useMockApi = false)
        val before = Shared.shellViewModel()
        Shared.resetForTests()
        val after = Shared.shellViewModel()
        assertNotSame(before, after)
        assertFalse(after.state.value.ready && after.state.value.visibleTabRoutes.isEmpty())
    }

    @Test fun `starting twice keeps the first graph`() {
        Shared.start(testPlatform(dir), settingsOnly, emptyList(), useMockApi = false)
        Shared.start(testPlatform(dir), settingsOnly, emptyList(), useMockApi = false)
        assertTrue(Shared.isStarted)
    }

    @Test fun `an invalid definition fails with its rule`() {
        val noTabs = DefinitionSpec(listOf(FeatureSpec("a", null, null, false, false, false)), null)
        val error = assertFailsWith<IllegalArgumentException> { Shared.start(testPlatform(dir), noTabs, emptyList(), useMockApi = false) }
        assertEquals("An app definition needs at least one tab", error.message)
    }

    @Test fun `the mock api without the mock module fails naming the gradle property`() {
        val error = assertFailsWith<IllegalStateException> { Shared.start(testPlatform(dir), settingsOnly, emptyList(), useMockApi = true) }
        assertTrue(error.message!!.contains("-Pwhitelabel.mockApi=true"), error.message)
    }
}
