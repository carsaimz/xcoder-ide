plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.xcoder.plugin.loader"
    compileSdk = 34
    defaultConfig { minSdk = 21 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":plugin-system:api"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)
}