plugins {
    id("whitelabel.kmp.library")
    // The ads rules module is a Koin module.
    id("whitelabel.koin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:consent:shared"))
            api(libs.kotlinx.datetime)
            implementation(project(":core:datastore"))
        }
        commonTest.dependencies { implementation(libs.okio.fakefilesystem) }
    }
}
