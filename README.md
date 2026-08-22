# XCoder IDE

<p align="center">
  <strong>A powerful mobile IDE for Android</strong><br>
  <sub>Built with the same battle-tested libraries as AndroidIDE</sub>
</p>

---

## Features

- **Code Editor** — Rosemoe [sora-editor](https://github.com/Rosemoe/sora-editor) with 30+ language syntax highlighting via TextMate, code folding, auto-completion, search & replace (regex), minimap, sticky scroll, indent guides, bracket matching, breadcrumbs, pinch-to-zoom
- **Terminal** — [Termux](https://github.com/termux/termux-app) terminal-emulator with VT100/xterm emulation, 256-color & true-color, Unicode, scrollback buffer, multi-session, copy/paste, special keys
- **File Tree** — [AndroidTreeView](https://github.com/bmelnychuk/AndroidTreeView) with lazy loading, file type icons, selection highlighting, context menus
- **Java LSP** — [Java Language Server](https://github.com/eclipse-jdtls/eclipse.jdt.ls) (jdtls) integration via LSP4J for code completion, hover docs, go-to-definition, diagnostics, code actions, rename refactoring
- **AI Copilot** — Multi-provider LLM support (OpenAI, Gemini, Claude)
- **Visual Editor** — Block-based drag-and-drop UI builder
- **Git** — JGit integration (branch, commit, push, pull, log, diff, stash, clone)
- **Build Engine** — Gradle-based project building
- **APK Editor** — Smali/Dex editing, resource editing, APK signing
- **Remote Filesystem** — FTP/SFTP support with caching and sync
- **Plugin System** — Dynamic plugin loading via DexClassLoader
- **Code Formatter** — Kotlin, Java, JSON, XML, HTML, CSS formatting
- **Search in Project** — Cross-file search and replace
- **Bookmarks** — Code bookmark management

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 1.9+ |
| UI | Jetpack Compose + Material 3 |
| DI | Dagger Hilt |
| Min SDK | 21 (Android 5.0) |
| Target SDK | 34 (Android 14) |
| Compile SDK | 34 |
| JVM | 17 |
| AGP | 8.2.0 |
| Gradle | 8.5 |

## Modules (16)

```
xcoder-ide/
├── app/                          # Main application
├── core/
│   ├── file-manager/             # File operations & SAF
│   ├── terminal/                 # Termux terminal-emulator
│   ├── git/                      # JGit integration
│   └── settings/                 # Preferences & DataStore
├── editor/
│   └── sora-editor/              # Rosemoe sora-editor wrapper
├── visual-editor/                # Block-based UI builder
├── build-engine/                 # Gradle build system
├── ai-copilot/                   # LLM code assistant
├── search-in-project/            # Cross-file search
├── code-formatter/               # Code formatting
├── bookmarks/                    # Bookmark management
├── apk-editor/                   # APK/Smali/Dex editor
├── remote-filesystem/            # FTP/SFTP client
├── lsp-java/                     # Java Language Server (jdtls)
└── plugin-system/
    ├── api/                      # Plugin API
    └── loader/                   # Plugin class loader
```

## Thanks to

- **[Rosemoe](https://github.com/Rosemoe)** for the awesome [CodeEditor](https://github.com/Rosemoe/sora-editor)
- **[Termux](https://github.com/termux)** for [Terminal Emulator](https://github.com/termux/termux-app) (terminal-emulator & terminal-view modules)
- **[AndroidIDE](https://github.com/AndroidIDE/AndroidIDE)** for editor integration patterns, LSP client architecture, build system design, and UI patterns (Apache 2.0)
- **[Sketchware-IA](https://github.com/FabioSilva11/Sketchware-IA)** for visual editor architecture, widget models (ViewBean/LayoutBean/TextBean), XML parser/generator, AAPT2 build pipeline, and project structure patterns (GPL-3.0)
- **[Dalvikus](https://github.com/loerting/dalvikus)** for APK/DEX tree architecture, smali editing, resource table browsing, and APK signing patterns (MIT)
- **[Bogdan Melnychuk](https://github.com/bmelnychuk)** for [AndroidTreeView](https://github.com/bmelnychuk/AndroidTreeView)
- **[George Fraser](https://github.com/GotoFuse)** for the [Java Language Server](https://github.com/eclipse-jdtls/eclipse.jdt.ls)

## CI/CD

GitHub Actions workflows handle everything — Gradle is installed via `gradle/actions/setup-gradle@v4`, no wrapper files needed in the repo.

| Workflow | Trigger | Description |
|---------|---------|-------------|
| CI | Push to main/develop, PRs | Lint → Unit Tests → Build |
| Build Debug | Push to develop, manual | Pre-release APK with auto-tag |
| Release | Tag push (v*), manual | Signed APK + AAB + GitHub Release |
| Tag & Changelog | Manual | Bump version, create tag, trigger release |
| Update Changelog | Tag push, manual | Generate changelog from git history |
| Gradle Setup | Manual | Verify Gradle installation |
| Dependabot | Weekly (Monday) | Check for dependency updates |

## Build

```bash
# CI handles this automatically, but locally you can use:
gradle assembleDebug     # Build debug APK
gradle assembleRelease   # Build release APK (needs keystore config)
gradle testDebugUnitTest  # Run unit tests
gradle lintDebug         # Run lint checks
```

## License

See [LICENSE](LICENSE)

