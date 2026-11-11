package com.whitelabel.core.network

import com.whitelabel.core.common.DataError
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import kotlin.test.Test
import kotlin.test.assertEquals
import platform.Foundation.NSError
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorTimedOut

/** How the Darwin engine's failures reach the user on iOS. */
class ConnectivityFailureIosTest {

    private fun darwin(code: Long) = DarwinHttpRequestException(NSError(NSURLErrorDomain, code, null))

    @Test fun `darwin not connected maps to NO_INTERNET`() {
        assertEquals(DataError.Network.NO_INTERNET, darwin(NSURLErrorNotConnectedToInternet).toDataError())
    }

    @Test fun `darwin timed out maps to REQUEST_TIMEOUT`() {
        assertEquals(DataError.Network.REQUEST_TIMEOUT, darwin(NSURLErrorTimedOut).toDataError())
    }

    @Test fun `any other exception maps to UNKNOWN`() {
        assertEquals(DataError.Network.UNKNOWN, IllegalStateException().toDataError())
    }
}
