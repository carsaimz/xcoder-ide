# Task 3-a: Write editor/terminal/core modules

## Files Written

### 1. IDEEditor.kt
- Path: `editor/sora-editor/src/main/java/com/xcoder/editor/sora/IDEEditor.kt`
- ~400 lines
- Wraps sora-editor CodeEditor with LSP, completion, diagnostics, preferences, search/replace

### 2. EditorCompletionWindow.kt
- Path: `editor/sora-editor/src/main/java/com/xcoder/editor/sora/EditorCompletionWindow.kt`
- ~320 lines
- LSP completion items with icons for 25+ CompletionItemKind values, color-coded

### 3. EditorDiagnosticOverlay.kt
- Path: `editor/sora-editor/src/main/java/com/xcoder/editor/sora/EditorDiagnosticOverlay.kt`
- ~350 lines
- Wavy underlines for error/warning/info/hint, long-press tooltip

### 4. SoraEditorViewModel.kt (REWRITE)
- Path: `editor/sora-editor/src/main/java/com/xcoder/editor/sora/SoraEditorViewModel.kt`
- ~460 lines
- Added LspState, per-file UndoRedoState, diagnostic counts

### 5. TerminalSessionManager.kt (REWRITE)
- Path: `core/terminal/src/main/java/com/xcoder/core/terminal/TerminalSessionManager.kt`
- ~330 lines
- Added ordered session list, listener interface, max 8 sessions, next/prev switching

### 6. TerminalViewClient.kt (NEW)
- Path: `core/terminal/src/main/java/com/xcoder/core/terminal/TerminalViewClient.kt`
- ~350 lines
- Full TerminalSessionClient with Ctrl shortcuts, 30+ extra keys, font/clipboard management

### 7. TerminalExtraKeys.kt (NEW)
- Path: `core/terminal/src/main/java/com/xcoder/core/terminal/TerminalExtraKeys.kt`
- ~220 lines
- 4 layouts, 50+ key mappings, display labels, serialization

### 8. core/terminal/build.gradle.kts (REWRITE)
- Added separate termux.terminal.emulator dependency, guava-empty

### 9. IDEPreferences.kt (NEW)
- Path: `core/settings/src/main/java/com/xcoder/core/settings/IDEPreferences.kt`
- ~310 lines
- 8 category objects with 60+ preference keys and defaults

### 10. FileUtil.kt (NEW)
- Path: `core/file-manager/src/main/java/com/xcoder/core/file/FileUtil.kt`
- ~400 lines
- 30+ file operations based on Sketchware-IA and Termux
