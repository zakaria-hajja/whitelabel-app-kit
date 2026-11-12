plugins {
    id("whitelabel.kmp.library")
    // consentModule() is a Koin module.
    id("whitelabel.koin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
    }
}
