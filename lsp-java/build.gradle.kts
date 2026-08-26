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
    kotlinOptions { jvmTarget = "17" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
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
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

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