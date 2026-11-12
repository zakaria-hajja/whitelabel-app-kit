package com.whitelabel.core.ads

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

// DataStore keeps a process-wide registry by path, so every test gets its own path.
private var nextDir = 0

class AdFreeTest {
    private val fileSystem = FakeFileSystem()
    private val path = "/data/${nextDir++}/ads-free.json".toPath()
    private var now = Instant.fromEpochSeconds(10_000)
    private val clock = object : Clock { override fun now() = now }

    private fun TestScope.adFree(job: Job = Job()) =
        AdFree(path, clock, CoroutineScope(UnconfinedTestDispatcher(testScheduler) + job), fileSystem)

    private fun at(hoursFromNow: Int) = now + hoursFromNow.hours

    @Test
    fun `starts inactive`() = runTest {
        assertThat(adFree().state.value).isEqualTo(AdFreeState.Inactive)
    }

    @Test
    fun `grant then expiry`() = runTest {
        val adFree = adFree()
        adFree.grantUntil(at(1))
        assertThat(adFree.state.value).isEqualTo(AdFreeState.Active(at(1)))
        now = Instant.fromEpochSeconds(10_000 + 3_599)
        assertThat(adFree.state.value).isEqualTo(AdFreeState.Active(Instant.fromEpochSeconds(10_000 + 3_600)))
        now = Instant.fromEpochSeconds(10_000 + 3_600)
        assertThat(adFree.state.value).isEqualTo(AdFreeState.Inactive)
    }

    @Test
    fun `extend stacks onto an active window`() = runTest {
        val adFree = adFree()
        adFree.grantUntil(at(1))
        adFree.extend(2.hours)
        assertThat(adFree.state.value).isEqualTo(AdFreeState.Active(at(3)))
    }

    @Test
    fun `extend after expiry starts from now`() = runTest {
        val adFree = adFree()
        adFree.grantUntil(at(1))
        val later = at(5)
        now = later
        adFree.extend(2.hours)
        assertThat(adFree.state.value).isEqualTo(AdFreeState.Active(later + 2.hours))
    }

    @Test
    fun `extend with nothing granted starts from now`() = runTest {
        val adFree = adFree()
        adFree.extend(1.hours)
        assertThat(adFree.state.value).isEqualTo(AdFreeState.Active(at(1)))
    }

    @Test
    fun `grant never shortens an active window`() = runTest {
        val adFree = adFree()
        adFree.grantUntil(at(5))
        adFree.grantUntil(at(2))
        assertThat(adFree.state.value).isEqualTo(AdFreeState.Active(at(5)))
        adFree.grantUntil(at(8))
        assertThat(adFree.state.value).isEqualTo(AdFreeState.Active(at(8)))
    }

    @Test
    fun `the window survives a store reopen`() = runTest {
        val process1 = Job()
        adFree(process1).grantUntil(at(4))
        process1.cancel()
        val reopened = adFree()
        assertThat(reopened.state.value).isEqualTo(AdFreeState.Active(at(4)))
    }
}
