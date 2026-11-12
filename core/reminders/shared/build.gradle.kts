plugins {
    id("whitelabel.kmp.library")
    // iosRemindersModule() (iOS) is a Koin module.
    id("whitelabel.koin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            implementation(project(":core:datastore"))
        }
    }
}
