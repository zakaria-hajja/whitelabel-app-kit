plugins {
    id("whitelabel.kmp.library")
    id("whitelabel.koin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(project(":core:branding"))
            implementation(project(":core:common"))
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.androidx.lifecycle.viewmodel.savedstate)
        }
    }
}
