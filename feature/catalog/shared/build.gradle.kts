plugins {
    id("whitelabel.kmp.library")
    id("whitelabel.kmp.resources")
    id("whitelabel.koin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(project(":core:presentation"))
            implementation(project(":core:common"))
            implementation(project(":core:network"))
            implementation(project(":core:analytics"))
            implementation(project(":core:branding"))
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.androidx.lifecycle.viewmodel.savedstate)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies { implementation(libs.ktor.client.mock) }
        jvmTest.dependencies {
            implementation(project(":core:testing"))
            implementation(libs.ktor.client.mock)
        }
    }
}
