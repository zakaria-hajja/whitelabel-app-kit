package com.whitelabel.core.mockapi

import com.whitelabel.core.network.EngineOverride
import com.whitelabel.core.network.ModuleContributor
import org.koin.dsl.module

/** The in-memory backend as the app's HTTP engine. One [FakeBackend] per graph, which tests can get to control it. */
val mockApiModule = module {
    single { FakeBackend() }
    single<EngineOverride> { EngineOverride { get<FakeBackend>().engine } }
}

/** Found by the shell through `ServiceLoader`: being on the classpath is the switch. */
class MockApiContributor : ModuleContributor {
    override fun modules() = listOf(mockApiModule)
}
