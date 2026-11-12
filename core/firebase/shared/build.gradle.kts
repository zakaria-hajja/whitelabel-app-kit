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
            // FirebaseServicesContributor extends ModuleContributor.
            api(project(":core:network"))
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
