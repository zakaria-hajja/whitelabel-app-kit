plugins {
    id("whitelabel.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            implementation(project(":core:datastore"))
        }
    }
}
