package com.whitelabel.core.firebase.google

import android.content.pm.ApplicationInfo
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.whitelabel.core.analytics.Analytics
import com.whitelabel.core.analytics.CrashReporter
import com.whitelabel.core.firebase.CollectionSwitch
import com.whitelabel.core.firebase.RemoteConfigSource
import com.whitelabel.core.network.ModuleContributor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Minimum fetch interval: none in debug builds so changes show at once; 12 hours in release. */
internal const val DEBUG_FETCH_INTERVAL_SECONDS = 0L
internal const val RELEASE_FETCH_INTERVAL_SECONDS = 43_200L

internal fun fetchIntervalSeconds(debuggable: Boolean): Long =
    if (debuggable) DEBUG_FETCH_INTERVAL_SECONDS else RELEASE_FETCH_INTERVAL_SECONDS

/** Binds the Firebase-backed sources when this module is on the classpath (found by `ServiceLoader`). */
class FirebaseContributor : ModuleContributor {
    override fun modules(): List<Module> = listOf(
        module {
            val firebase = named("firebase")
            single<RemoteConfigSource> {
                val debuggable = androidContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                FirebaseRemoteConfigSource(FirebaseRemoteConfig.getInstance(), fetchIntervalSeconds(debuggable))
            }
            single<Analytics>(firebase) { FirebaseAnalyticsAdapter(FirebaseAnalytics.getInstance(androidContext())) }
            single<CrashReporter>(firebase) { FirebaseCrashReporter(FirebaseCrashlytics.getInstance()) }
            single<CollectionSwitch> {
                FirebaseCollectionSwitch(FirebaseAnalytics.getInstance(androidContext()), FirebaseCrashlytics.getInstance())
            }
        },
    )
}
