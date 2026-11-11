plugins {
    id("whitelabel.kmp.library")
    id("whitelabel.koin")
}

// Debug only: an app's framework includes it with -Pwhitelabel.mockApi=true, so Release never
// contains the fake backend.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:ios"))
            api(project(":core:mockapi"))
        }
    }
}
