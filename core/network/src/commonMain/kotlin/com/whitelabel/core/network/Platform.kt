package com.whitelabel.core.network

import io.ktor.client.engine.HttpClientEngine

/** The platform's HTTP engine: OkHttp on Android (and the JVM host), Darwin on iOS. */
expect fun defaultHttpEngine(): HttpClientEngine

/** The request got no answer because the device is offline or the host can't be reached. */
internal expect fun Throwable.isConnectivityFailure(): Boolean

/** A platform engine's own timeout, beyond Ktor's (`HttpRequestTimeoutException` and the like). */
internal expect fun Throwable.isPlatformTimeout(): Boolean
