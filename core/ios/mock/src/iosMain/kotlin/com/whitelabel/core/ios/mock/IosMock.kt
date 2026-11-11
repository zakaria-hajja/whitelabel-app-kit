package com.whitelabel.core.ios.mock

import com.whitelabel.core.ios.SharedGraph
import com.whitelabel.core.mockapi.FakeBackend
import com.whitelabel.core.mockapi.MockApiContributor
import org.koin.mp.KoinPlatform

/** The fake backend for Debug builds and UI tests: call [enable] before `SharedGraph.start(useMockApi = true)`. */
object IosMock {
    fun enable() = SharedGraph.registerContributor(MockApiContributor())

    /** The running graph's fake, to set it up (flags, expired tokens) from the app's debug controls. */
    fun fakeBackend(): FakeBackend = KoinPlatform.getKoin().get()
}
