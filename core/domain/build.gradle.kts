plugins {
    id("whitelabel.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:common"))
            api(libs.kotlinx.coroutines.core)
        }
    }
}
