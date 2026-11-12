package com.whitelabel.core.ads

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem

// DataStore keeps a process-wide registry by path, so every test gets its own path.
private var nextDir = 0

class LaunchCounterTest {
    private val fileSystem = FakeFileSystem()
    private val path = "/data/${nextDir++}/ads-launches.json".toPath()

    @Test
    fun `first launch is 1 and the next process is 2`() = runTest {
        val process1 = Job()
        val first = LaunchCounter(path, CoroutineScope(UnconfinedTestDispatcher(testScheduler) + process1), fileSystem)
        assertThat(first.launchNumber.value).isEqualTo(0)
        assertThat(first.countThisLaunch()).isEqualTo(1)
        assertThat(first.launchNumber.value).isEqualTo(1)
        process1.cancel()

        val second = LaunchCounter(
            path,
            CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()),
            fileSystem,
        )
        assertThat(second.countThisLaunch()).isEqualTo(2)
        assertThat(second.launchNumber.value).isEqualTo(2)
    }

    @Test
    fun `counting twice in one process doesn't advance`() = runTest {
        val counter = LaunchCounter(path, backgroundScope, fileSystem)
        assertThat(counter.countThisLaunch()).isEqualTo(1)
        assertThat(counter.countThisLaunch()).isEqualTo(1)
        assertThat(counter.launchNumber.value).isEqualTo(1)
    }
}
