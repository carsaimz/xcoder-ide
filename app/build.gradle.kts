plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.xcoder.ide"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xcoder.ide"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = true
        warningsAsErrors = false
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    // --- XCoder Core Modules ---
    implementation(project(":core:file-manager"))
    implementation(project(":core:terminal"))
    implementation(project(":core:git"))
    implementation(project(":core:settings"))

    // --- Editor (Rosemoe sora-editor) ---
    implementation(project(":editor:sora-editor"))

    // --- Feature Modules ---
    implementation(project(":visual-editor"))
    implementation(project(":build-engine"))
    implementation(project(":ai-copilot"))
    implementation(project(":search-in-project"))
    implementation(project(":code-formatter"))
    implementation(project(":bookmarks"))
    implementation(project(":apk-editor"))
    implementation(project(":remote-filesystem"))

    // --- LSP (Java Language Server) ---
    implementation(project(":lsp-java"))

    // --- Plugin System ---
    implementation(project(":plugin-system:loader"))

    // --- AndroidTreeView --- (removed: JitPack unavailable, use Compose LazyColumn tree)

    // --- AndroidX Core ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // --- Compose ---
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // --- Material Components (XML-based Material3 attrs like cornerFamily) ---
    implementation("com.google.android.material:material:1.11.0")

    // --- Compose Integration ---
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    // --- Hilt ---
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // --- Image Loading ---
    implementation(libs.coil.compose)

    // --- Coroutines ---
    implementation(libs.kotlinx.coroutines.android)

    // --- Serialization ---
    implementation(libs.kotlinx.serialization.json)

    // --- Lifecycle ---
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // --- DataStore ---
    implementation(libs.datastore.preferences)

    // --- Desugaring (required by sora-editor language-textmate) ---
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // --- Testing ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

kapt {
    correctErrorTypes = true
}