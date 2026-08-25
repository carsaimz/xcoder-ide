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
        // JitPack — required for Termux terminal-emulator, AndroidTreeView
        maven { url = uri("https://jitpack.io") }
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

// ── Editor Module (Rosemoe sora-editor) ──────────────────────────────────
include(":editor:sora-editor")

// ── Feature Modules ───────────────────────────────────────────────────────
include(":visual-editor")
include(":build-engine")
include(":ai-copilot")
include(":search-in-project")
include(":code-formatter")
include(":bookmarks")
include(":apk-editor")
// include(":remote-filesystem") // TODO: kapt <Error module> — re-enable after fixing
include(":lsp-java")

// ── Plugin System Modules ─────────────────────────────────────────────────
include(":plugin-system:api")
include(":plugin-system:loader")
