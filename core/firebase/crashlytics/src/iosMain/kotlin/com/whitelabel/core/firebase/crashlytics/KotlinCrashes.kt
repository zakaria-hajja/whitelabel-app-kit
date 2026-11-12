package com.whitelabel.core.firebase.crashlytics

import co.touchlab.crashkios.crashlytics.CrashlyticsKotlin
import co.touchlab.crashkios.crashlytics.enableCrashlytics
import co.touchlab.crashkios.crashlytics.setCrashlyticsUnhandledExceptionHook

/**
 * Sends Kotlin crashes to Crashlytics with their Kotlin frames: an uncaught Kotlin exception is
 * recorded as a fatal crash before the app ends. Swift calls it once, right after
 * `FirebaseApp.configure()`, and only when Firebase is on. Crashlytics' own collection flag still
 * decides whether anything is sent.
 */
fun installKotlinCrashHook() {
    enableCrashlytics()
    setCrashlyticsUnhandledExceptionHook()
}

/** Records [throwable] as a non-fatal Crashlytics report with its Kotlin frames (after [installKotlinCrashHook]). */
fun recordKotlinException(throwable: Throwable) {
    CrashlyticsKotlin.sendHandledException(throwable)
}
