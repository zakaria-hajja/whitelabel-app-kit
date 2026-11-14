package com.whitelabel.core.ads

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.whitelabel.core.consent.Consent
import com.whitelabel.core.datastore.StorePaths
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module

class AdsKoinTest {
    @Test
    fun `starting koin counts the launch without resolving the controller`() = runTest {
        // adsModule uses the real file system, so use a fresh temporary directory.
        val dir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "ads-koin-${kotlin.time.Clock.System.now().toEpochMilliseconds()}"
        FileSystem.SYSTEM.createDirectories(dir)
        val job = Job()
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + job)
        val app = koinApplication {
            modules(
                module {
                    single { StorePaths { name -> dir / "$name.json" } }
                    single { Consent(null) }
                    single(named("applicationScope")) { scope }
                },
                adsModule(true, mapOf("home" to "u")),
            )
        }
        // The counter is a singleton; resolving it does not create the controller, it only reads the count.
        assertThat(app.koin.get<LaunchCounter>().launchNumber.value).isEqualTo(1)
        job.cancel()
        app.close()
        FileSystem.SYSTEM.deleteRecursively(dir)
    }

    @Test
    fun `the module binds the request policy it is given and the default without one`() {
        val npa = koinApplication(createEagerInstances = false) { modules(adsModule(true, emptyMap(), AdRequestPolicy(nonPersonalized = true))) }
        assertThat(npa.koin.get<AdRequestPolicy>()).isEqualTo(AdRequestPolicy(nonPersonalized = true))
        npa.close()
        val plain = koinApplication(createEagerInstances = false) { modules(adsModule(true, emptyMap())) }
        assertThat(plain.koin.get<AdRequestPolicy>()).isEqualTo(AdRequestPolicy.DEFAULT)
        plain.close()
    }
}
