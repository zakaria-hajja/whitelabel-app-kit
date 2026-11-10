plugins {
    id("whitelabel.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.whitelabel.core.datastore"
}

dependencies {
    api(libs.androidx.datastore.core)
    api(libs.kotlinx.serialization.json)
}
