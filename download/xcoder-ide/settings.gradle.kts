/*
 * XCoder IDE — Project Settings
 * Configures plugin management, dependency resolution, and module includes.
 */

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "XCoder IDE"

// ── App Module ────────────────────────────────────────────────────────────
include(":app")

// ── Core Modules ──────────────────────────────────────────────────────────
include(":core:file-manager")
include(":core:terminal")
include(":core:git")
include(":core:settings")

// ── Editor Modules ────────────────────────────────────────────────────────
include(":editor:web-editor")
include(":editor:native-editor")

// ── Feature Modules ───────────────────────────────────────────────────────
include(":visual-editor")
include(":build-engine")
include(":ai-copilot")

// ── Plugin System Modules ─────────────────────────────────────────────────
include(":plugin-system:api")
include(":plugin-system:loader")
