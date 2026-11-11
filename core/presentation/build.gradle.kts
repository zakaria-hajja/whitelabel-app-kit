plugins {
    id("whitelabel.kmp.library")
    id("whitelabel.kmp.resources")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:common"))
        }
    }
}
