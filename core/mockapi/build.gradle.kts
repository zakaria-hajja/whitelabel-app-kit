plugins {
    id("whitelabel.kmp.library")
    id("whitelabel.koin")
}

// Debug and test builds only: the app plugin adds it as debugImplementation behind
// -Pwhitelabel.mockApi=true, and end-to-end tests add it as androidTestImplementation.
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:network"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.atomicfu)
        }
        commonTest.dependencies { implementation(project(":core:common")) }
        jvmTest.dependencies { implementation(project(":core:testing")) }
    }
}
