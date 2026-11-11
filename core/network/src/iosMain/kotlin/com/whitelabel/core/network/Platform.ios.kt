package com.whitelabel.core.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import kotlinx.io.IOException
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorTimedOut

actual fun defaultHttpEngine(): HttpClientEngine = Darwin.create()

/** As on the JVM: any I/O failure, which includes every Darwin request error (it is an IOException). */
internal actual fun Throwable.isConnectivityFailure(): Boolean = this is IOException

/** The Darwin engine reports its own timeout as an NSURLErrorTimedOut request error. */
internal actual fun Throwable.isPlatformTimeout(): Boolean =
    this is DarwinHttpRequestException && origin.domain == NSURLErrorDomain && origin.code == NSURLErrorTimedOut
