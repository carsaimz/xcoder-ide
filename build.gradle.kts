/*
 * XCoder IDE — Root Build Configuration
 * Applies common Android and Kotlin configuration to all subprojects.
 */

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kapt) apply false
}

subprojects {
    // ── JVM Toolchain ──────────────────────────────────────────────────
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
        }
    }

    // ── Android Configuration ─────────────────────────────────────────
    afterEvaluate {
        if (plugins.hasPlugin("com.android.application") || plugins.hasPlugin("com.android.library")) {
            extensions.configure<com.android.build.gradle.BaseExtension> {
                compileSdkVersion(34)

                defaultConfig {
                    minSdk = 21
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    vectorDrawables {
                        useSupportLibrary = true
                    }
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                val isApp = plugins.hasPlugin("com.android.application")
                buildTypes {
                    named("release") {
                        isMinifyEnabled = isApp
                        if (isApp) {
                            isShrinkResources = true
                            proguardFiles(
                                getDefaultProguardFile("proguard-android-optimize.txt"),
                                file("proguard-rules.pro")
                            )
                        }
                    }
                    named("debug") {
                        isMinifyEnabled = false
                        isDebuggable = true
                    }
                }

                packagingOptions {
                    resources {
                        excludes += "/META-INF/{AL2.0,LGPL2.1}"
                        excludes += "/META-INF/LICENSE*"
                        excludes += "/META-INF/NOTICE*"
                    }
                }
            }
        }

        // ── Kotlin Options ────────────────────────────────────────────
        if (plugins.hasPlugin("org.jetbrains.kotlin.android") || plugins.hasPlugin("org.jetbrains.kotlin.jvm")) {
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }

        // ── Kapt: auto-apply when Hilt or Room is on the classpath ────
        val hasKapt = configurations.any { it.name == "kapt" }
        if (hasKapt && !plugins.hasPlugin("org.jetbrains.kotlin.kapt")) {
            apply(plugin = "org.jetbrains.kotlin.kapt")
        }
    }
}

// ── Clean Task ────────────────────────────────────────────────────────────
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
