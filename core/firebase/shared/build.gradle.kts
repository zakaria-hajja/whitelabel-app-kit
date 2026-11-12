plugins {
    id("whitelabel.kmp.library")
    // firebaseModule() is a Koin module.
    id("whitelabel.koin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(project(":core:analytics"))
            api(project(":core:consent:shared"))
            implementation(libs.kotlinx.serialization.json)
        }
        // Kotlin crashes reach Crashlytics with Kotlin frames. CrashKiOS finds Crashlytics at run time,
        // so an app framework keeps this code only when it exports this module (apps with Firebase).
        iosMain.dependencies {
            implementation(libs.crashkios.crashlytics)
        }
    }
}
