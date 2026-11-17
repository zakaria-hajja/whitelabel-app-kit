package com.whitelabel.core.ads

import kotlin.time.Clock

/** A loaded native ad, wrapped by the platform's Google host. [destroy] releases the SDK's ad. */
interface NativeAdHandle {
    fun destroy()
}

/** What a list shows when it appears: [ad] (null for nothing yet), and whether to [request] a new one now. */
data class NativeAdVisit(val ad: NativeAdHandle?, val request: Boolean)

/**
 * When a native placement requests, shows, keeps and destroys an ad, the same on both platforms.
 * A *visit* is the time the list is on screen with the rules at `Ready`.
 *
 * - An ad is shown at most [maxAgeMillis] (1 h) after it loaded; an older one is destroyed.
 * - Requests are at least [refreshMillis] (60 s) apart, one at a time, and only while a visit begins.
 * - A shown ad is never swapped mid-visit: a newer one waits as *next* and shows on the next visit.
 * - An ad that loads after the visit ended is kept for the next one.
 *
 * Main thread only on both platforms: no locking, no coroutines, no SDK.
 */
class NativeAdRotation(
    private val nowMillis: () -> Long,
    val refreshMillis: Long = 60_000,
    val maxAgeMillis: Long = 3_600_000,
) {
    private class Held(val ad: NativeAdHandle, val loadedAt: Long)

    private var current: Held? = null
    private var next: Held? = null
    private var inFlight = false
    private var lastRequestAt: Long? = null
    private var inVisit = false

    /** The list appeared with the rules at `Ready`: what to show, and whether to start a request. */
    fun beginVisit(): NativeAdVisit {
        val now = nowMillis()
        current = current?.takeUnlessExpired(now)
        next = next?.takeUnlessExpired(now)
        next?.let { promoted ->
            current?.ad?.destroy()
            current = promoted
            next = null
        }
        inVisit = true
        val shown = current
        val request = !inFlight &&
            (shown == null || now - shown.loadedAt >= refreshMillis) &&
            lastRequestAt.let { it == null || now < it || now - it >= refreshMillis }
        if (request) {
            inFlight = true
            lastRequestAt = now
        }
        return NativeAdVisit(shown?.ad, request)
    }

    /**
     * A requested ad loaded: returns it when it shows now (a visit with nothing shown), or null when
     * it is kept for the next visit (replacing, and destroying, an earlier kept one).
     */
    fun loaded(ad: NativeAdHandle): NativeAdHandle? {
        inFlight = false
        val held = Held(ad, nowMillis())
        if (inVisit && current == null) {
            current = held
            return ad
        }
        next?.ad?.destroy()
        next = held
        return null
    }

    /** A requested ad failed to load: a later visit retries, still [refreshMillis] after the last request. */
    fun failed() {
        inFlight = false
    }

    /** The list left the screen (or the rules stopped being `Ready`); held ads stay for the next visit. */
    fun endVisit() {
        inVisit = false
    }

    /**
     * The ad the next [beginVisit] would show (a fresh *next*, else a fresh *current*), without
     * beginning a visit, requesting or destroying anything. A new slot draws it on its first frame,
     * so a list that comes back to a held ad has its row at once instead of one frame later.
     */
    fun peek(): NativeAdHandle? {
        val now = nowMillis()
        return (next?.takeIf { !it.isExpired(now) } ?: current?.takeIf { !it.isExpired(now) })?.ad
    }

    // A clock that went back before the load leaves the age unknown: treated as expired.
    private fun Held.isExpired(now: Long): Boolean = now < loadedAt || now - loadedAt > maxAgeMillis

    private fun Held.takeUnlessExpired(now: Long): Held? =
        if (isExpired(now)) {
            ad.destroy()
            null
        } else {
            this
        }
}

/** One [NativeAdRotation] per placement for the whole process, so its ad survives leaving the screen. */
object NativeAdRotations {
    private val rotations = mutableMapOf<String, NativeAdRotation>()

    /** The rotation of [placement], created on first use on [Clock.System]. Main thread only. */
    fun of(placement: String): NativeAdRotation =
        rotations.getOrPut(placement) { NativeAdRotation(nowMillis = { Clock.System.now().toEpochMilliseconds() }) }
}
