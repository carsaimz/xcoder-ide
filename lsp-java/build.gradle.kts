plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.xcoder.lsp.java"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes { release { isMinifyEnabled = false } }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // ── sora-editor integration ────────────────────────────────────
    implementation(project(":editor:sora-editor"))
    implementation(libs.sora.editor)

    // ── LSP4J (Language Server Protocol 3.16) ─────────────────────
    implementation(libs.lsp4j)
    implementation(libs.lsp4j.jsonrpc)

    // ── AndroidX ──────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)

    // ── Hilt ──────────────────────────────────────────────────────
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // ── Coroutines ────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // ── Testing ──────────────────────────────────────────────────
    testImplementation(libs.junit)
}

kapt { correctErrorTypes = true }