package com.whitelabel.core.shell.brand

import android.content.Context
import android.content.pm.ApplicationInfo
import com.whitelabel.core.consent.UmpResult
import com.whitelabel.core.consent.UmpStatus
import com.whitelabel.core.firebase.FirebaseMode
import com.whitelabel.core.firebase.FirebaseServicesContributor
import com.whitelabel.core.network.ModuleContributor
import com.whitelabel.core.shell.AppDefinition
import com.whitelabel.core.shell.checkFirebaseDeclared
import com.whitelabel.core.shell.debug.DebugMarkers

/** How the Firebase part of the graph is wired: its mode, the build kind, and (fake mode) the consent answer. */
internal data class FirebaseWiring(
    val mode: FirebaseMode,
    val debugBuild: Boolean,
    val debugAnalytics: Boolean,
    /** What the fake consent port answers; `null` makes the gather fail. Used only in fake mode. */
    val fakeConsent: UmpResult? = null,
) {
    companion object {
        val OFF = FirebaseWiring(FirebaseMode.OFF, debugBuild = false, debugAnalytics = false)
    }
}

/**
 * Whether `:core:firebase:google`'s contributor is among [contributors]. The shell can't reference
 * that class (release builds may not link it), and R8 renames it, so it is found by its marker type.
 */
internal fun firebaseContributorPresent(contributors: List<ModuleContributor>): Boolean =
    contributors.any { it is FirebaseServicesContributor }

internal fun firebaseModeFor(
    resource: String,
    contributorPresent: Boolean,
    fakeMarker: Boolean,
    debugBuild: Boolean,
): FirebaseMode = when {
    resource == "fake" || (fakeMarker && debugBuild) -> FirebaseMode.FAKE
    resource == "on" && contributorPresent -> FirebaseMode.ON
    resource == "on" -> throw IllegalStateException("firebase is on but :core:firebase:google isn't linked")
    else -> FirebaseMode.OFF
}

/**
 * What a `fake-consent-<state>` marker answers: `obtained` (default), `declined` (the form was
 * answered with every purpose refused), `failed` (the gather fails) or `notRequired`.
 */
internal fun fakeConsentResult(state: String): UmpResult? = when (state) {
    "declined" -> UmpResult(UmpStatus.OBTAINED, canRequestAds = true, purposeConsents = "0")
    "failed" -> null
    "notRequired" -> UmpResult(UmpStatus.NOT_REQUIRED, canRequestAds = true, purposeConsents = null)
    else -> UmpResult(UmpStatus.OBTAINED, canRequestAds = true, purposeConsents = "1111111111")
}

/**
 * Reads the app's `foundation_firebase` and `foundation_firebase_declared` strings (absent, so off
 * and false, until the app plugin writes them) and the Debug markers, checks them against the
 * definition, and decides the mode. Startup fails on an inconsistent configuration.
 */
internal fun firebaseWiringFor(context: Context, definition: AppDefinition, contributors: List<ModuleContributor>): FirebaseWiring {
    fun string(name: String, fallback: String): String {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        return if (id == 0) fallback else context.getString(id)
    }
    checkFirebaseDeclared(definition, declared = string("foundation_firebase_declared", "false") == "true")
    if (!definition.usesFirebase) return FirebaseWiring.OFF
    val debugBuild = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val markers = DebugMarkers.of(context, debugBuild)
    val mode = firebaseModeFor(
        resource = string("foundation_firebase", "off"),
        contributorPresent = firebaseContributorPresent(contributors),
        fakeMarker = markers.has(DebugMarkers.FAKE_FIREBASE),
        debugBuild = debugBuild,
    )
    return FirebaseWiring(
        mode = mode,
        debugBuild = debugBuild,
        debugAnalytics = markers.has(DebugMarkers.ANALYTICS_DEBUG),
        fakeConsent = fakeConsentResult(markers.fakeConsentState()),
    )
}
