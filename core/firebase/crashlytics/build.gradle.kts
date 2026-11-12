plugins {
    id("whitelabel.kmp.library")
}

// iOS code only: the Kotlin crash hook over CrashKiOS. It needs Crashlytics in the app, so an app's
// framework gets it only with Firebase (firebase.enabled true or debug; the iOS app plugin adds and
// exports it). Kept out of :core:firebase:shared, which every app's framework links.
kotlin {
    sourceSets {
        iosMain.dependencies {
            implementation(libs.crashkios.crashlytics)
        }
    }
}
