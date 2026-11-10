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

    androidTestImplementation(libs.assertk)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
