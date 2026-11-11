plugins {
    id("whitelabel.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.datastore.core.okio)
            api(libs.okio)
            api(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies { implementation(libs.okio.fakefilesystem) }
    }
}
