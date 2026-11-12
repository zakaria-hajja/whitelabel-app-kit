package com.whitelabel.core.ads

/** Asks for tracking permission (iOS ATT) when it is needed. [onDone] runs once the prompt is settled or was not needed. */
interface TrackingPort {
    fun requestIfNeeded(onDone: () -> Unit)
}

/** Android, and iOS with the switch off: there is nothing to ask, so it is done at once. */
object NoTracking : TrackingPort {
    override fun requestIfNeeded(onDone: () -> Unit) = onDone()
}
