/*
 * XCoder IDE — Root Build Configuration
 * Applies common Android and Kotlin configuration to all subprojects.
 * The :app module configures itself fully; only library modules get defaults here.
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

    // ── Android Configuration (library modules only) ─────────────────
    // The :app module declares its own full configuration.
    afterEvaluate {
        if (name == "app") return@afterEvaluate

        if (plugins.hasPlugin("com.android.library")) {
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

                buildTypes {
                    named("release") {
                        isMinifyEnabled = false
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
    }
}

// ── Clean Task ────────────────────────────────────────────────────────────
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
