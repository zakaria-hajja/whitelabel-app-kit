plugins {
    id("whitelabel.kmp.library")
    id("whitelabel.koin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        jvmMain.dependencies { implementation(libs.ktor.client.okhttp) }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }
        commonTest.dependencies { implementation(libs.ktor.client.mock) }
        jvmTest.dependencies { implementation(project(":core:testing")) }
        iosTest.dependencies { implementation(libs.ktor.client.darwin) }
    }
}
