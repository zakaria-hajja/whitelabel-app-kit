plugins {
    id("whitelabel.kmp.library")
    // The ads module (added in a later task) is a Koin module.
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
