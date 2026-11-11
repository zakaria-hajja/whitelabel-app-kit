package com.whitelabel.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.channels.UnresolvedAddressException

actual fun defaultHttpEngine(): HttpClientEngine = OkHttp.create()

internal actual fun Throwable.isConnectivityFailure(): Boolean =
    this is IOException || this is UnresolvedAddressException

internal actual fun Throwable.isPlatformTimeout(): Boolean = this is SocketTimeoutException
