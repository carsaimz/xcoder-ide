plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.xcoder.editor.sora"
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
    implementation(project(":core:file-manager"))

    // -- Rosemoe sora-editor (CodeEditor) --
    implementation(libs.sora.editor)
    implementation(libs.sora.editor.language.java)
    implementation(libs.sora.editor.language.textmate)

    // -- AndroidX --
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // -- Compose --
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // -- Hilt --
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // -- Coroutines --
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // -- LSP4J (data model for diagnostics/completions) --
    implementation(libs.lsp4j)
    implementation(libs.lsp4j.jsonrpc)

    // -- Testing --
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}

kapt { correctErrorTypes = true }
