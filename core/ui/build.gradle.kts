plugins {
    id("whitelabel.android.library")
    id("whitelabel.android.library.compose")
}

android {
    namespace = "com.whitelabel.core.ui"
}

dependencies {
    api(project(":core:common"))
    api(libs.androidx.lifecycle.runtime.compose)
}
