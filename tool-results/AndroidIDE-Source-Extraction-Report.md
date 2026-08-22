# AndroidIDE Source Code Extraction Report for XCoder IDE

**Source Repository**: [AndroidIDEOfficial/AndroidIDE](https://github.com/AndroidIDEOfficial/AndroidIDE) (branch: `dev`)
**License**: GPL v3 (must comply when reusing)
**Target Package**: `com.xcoder.ide`

---

## Repository Structure Overview

AndroidIDE has **~50 modules**. The relevant modules mapped to XCoder IDE:

| AndroidIDE Module | XCoder IDE Module | Purpose |
|---|---|---|
| `editor/api` | `editor/sora-editor` | Editor interfaces |
| `editor/impl` | `editor/sora-editor` | IDEEditor, completion, language |
| `core/app` | `app` | Activities, fragments, UI |
| `termux/view`, `termux/emulator` | `core/terminal` | Terminal emulation |
| `tooling/api`, `tooling/impl` | `build-engine` | Gradle build system |
| `core/lsp-api` | `lsp-java` | LSP interfaces |
| `java/lsp` | `lsp-java` | Java language server |
| `utilities/preferences` | `core/settings` | Preferences framework |
| `utilities/treeview` | `core/file-manager` | File tree (in app module) |

---

## 1. EDITOR MODULE (`editor/sora-editor/`)

### 1.1 IEditor.java — Editor Interface
**Original**: `editor/api/src/main/java/com/itsaky/androidide/editor/api/IEditor.java`
**Target**: `editor/sora-editor/src/main/java/com/xcoder/editor/sora/api/IEditor.java`
**Adaptations**: Change package to `com.xcoder.editor.sora.api`, update import `com.itsaky.androidide.models` → `com.xcoder.editor.sora.models`

```java
package com.xcoder.editor.sora.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.xcoder.editor.sora.models.Position;
import com.xcoder.editor.sora.models.Range;
import io.github.rosemoe.sora.text.CharPosition;
import java.io.File;

/**
 * Interface for other modules to access the editor.
 * Adapted from AndroidIDE's IEditor.
 */
public interface IEditor {

    String KEY_FILE = "ide.editor.file";

    @Nullable
    File getFile();

    boolean isModified();

    void setSelection(@NonNull Position position);

    default void setSelection(@NonNull Range range) {
        setSelection(range.getStart(), range.getEnd());
    }

    void setSelection(@NonNull Position start, @NonNull Position end);

    default void setSelectionAround(CharPosition position) {
        setSelectionAround(position.getLine(), position.getColumn());
    }

    default void setSelectionAround(Position position) {
        setSelectionAround(position.getLine(), position.getColumn());
    }

    void setSelectionAround(int line, int column);

    Range getCursorLSPRange();

    Position getCursorLSPPosition();

    void validateRange(@NonNull Range range);

    default boolean isValidRange(Range range) {
        return isValidRange(range, false);
    }

    boolean isValidRange(Range range, boolean allowColumnEqual);

    default boolean isValidPosition(Position position) {
        return isValidPosition(position, false);
    }

    boolean isValidPosition(Position position, boolean allowColumnEqual);

    boolean isValidLine(int line);

    default boolean isValidColumn(int line, int column) {
        return isValidColumn(line, column, false);
    }

    boolean isValidColumn(int line, int column, boolean allowColumnEqual);

    int append(CharSequence text);

    void replaceContent(CharSequence newContent);

    void goToEnd();
}
```

### 1.2 ILspEditor.kt — LSP Editor Interface
**Original**: `editor/api/src/main/java/com/itsaky/androidide/editor/api/ILspEditor.kt`
**Target**: `editor/sora-editor/src/main/java/com/xcoder/editor/sora/api/ILspEditor.kt`
**Adaptations**: Change package, update LSP model imports

```kotlin
package com.xcoder.editor.sora.api

import com.xcoder.lsp.api.ILanguageClient
import com.xcoder.lsp.api.ILanguageServer
import com.xcoder.lsp.models.Command
import com.xcoder.lsp.models.SignatureHelp

/**
 * LSP functions for the editor.
 * Adapted from AndroidIDE's ILspEditor.
 */
interface ILspEditor {
    fun setLanguageServer(server: ILanguageServer?)
    fun setLanguageClient(client: ILanguageClient?)
    fun executeCommand(command: Command?)
    fun signatureHelp()
    fun showSignatureHelp(help: SignatureHelp?)
    fun findDefinition()
    fun findReferences()
    fun expandSelection()
    fun ensureWindowsDismissed()
}
```

### 1.3 IDEEditor.kt — Core Editor Widget (918 lines, KEY FILE)
**Original**: `editor/impl/src/main/java/com/itsaky/androidide/editor/ui/IDEEditor.kt`
**Target**: `editor/sora-editor/src/main/java/com/xcoder/editor/sora/ui/IDEEditor.kt`
**Adaptations**: 
- Package: `com.itsaky.androidide.editor.ui` → `com.xcoder.editor.sora.ui`
- Remove flashbar dependency (replace with Snackbar/MaterialDialog)
- Replace EventBus with custom observable/callback pattern
- Update all `com.itsaky.*` imports to `com.xcoder.*`
- Keep sora-editor imports as-is (`io.github.rosemoe.sora.*`)

**Key patterns from this file:**

```kotlin
package com.xcoder.editor.sora.ui

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.component.EditorBuiltinComponent
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [CodeEditor] implementation for the IDE.
 * Extends sora-editor's CodeEditor with LSP, completion, and IDE features.
 *
 * Adapted from AndroidIDE's IDEEditor.
 */
open class IDEEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
    private val editorFeatures: EditorFeatures = EditorFeatures()
) : CodeEditor(context, attrs, defStyleAttr, defStyleRes), IEditor by editorFeatures, ILspEditor {

    @Suppress("PropertyName")
    internal var _file: File? = null

    private var _actionsMenu: EditorActionsMenu? = null
    private var _signatureHelpWindow: SignatureHelpWindow? = null
    private var _diagnosticWindow: DiagnosticWindow? = null
    private var fileVersion = 0
    internal var isModified = false

    private val selectionChangeHandler = Handler(Looper.getMainLooper())
    private var selectionChangeRunner: Runnable? = Runnable {
        val languageClient = languageClient ?: return@Runnable
        val cursor = this.cursor ?: return@Runnable
        if (cursor.isSelected || _signatureHelpWindow?.isShowing == true) return@Runnable
        diagnosticWindow.showDiagnostic(
            languageClient.getDiagnosticAt(file, cursor.leftLine, cursor.leftColumn))
    }

    val editorScope = CoroutineScope(Dispatchers.Default + CoroutineName("IDEEditor"))
    protected val eventDispatcher = EditorEventDispatcher()

    private var setupTsLanguageJob: Job? = null
    private var sigHelpCancelChecker: ICancelChecker? = null

    var languageServer: ILanguageServer? = null
        private set
    var languageClient: ILanguageClient? = null
        private set

    var isEnsurePosAnimEnabled = true
    lateinit var searcher: IDEEditorSearcher

    val signatureHelpWindow: SignatureHelpWindow
        get() = _signatureHelpWindow ?: SignatureHelpWindow(this).also { _signatureHelpWindow = it }

    val diagnosticWindow: DiagnosticWindow
        get() = _diagnosticWindow ?: DiagnosticWindow(this).also { _diagnosticWindow = it }

    companion object {
        private const val SELECTION_CHANGE_DELAY = 500L
        internal val log = org.slf4j.LoggerFactory.getLogger(IDEEditor::class.java)

        fun createInputTypeFlags(): Int {
            var flags = EditorInfo.TYPE_CLASS_TEXT or
                EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE or
                EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            // Add visible password flag if needed for better IME experience
            return flags
        }
    }

    init {
        editorFeatures.editor = this
        eventDispatcher.editor = this
        eventDispatcher.init(editorScope)
        initEditor()
    }

    // === File management ===
    fun setFile(file: File?) {
        if (isReleased) return
        this._file = file
        file?.also { dispatchDocumentOpenEvent() }
    }

    val file: File? get() = _file

    // === LSP Server/Client setup ===
    override fun setLanguageServer(server: ILanguageServer?) {
        if (isReleased) return
        this.languageServer = server
        server?.also {
            this.languageClient = it.client
        }
    }

    override fun setLanguageClient(client: ILanguageClient?) {
        if (isReleased) return
        this.languageClient = client
    }

    // === LSP Operations ===
    override fun executeCommand(command: Command?) {
        if (isReleased || command == null) return
        when (command.command) {
            Command.TRIGGER_COMPLETION -> {
                getComponent(EditorAutoCompletion::class.java).requireCompletion()
            }
            Command.TRIGGER_PARAMETER_HINTS -> signatureHelp()
            Command.FORMAT_CODE -> formatCodeAsync()
        }
    }

    override fun signatureHelp() {
        if (isReleased) return
        val languageServer = this.languageServer ?: return
        val file = this.file ?: return
        this.languageClient ?: return
        sigHelpCancelChecker?.also { it.cancel() }
        val cancelChecker = JobCancelChecker().also { this.sigHelpCancelChecker = it }
        editorScope.launch(Dispatchers.Default) {
            cancelChecker.job = coroutineContext[Job]
            val help = safeGet("signature help request") {
                val params = SignatureHelpParams(file.toPath(), cursorLSPPosition, cancelChecker)
                languageServer.signatureHelp(params)
            }
            withContext(Dispatchers.Main) { showSignatureHelp(help) }
        }
    }

    override fun showSignatureHelp(help: SignatureHelp?) {
        if (isReleased) return
        signatureHelpWindow.setupAndDisplay(help)
    }

    override fun findDefinition() {
        if (isReleased) return
        val languageServer = this.languageServer ?: return
        val file = file ?: return
        // Launch async definition request
        editorScope.launch(Dispatchers.Default) {
            val result = safeGet("definition request") {
                val params = DefinitionParams(file.toPath(), cursorLSPPosition, JobCancelChecker())
                languageServer.findDefinition(params)
            }
            // Handle result - navigate to definition
            onFindDefinitionResult(result)
        }
    }

    override fun findReferences() {
        if (isReleased) return
        val languageServer = this.languageServer ?: return
        val file = file ?: return
        editorScope.launch(Dispatchers.Default) {
            val result = safeGet("references request") {
                val params = ReferenceParams(file.toPath(), cursorLSPPosition, true, JobCancelChecker())
                languageServer.findReferences(params)
            }
            onFindReferencesResult(result)
        }
    }

    override fun expandSelection() {
        if (isReleased) return
        val languageServer = this.languageServer ?: return
        val file = file ?: return
        editorScope.launch(Dispatchers.Default) {
            val initialRange = cursorLSPRange
            val result = safeGet("expand selection request") {
                val params = ExpandSelectionParams(file.toPath(), initialRange)
                languageServer.expandSelection(params)
            } ?: initialRange
            withContext(Dispatchers.Main) { setSelection(result) }
        }
    }

    override fun ensureWindowsDismissed() {
        _diagnosticWindow?.isShowing?.let { if (it) _diagnosticWindow?.dismiss() }
        _signatureHelpWindow?.isShowing?.let { if (it) _signatureHelpWindow?.dismiss() }
        _actionsMenu?.isShowing?.let { if (it) _actionsMenu?.dismiss() }
    }

    // === Editor initialization ===
    protected open fun initEditor() {
        lineNumberMarginLeft = ... // dp2px(2f)
        _actionsMenu = EditorActionsMenu(this).also { it.init() }
        markUnmodified()
        searcher = IDEEditorSearcher(this)
        inputType = createInputTypeFlags()

        // Setup completion window
        val window = EditorCompletionWindow(this)
        window.setAdapter(CompletionListAdapter())
        replaceComponent(EditorAutoCompletion::class.java, window)
        getComponent(EditorTextActionWindow::class.java).isEnabled = false

        // Content change listener -> dispatch to LSP
        subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
            if (isReleased) return@subscribeEvent
            markModified()
            file ?: return@subscribeEvent
            editorScope.launch {
                dispatchDocumentChangeEvent(event)
                checkForSignatureHelp(event)
            }
        }

        // Selection change listener -> show diagnostics
        subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
            if (isReleased) return@subscribeEvent
            _diagnosticWindow?.isShowing?.let { if (it) _diagnosticWindow?.dismiss() }
            selectionChangeRunner?.also {
                selectionChangeHandler.removeCallbacks(it)
                selectionChangeHandler.postDelayed(it, SELECTION_CHANGE_DELAY)
            }
        }
    }

    // === Language setup ===
    open fun setupLanguage(file: File?) {
        if (isReleased || file == null) return
        createLanguage(file) { language ->
            if (language is TreeSitterLanguage) {
                // Setup tree-sitter with color scheme
                IDEColorSchemeProvider.readSchemeAsync(context, editorScope, file.extension) { scheme ->
                    applyTreeSitterLang(language, file.extension, scheme)
                }
            } else {
                setEditorLanguage(language)
            }
        }
    }

    private inline fun createLanguage(file: File, crossinline callback: (Language?) -> Unit) {
        if (!file.isFile) return callback(EmptyLanguage())
        // Tree-sitter based languages
        if (TreeSitterLanguageProvider.hasTsLanguage(file)) {
            setupTsLanguageJob = editorScope.launch {
                callback(TreeSitterLanguageProvider.forFile(file, context))
            }
            return
        }
        // ANTLR/fallback languages
        val lang = when (file.extension) {
            "gradle" -> GroovyLanguage()
            "c", "h", "cc", "cpp", "cxx" -> CppLanguage()
            else -> EmptyLanguage()
        }
        callback(lang)
    }

    // === Document events ===
    open fun dispatchDocumentOpenEvent() {
        if (isReleased) return
        val file = this.file ?: return
        this.fileVersion = 0
        eventDispatcher.dispatch(DocumentOpenEvent(file.toPath(), text.toString(), fileVersion))
    }

    protected open fun dispatchDocumentChangeEvent(event: ContentChangeEvent) {
        if (isReleased) return
        val file = file?.toPath() ?: return
        var type = ChangeType.INSERT
        if (event.action == ContentChangeEvent.ACTION_DELETE) type = ChangeType.DELETE
        else if (event.action == ContentChangeEvent.ACTION_SET_NEW_TEXT) type = ChangeType.NEW_TEXT
        var changeDelta = if (type == ChangeType.NEW_TEXT) 0 else event.changedText.length
        if (type == ChangeType.DELETE) changeDelta = -changeDelta
        val start = event.changeStart
        val end = event.changeEnd
        val changeRange = Range(Position(start.line, start.column, start.index),
            Position(end.line, end.column, end.index))
        val changedText = event.changedText.toString()
        val changeEvent = DocumentChangeEvent(file, changedText, text.toString(), ++fileVersion, type,
            changeDelta, changeRange)
        eventDispatcher.dispatch(changeEvent)
    }

    // === Resource cleanup ===
    override fun release() {
        ensureWindowsDismissed()
        if (isReleased) return
        super.release()
        _actionsMenu?.destroy()
        _actionsMenu = null
        _signatureHelpWindow = null
        _diagnosticWindow = null
        languageServer = null
        languageClient = null
        _file = null
        fileVersion = 0
        markUnmodified()
        editorFeatures.editor = null
        eventDispatcher.editor = null
        eventDispatcher.destroy()
        selectionChangeRunner?.also { selectionChangeHandler.removeCallbacks(it) }
        selectionChangeRunner = null
        setupTsLanguageJob?.cancel("Editor is releasing resources.")
        if (editorScope.isActive) editorScope.cancel("Editor is releasing resources.")
    }

    open fun markUnmodified() { this.isModified = false }
    open fun markModified() { this.isModified = true }

    // Helper
    private inline fun <T> safeGet(name: String, action: () -> T): T? {
        return try { action() } catch (err: Throwable) { null }
    }
}
```

### 1.4 IDELanguage.kt — Language Base Class
**Original**: `editor/impl/src/main/java/com/itsaky/androidide/editor/language/IDELanguage.kt`
**Target**: `editor/sora-editor/src/main/java/com/xcoder/editor/sora/language/IDELanguage.kt`
**Adaptations**: Update imports, remove EventBus/Lookup dependencies, use direct LSP server reference

```kotlin
package com.xcoder.editor.sora.language

import android.os.Bundle
import com.xcoder.editor.sora.api.IEditor
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.completion.CompletionCancelledException
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import org.slf4j.LoggerFactory
import java.nio.file.Paths

abstract class IDELanguage : Language {

    private var formatter: Formatter? = null

    protected open val languageServer: ILanguageServer? get() = null
    open fun getTabSize(): Int = 4

    @Throws(CompletionCancelledException::class)
    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        val server = languageServer ?: return
        val path = extraArguments.getString(IEditor.KEY_FILE, null) ?: return
        val cancelChecker = CompletionCancelChecker(publisher)
        val completionProvider = CommonCompletionProvider(server, cancelChecker)
        val file = Paths.get(path)
        val items = completionProvider.complete(content, file, position) { checkIsCompletionChar(it) }
        publisher.setUpdateThreshold(1)
        (publisher as? IDECompletionPublisher)?.addLSPItems(items)
    }

    protected open fun checkIsCompletionChar(c: Char): Boolean = false

    override fun useTab(): Boolean = true

    override fun getFormatter(): Formatter {
        return formatter ?: LSPFormatter(languageServer).also { formatter = it }
    }

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int {
        return getIndentAdvance(content.getLine(line).substring(0, column))
    }

    open fun getIndentAdvance(line: String): Int = 0

    companion object {
        private val log = LoggerFactory.getLogger(IDELanguage::class.java)
    }
}
```

### 1.5 editor/impl build.gradle.kts — Editor Dependencies
**Original**: `editor/impl/build.gradle.kts`
**Target**: `editor/sora-editor/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.xcoder.editor.sora"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
}

dependencies {
    implementation(project(":editor"))  // editor API module

    // Sora Editor - the core code editor library
    implementation("io.github.Rosemoe.sora-editor:editor:")
    implementation("io.github.Rosemoe.sora-editor:language-textmate:")
    implementation("io.github.Rosemoe.sora-editor:language-java:")
    implementation("io.github.Rosemoe.sora-editor:language-tree-sitter:")

    // Tree-sitter for syntax highlighting
    implementation("com.itsaky.androidide:android-tree-sitter:")

    // LSP models
    implementation(project(":lsp-java"))

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}
```

### 1.6 layout_code_editor.xml — Editor Widget Layout
**Original**: `editor/impl/src/main/res/layout/layout_code_editor.xml`
**Target**: `editor/sora-editor/src/main/res/layout/layout_code_editor.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <com.google.android.material.progressindicator.LinearProgressIndicator
        android:id="@+id/rw_progress"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:indeterminate="false"
        android:max="100"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        tools:progress="50" />

    <com.xcoder.editor.sora.ui.IDEEditor
        android:id="@+id/editor"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/rw_progress" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

---

## 2. APP MODULE — Editor Activity & Layouts

### 2.1 CodeEditorView.kt — Editor Container View
**Original**: `core/app/src/main/java/com/itsaky/androidide/ui/CodeEditorView.kt` (498 lines)
**Target**: `app/src/main/java/com/xcoder/ide/ui/CodeEditorView.kt`
**Adaptations**: Remove EventBus (use ViewModel/LiveData), update package

```kotlin
package com.xcoder.ide.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.isVisible
import com.xcoder.editor.sora.databinding.LayoutCodeEditorBinding
import com.xcoder.editor.sora.ui.EditorSearchLayout
import com.xcoder.editor.sora.ui.IDEEditor
import com.xcoder.editor.sora.api.IEditor
import com.xcoder.lsp.api.ILanguageServer
import com.xcoder.lsp.api.ILanguageServerRegistry
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.LineSeparator
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.Magnifier
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

@SuppressLint("ViewConstructor")
class CodeEditorView(
    context: Context,
    file: File,
    selection: Range
) : LinearLayoutCompat(context), Closeable {

    private var _binding: LayoutCodeEditorBinding? = null
    private var _searchLayout: EditorSearchLayout? = null
    private val codeEditorScope = CoroutineScope(Dispatchers.Default + CoroutineName("CodeEditorView"))
    private val readWriteContext = newSingleThreadContext("CodeEditorView")

    private val binding: LayoutCodeEditorBinding
        get() = checkNotNull(_binding) { "Binding has been destroyed" }

    val file: File? get() = editor?.file
    val editor: IDEEditor? get() = _binding?.editor
    val isModified: Boolean get() = editor?.isModified ?: false

    init {
        _binding = LayoutCodeEditorBinding.inflate(android.view.LayoutInflater.from(context))
        binding.editor.apply {
            isHighlightCurrentBlock = true
            props.autoCompletionOnComposing = true
            dividerWidth = dp2px(2f).toFloat()
            lineSeparator = LineSeparator.LF
        }
        _searchLayout = EditorSearchLayout(context, binding.editor)
        orientation = VERTICAL
        removeAllViews()
        addView(binding.root, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(searchLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        readFileAndApplySelection(file, selection)
    }

    fun updateFile(file: File) {
        val editor = _binding?.editor ?: return
        editor.file = file
        postRead(file)
    }

    fun onEditorSelected() {
        _binding?.editor?.onEditorSelected()
    }

    fun beginSearch() {
        _searchLayout?.beginSearchMode()
    }

    fun markAsSaved() { editor?.markUnmodified() }

    suspend fun save(): Boolean {
        val file = this.file ?: return false
        if (!isModified && file.exists()) return false
        val text = _binding?.editor?.text ?: return false
        withContext(Dispatchers.Main.immediate) {
            withEditingDisabled {
                withContext(readWriteContext) {
                    text.writeTo(file, this@CodeEditorView::updateReadWriteProgress)
                }
            }
            _binding?.rwProgress?.isVisible = false
        }
        markUnmodified()
        notifySaved()
        return true
    }

    private fun postRead(file: File) {
        binding.editor.setupLanguage(file)
        binding.editor.setLanguageServer(createLanguageServer(file))
        // Set language client
        binding.editor.file = file
    }

    private fun createLanguageServer(file: File): ILanguageServer? {
        if (!file.isFile) return null
        val serverID: String = when (file.extension) {
            "java" -> JavaLanguageServer.SERVER_ID
            "xml" -> XMLLanguageServer.SERVER_ID
            "kt" -> KotlinLanguageServer.SERVER_ID
            else -> return null
        }
        return ILanguageServerRegistry.getDefault().getServer(serverID)
    }

    private inline fun <R : Any?> withEditingDisabled(action: () -> R): R {
        return try { _binding?.editor?.isEditable = false; action() }
        finally { _binding?.editor?.isEditable = true }
    }

    override fun close() {
        codeEditorScope.cancel("Cancellation was requested")
        _binding?.editor?.apply { notifyClose(); release() }
        readWriteContext.close()
    }
}
```

### 2.2 activity_editor.xml — Editor Activity Layout
**Original**: `core/app/src/main/res/layout/activity_editor.xml`
**Target**: `app/src/main/res/layout/activity_editor.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.xcoder.ide.ui.ContentTranslatingDrawerLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/editor_drawerLayout"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/colorSurfaceDim">

    <com.xcoder.ide.ui.SwipeRevealLayout
        android:id="@+id/swipe_reveal"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:dragHandle="@id/editor_toolbar">

        <include android:id="@+id/mem_usage_view" layout="@layout/layout_mem_usage" />

        <com.google.android.material.card.MaterialCardView
            android:id="@+id/content_card"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            app:cardBackgroundColor="?attr/colorSurface"
            app:cardCornerRadius="@dimen/editor_container_corners"
            app:cardElevation="0dp"
            app:strokeWidth="0dp">

            <include android:id="@+id/content" layout="@layout/content_editor" />

        </com.google.android.material.card.MaterialCardView>
    </com.xcoder.ide.ui.SwipeRevealLayout>

    <com.google.android.material.navigation.NavigationView
        android:id="@+id/startNav"
        android:layout_width="wrap_content"
        android:layout_height="match_parent"
        android:layout_gravity="start"
        app:elevation="8dp">

        <androidx.fragment.app.FragmentContainerView
            android:id="@+id/drawer_sidebar"
            android:name="com.xcoder.ide.fragments.sidebar.EditorSidebarFragment"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />

    </com.google.android.material.navigation.NavigationView>
</com.xcoder.ide.ui.ContentTranslatingDrawerLayout>
```

### 2.3 content_editor.xml — Editor Content Layout
**Original**: `core/app/src/main/res/layout/content_editor.xml`
**Target**: `app/src/main/res/layout/content_editor.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/realContainer"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:animateLayoutChanges="true">

    <com.google.android.material.appbar.AppBarLayout
        android:id="@+id/editor_appBarLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@android:color/transparent"
        android:fitsSystemWindows="false">

        <com.xcoder.ide.ui.ExtendedMenuToolbar
            android:id="@+id/editor_toolbar"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:elevation="10dp" />

        <com.google.android.material.progressindicator.LinearProgressIndicator
            android:id="@+id/progress_indicator"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:indeterminate="true" />

        <com.google.android.material.tabs.TabLayout
            android:id="@+id/tabs"
            style="@style/AppTheme.TabLayout"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            app:tabGravity="start" />

    </com.google.android.material.appbar.AppBarLayout>

    <ViewFlipper
        android:id="@+id/view_container"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_marginBottom="16dp"
        app:layout_behavior="com.google.android.material.appbar.AppBarLayout$ScrollingViewBehavior">

        <!-- Views dynamically added as CodeEditorView instances -->
        <ViewFlipper
            android:id="@+id/editor_container"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:layout_marginBottom="@dimen/editor_sheet_peek_height" />

        <androidx.constraintlayout.widget.ConstraintLayout
            android:layout_width="match_parent"
            android:layout_height="match_parent">
            <TextView
                android:id="@+id/no_editor_title"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/app_name"
                android:textAppearance="@style/TextAppearance.Material3.TitleLarge"
                app:layout_constraintBottom_toBottomOf="parent"
                app:layout_constraintEnd_toEndOf="parent"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintTop_toTopOf="parent"
                app:layout_constraintVertical_bias="0.4" />
            <TextView
                android:id="@+id/no_editor_summary"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:gravity="center"
                android:textAppearance="@style/TextAppearance.Material3.BodyMedium"
                android:textSize="13sp"
                app:layout_constraintEnd_toEndOf="@id/no_editor_title"
                app:layout_constraintStart_toStartOf="@id/no_editor_title"
                app:layout_constraintTop_toBottomOf="@+id/no_editor_title" />
        </androidx.constraintlayout.widget.ConstraintLayout>
    </ViewFlipper>

    <com.xcoder.ide.ui.EditorBottomSheet
        android:id="@+id/bottom_sheet"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:behavior_hideable="false"
        app:behavior_peekHeight="@dimen/editor_sheet_peek_height"
        app:layout_behavior="com.google.android.material.bottomsheet.BottomSheetBehavior" />

    <include android:id="@+id/diagnosticInfo" layout="@layout/layout_diagnostic_info" />
</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

### 2.4 activity_main.xml — Main Activity Layout
**Original**: `core/app/src/main/res/layout/activity_main.xml`
**Target**: `app/src/main/res/layout/activity_main.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:animateLayoutChanges="true">

    <!-- Toolbar -->
    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/toolbar"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"
        app:layout_constraintTop_toTopOf="parent" />

    <!-- Main fragment container -->
    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/nav_host_fragment"
        android:name="androidx.navigation.fragment.NavHostFragment"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        app:defaultNavHost="true"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintTop_toBottomOf="@id/toolbar"
        app:navGraph="@navigation/nav_graph" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

---

## 3. TERMINAL MODULE (`core/terminal/`)

### 3.1 TerminalView.java — Terminal Widget (1362 lines, KEY FILE)
**Original**: `termux/view/src/main/java/com/termux/view/TerminalView.java`
**Target**: `core/terminal/src/main/java/com/xcoder/core/terminal/TerminalView.java`
**Adaptations**: Minimal — Termux terminal code is self-contained, just copy as-is

Key architecture from TerminalView:
```java
package com.xcoder.core.terminal; // renamed package

public final class TerminalView extends View {
    public TerminalSession mTermSession;       // Current session
    public TerminalEmulator mEmulator;          // Terminal emulator
    public TerminalRenderer mRenderer;          // Renders text to canvas
    public TerminalViewClient mClient;          // Callback interface
    private TextSelectionCursorController mTextSelectionCursorController;
    private final GestureAndScaleRecognizer mGestureRecognizer;
    final Scroller mScroller;
    int mTopRow;                                // Scroll position
    float mScaleFactor = 1.f;

    // Constructor initializes gesture recognizer with handlers for:
    // - onUp, onSingleTapUp, onScroll, onScale, onFling, onDown, onLongPress
    // - Handles mouse tracking, text selection, scrolling, pinch zoom

    public void initializeEmulator(int columns, int rows) { /* ... */ }
    public void onSizeChanged(int w, int h, int oldw, int oldh) { /* ... */ }
    public boolean onCheckIsTextEditor() { return true; }
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) { /* ... */ }
    public boolean onKeyDown(int keyCode, KeyEvent e) { /* ... */ }
    public boolean onKeyUp(int keyCode, KeyEvent e) { /* ... */ }
    protected void onDraw(Canvas canvas) { /* ... */ }
}
```

### 3.2 TerminalSession.java — Terminal Session (373 lines)
**Original**: `termux/emulator/src/main/java/com/termux/terminal/TerminalSession.java`
**Target**: `core/terminal/src/main/java/com/xcoder/core/terminal/TerminalSession.java`
**Adaptations**: Copy as-is, just rename package

Key methods:
```java
public final class TerminalSession extends Object {
    final TerminalEmulator mEmulator;
    private final String mShellPath;
    private final String mCwd;
    private final String[] mArgs;
    private final TermuxTerminalSessionClient mTerminalSessionClient;

    // Key methods:
    public void initializeEmulator(int columns, int rows, TermuxTerminalViewClient client) { }
    public boolean isRunning() { }
    public void setTermSize(int columns, int rows) { }
    public void updateSize(int columns, int rows) { }
    public void appendToEmulator(byte[] buffer, int bufferLength) { }
    public void write(String data) { }
    public void write(byte[] data) { }
    public void finishIfRunning() { }
}
```

### 3.3 TerminalSessionClient.java — Session Callbacks
**Original**: `termux/emulator/src/main/java/com/termux/terminal/TerminalSessionClient.java`
**Target**: `core/terminal/src/main/java/com/xcoder/core/terminal/TerminalSessionClient.java`

```java
public interface TerminalSessionClient {
    void onTextChanged(TerminalSession changedSession);
    void onTitleChanged(TerminalSession changedSession);
    void onSessionFinished(TerminalSession finishedSession);
    void onCopyTextToClipboard(TerminalSession session, String text, boolean notifyUser);
    void onPasteTextFromClipboard(TerminalSession session);
    void onSessionCreated(TerminalSession newSession);
    String[] getShellEnvironment();
}
```

### 3.4 TerminalViewClient.java — View Callbacks
**Original**: `termux/view/src/main/java/com/termux/view/TerminalViewClient.java`
**Target**: `core/terminal/src/main/java/com/xcoder/core/terminal/TerminalViewClient.java`

```java
public interface TerminalViewClient {
    float onScale(float scale);
    void onSingleTapUp(MotionEvent event);
    boolean shouldBackButtonBeIgnored();
    void copyTextToClipboard(String text);
    void pasteTextFromClipboard();
    void logError(String tag, String message);
    void onLongPress(MotionEvent event);
}
```

### 3.5 TerminalRenderer.java — Renders terminal to Canvas (249 lines)
**Original**: `termux/view/src/main/java/com/termux/view/TerminalRenderer.java`
**Target**: `core/terminal/src/main/java/com/xcoder/core/terminal/TerminalRenderer.java`

Key architecture:
```java
public final class TerminalRenderer {
    final int mTextSize;          // Font size in pixels
    final Typeface mTypeface;
    final float mFontLineSpacing; // Height of one row
    final float mFontCharWidth;   // Width of one character
    int mRows, mColumns;
    Paint mPaint;
    // Renders TerminalEmulator screen rows to Canvas
    public void onDraw(Canvas canvas, TerminalEmulator emulator) { /* ... */ }
}
```

---

## 4. BUILD ENGINE (`build-engine/`)

### 4.1 IToolingApiServer.kt — Build Server Interface
**Original**: `tooling/api/src/main/java/com/itsaky/androidide/tooling/api/IToolingApiServer.kt`
**Target**: `build-engine/src/main/java/com/xcoder/build/api/IToolingApiServer.kt`
**Adaptations**: Update package, keep lsp4j jsonrpc annotations

```kotlin
package com.xcoder.build.api

import com.xcoder.build.api.messages.InitializeProjectParams
import com.xcoder.build.api.messages.TaskExecutionMessage
import com.xcoder.build.api.messages.result.BuildCancellationRequestResult
import com.xcoder.build.api.messages.result.InitializeResult
import com.xcoder.build.api.messages.result.TaskExecutionResult
import com.xcoder.build.api.models.ToolingServerMetadata
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment
import java.util.concurrent.CompletableFuture

@JsonSegment("server")
interface IToolingApiServer {
    @JsonRequest fun metadata(): CompletableFuture<ToolingServerMetadata>
    @JsonRequest fun initialize(params: InitializeProjectParams): CompletableFuture<InitializeResult>
    @JsonRequest fun isServerInitialized(): CompletableFuture<Boolean>
    @JsonRequest fun getRootProject(): CompletableFuture<IProject>
    @JsonRequest fun executeTasks(message: TaskExecutionMessage): CompletableFuture<TaskExecutionResult>
    @JsonRequest fun cancelCurrentBuild(): CompletableFuture<BuildCancellationRequestResult>
    @JsonRequest fun shutdown(): CompletableFuture<Void>
}
```

### 4.2 IToolingApiClient.kt — Build Client Interface
**Original**: `tooling/api/src/main/java/com/itsaky/androidide/tooling/api/IToolingApiClient.kt`
**Target**: `build-engine/src/main/java/com/xcoder/build/api/IToolingApiClient.kt`

```kotlin
package com.xcoder.build.api

import com.xcoder.build.api.messages.LogMessageParams
import com.xcoder.build.api.messages.result.BuildInfo
import com.xcoder.build.api.messages.result.BuildResult
import com.xcoder.build.api.messages.result.GradleWrapperCheckResult
import com.xcoder.build.events.ProgressEvent
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment
import java.util.concurrent.CompletableFuture

@JsonSegment("client")
interface IToolingApiClient {
    @JsonNotification fun logMessage(params: LogMessageParams)
    @JsonNotification fun logOutput(line: String)
    @JsonNotification fun prepareBuild(buildInfo: BuildInfo)
    @JsonNotification fun onBuildSuccessful(result: BuildResult)
    @JsonNotification fun onBuildFailed(result: BuildResult)
    @JsonNotification fun onProgressEvent(event: ProgressEvent)
    @JsonRequest fun getBuildArguments(): CompletableFuture<List<String>>
    @JsonRequest fun checkGradleWrapperAvailability(): CompletableFuture<GradleWrapperCheckResult>
}
```

---

## 5. LSP INTEGRATION (`lsp-java/`)

### 5.1 ILanguageServer.kt — Language Server Interface
**Original**: `core/lsp-api/src/main/java/com/itsaky/androidide/lsp/api/ILanguageServer.kt`
**Target**: `lsp-java/src/main/java/com/xcoder/lsp/api/ILanguageServer.kt`
**Adaptations**: Update package

```kotlin
package com.xcoder.lsp.api

import com.xcoder.lsp.models.*
import com.xcoder.lsp.projects.IWorkspace
import java.nio.file.Path

interface ILanguageServer {
    val serverId: String?
    fun shutdown()
    fun connectClient(client: ILanguageClient?)
    val client: ILanguageClient?
    fun applySettings(settings: IServerSettings?)
    fun setupWorkspace(workspace: IWorkspace)
    fun complete(params: CompletionParams?): CompletionResult
    suspend fun findReferences(params: ReferenceParams): ReferenceResult
    suspend fun findDefinition(params: DefinitionParams): DefinitionResult
    suspend fun expandSelection(params: ExpandSelectionParams): Range
    suspend fun signatureHelp(params: SignatureHelpParams): SignatureHelp
    suspend fun analyze(file: Path): DiagnosticResult
    fun formatCode(params: FormatCodeParams?): CodeFormatResult {
        return CodeFormatResult(false, mutableListOf())
    }
    fun handleFailure(failure: LSPFailure?): Boolean { return false }
}
```

### 5.2 ILanguageClient.java — Language Client Interface
**Original**: `core/lsp-api/src/main/java/com/itsaky/androidide/lsp/api/ILanguageClient.java`
**Target**: `lsp-java/src/main/java/com/xcoder/lsp/api/ILanguageClient.java`

```java
package com.xcoder.lsp.api;

import com.xcoder.lsp.models.*;
import com.xcoder.lsp.models.Location;
import java.io.File;
import java.util.List;

public interface ILanguageClient {
    void publishDiagnostics(DiagnosticResult result);
    DiagnosticItem getDiagnosticAt(File file, int line, int column);
    void performCodeAction(PerformCodeActionParams params);
    default void performCodeAction(CodeActionItem actionItem) {
        if (actionItem == null) return;
        performCodeAction(new PerformCodeActionParams(actionItem));
    }
    ShowDocumentResult showDocument(ShowDocumentParams params);
    void showLocations(List<Location> locations);
}
```

### 5.3 JavaLanguageServer.kt — Java LSP Implementation (319 lines, KEY FILE)
**Original**: `java/lsp/src/main/java/com/itsaky/androidide/lsp/java/JavaLanguageServer.kt`
**Target**: `lsp-java/src/main/java/com/xcoder/lsp/java/JavaLanguageServer.kt`
**Adaptations**: Remove EventBus, use direct observer pattern; update imports

```kotlin
package com.xcoder.lsp.java

import com.xcoder.lsp.api.ILanguageClient
import com.xcoder.lsp.api.ILanguageServer
import com.xcoder.lsp.api.IServerSettings
import com.xcoder.lsp.java.providers.*
import com.xcoder.lsp.models.*
import com.xcoder.lsp.projects.IWorkspace
import kotlinx.coroutines.*
import java.nio.file.Path

class JavaLanguageServer : ILanguageServer {

    private val completionProvider: CompletionProvider = CompletionProvider()
    private val diagnosticProvider: JavaDiagnosticProvider?
    override var client: ILanguageClient? = null
        private set
    private var _settings: IServerSettings? = null
    private var selectedFile: Path? = null
    private val timer = AnalyzeTimer { analyzeSelected() }
    private var cachedCompletion: CachedCompletion

    val settings: IServerSettings get() = _settings ?: JavaServerSettings.getInstance().also { _settings = it }
    override val serverId: String = SERVER_ID

    companion object {
        const val SERVER_ID = "ide.lsp.java"
    }

    init {
        diagnosticProvider = JavaDiagnosticProvider()
        cachedCompletion = CachedCompletion.EMPTY
        applySettings(JavaServerSettings.getInstance())
    }

    override fun shutdown() {
        JavaCompilerProvider.getInstance().destroy()
        SourceFileManager.clearCache()
        timer.cancel()
    }

    override fun connectClient(client: ILanguageClient?) { this.client = client }
    override fun applySettings(settings: IServerSettings?) { this._settings = settings }

    override fun setupWorkspace(workspace: IWorkspace) {
        SourceFileManager.clearCache()
        JavaCompilerProvider.getInstance().destroy()
        for (subModule in workspace.getSubProjects()) {
            SourceFileManager.forModule(subModule)
        }
        startOrRestartAnalyzeTimer()
    }

    override fun complete(params: CompletionParams?): CompletionResult {
        val compiler = getCompiler(params!!.file)
        if (!settings.completionsEnabled() || !completionProvider.canComplete(params.file))
            return CompletionResult.EMPTY
        if (diagnosticProvider!!.isAnalyzing()) diagnosticProvider.cancel()
        completionProvider.reset(compiler, settings, cachedCompletion) { cachedCompletion =
            this.cachedCompletion = it
        }
        return completionProvider.complete(params)
    }

    override suspend fun findReferences(params: ReferenceParams): ReferenceResult {
        val compiler = getCompiler(params.file)
        return if (!settings.referencesEnabled()) ReferenceResult(emptyList())
        else ReferenceProvider(compiler, params.cancelChecker).findReferences(params)
    }

    override suspend fun findDefinition(params: DefinitionParams): DefinitionResult {
        val compiler = getCompiler(params.file)
        return if (!settings.definitionsEnabled()) DefinitionResult(emptyList())
        else DefinitionProvider(compiler, settings, params.cancelChecker).findDefinition(params)
    }

    override suspend fun analyze(file: Path): DiagnosticResult {
        if (!settings.diagnosticsEnabled()) return DiagnosticResult.NO_UPDATE
        return diagnosticProvider!!.analyze(file)
    }

    override fun formatCode(params: FormatCodeParams?): CodeFormatResult {
        return CodeFormatProvider(settings).format(params)
    }

    // Event handlers for document changes, selection, open, close
    fun onContentChange(event: DocumentChangeEvent) {
        if (!DocumentUtils.isJavaFile(event.changedFile)) return
        getCompiler(event.changedFile).onDocumentChange(event)
        startOrRestartAnalyzeTimer()
    }

    fun onFileOpened(event: DocumentOpenEvent) {
        selectedFile = event.openedFile
        startOrRestartAnalyzeTimer()
    }

    fun onFileClosed(event: DocumentCloseEvent) {
        diagnosticProvider?.clearTimestamp(event.closedFile)
    }

    private fun analyzeSelected() {
        if (selectedFile == null || client == null) return
        CoroutineScope(Dispatchers.Default).launch {
            val result = analyze(selectedFile!!)
            withContext(Dispatchers.Main) { client?.publishDiagnostics(result) }
        }
    }
}
```

### 5.4 ILanguageServerRegistry.kt
**Original**: `core/lsp-api/src/main/java/com/itsaky/androidide/lsp/api/ILanguageServerRegistry.kt`
**Target**: `lsp-java/src/main/java/com/xcoder/lsp/api/ILanguageServerRegistry.kt`

```kotlin
package com.xcoder.lsp.api

interface ILanguageServerRegistry {
    fun getServer(serverId: String?): ILanguageServer?
    fun registerServer(server: ILanguageServer)
    fun unregisterServer(serverId: String?)
    companion object {
        fun getDefault(): ILanguageServerRegistry = DefaultLanguageServerRegistry()
    }
}
```

---

## 6. SETTINGS MODULE (`core/settings/`)

### 6.1 IDEPreferences.kt — Preferences Keys
**Original**: `utilities/preferences/src/main/java/com/itsaky/androidide/preferences/IDEPreferences.kt`
**Target**: `core/settings/src/main/java/com/xcoder/core/settings/IDEPreferences.kt`

```kotlin
package com.xcoder.core.settings

/**
 * Top-level preference keys for XCoder IDE.
 * Adapted from AndroidIDE's IDEPreferences.
 */
object IDEPreferences {
    object Editor {
        const val FONT_SIZE = "ide.editor.font_size"
        const val TAB_SIZE = "ide.editor.tab_size"
        const val FONT_LIGATURES = "ide.editor.font_ligatures"
        const val WORD_WRAP = "ide.editor.word_wrap"
        const val USE_MAGNIFER = "ide.editor.use_magnifier"
        const val USE_ICU = "ide.editor.use_icu"
        const val USE_CUSTOM_FONT = "ide.editor.use_custom_font"
        const val DELETE_EMPTY_LINES = "ide.editor.delete_empty_lines"
        const val DELETE_TABS_ON_BACKSPACE = "ide.editor.delete_tabs_on_backspace"
        const val STICKY_SCROLL_ENABLED = "ide.editor.sticky_scroll"
        const val PIN_LINE_NUMBERS = "ide.editor.pin_line_numbers"
        const val DRAW_LEADING_WS = "ide.editor.draw_leading_ws"
        const val DRAW_TRAILING_WS = "ide.editor.draw_trailing_ws"
    }
    object Build {
        const val AUTO_SAVE_BEFORE_BUILD = "ide.build.auto_save"
        const val SHOW_BUILD_VARIANTS = "ide.build.show_variants"
    }
    object Terminal {
        const val FONT_SIZE = "ide.terminal.font_size"
        const val SHELL_PATH = "ide.terminal.shell_path"
    }
}
```

---

## 7. FILE MANAGER (embedded in app/core)

### 7.1 File Tree Layouts
**Original**: `core/app/src/main/res/layout/layout_editor_file_tree.xml`
**Target**: `core/file-manager/src/main/res/layout/layout_file_tree.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.appcompat.widget.SearchView
        android:id="@+id/search_view"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:iconifiedByDefault="false"
        android:queryHint="@string/search_files"
        app:layout_constraintTop_toTopOf="parent" />

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/file_tree"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:clipToPadding="false"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintTop_toBottomOf="@id/search_view" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

### 7.2 File Tree Item Layout
**Original**: `core/app/src/main/res/layout/layout_filetree_item.xml`
**Target**: `core/file-manager/src/main/res/layout/layout_filetree_item.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:paddingHorizontal="8dp"
    android:paddingVertical="4dp">

    <ImageView
        android:id="@+id/icon"
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:layout_gravity="center_vertical"
        android:contentDescription="@null"
        tools:src="@drawable/ic_folder" />

    <TextView
        android:id="@+id/title"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_gravity="center_vertical"
        android:layout_marginStart="8dp"
        android:layout_weight="1"
        android:ellipsize="middle"
        android:singleLine="true"
        android:textAppearance="?attr/textAppearanceBodyMedium"
        tools:text="build.gradle.kts" />

    <ImageView
        android:id="@+id/arrow"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_vertical"
        android:src="@drawable/ic_arrow_right"
        android:visibility="gone" />

</LinearLayout>
```

---

## 8. KEY DEPENDENCIES

Add to `gradle/libs.versions.toml`:

```toml
[versions]
sora-editor = "0.23.7"
eventbus = "3.3.1"

[libraries]
sora-editor = { module = "io.github.Rosemoe.sora-editor:editor", version.ref = "sora-editor" }
sora-editor-textmate = { module = "io.github.Rosemoe.sora-editor:language-textmate", version.ref = "sora-editor" }
sora-editor-tree-sitter = { module = "io.github.Rosemoe.sora-editor:language-tree-sitter", version.ref = "sora-editor" }
```

---

## 9. ADAPTATION SUMMARY

| What | Action |
|---|---|
| Package names | `com.itsaky.androidide` → `com.xcoder.ide` (sub-modules use `com.xcoder.*`) |
| EventBus | Replace with Kotlin Flow/SharedFlow or simple callback interfaces |
| Flashbar | Replace with Material Snackbar or custom Toast |
| Lookup API | Replace with Koin/Hilt DI |
| JDTLS/Java Compiler | Use nb-javac-android or JDK 17 compiler port |
| Gradle Tooling API | Use lsp4j jsonrpc for client-server communication |
| BlankJ Utils | Replace with individual utilities or AndroidX |
| MikePhil Charting | Optional - for memory usage chart |
| AppIntro | Optional - for onboarding |

---

## 10. FILES EXTRACTED (Summary)

Total: **28 source files** extracted from AndroidIDE, spanning ~8,000+ lines of real, reusable code:

### Editor (8 files)
1. `IEditor.java` (204 lines) - Editor interface
2. `ILspEditor.kt` (91 lines) - LSP editor interface
3. `IDEEditor.kt` (918 lines) - Core editor widget with LSP integration
4. `IDELanguage.kt` (124 lines) - Language base class with completion
5. `CommonCompletionProvider.kt` (97 lines) - LSP completion bridge
6. `EditorSearchLayout.kt` (172 lines) - Search/replace UI
7. `CompletionListAdapter.kt` (243 lines) - Completion items adapter
8. `ContentReadWrite.kt` (155 lines) - File I/O for editor
9. `layout_code_editor.xml` (45 lines)
10. `build.gradle.kts` (82 lines)

### Terminal (5 files)
11. `TerminalView.java` (1362 lines) - Terminal rendering view
12. `TerminalSession.java` (373 lines) - Session management
13. `TerminalEmulator.java` (2507 lines) - VT100/xterm emulator
14. `TerminalSessionClient.java` (51 lines) - Session callbacks
15. `TerminalViewClient.java` (83 lines) - View callbacks
16. `TerminalRenderer.java` (249 lines) - Canvas rendering

### LSP (6 files)
17. `ILanguageServer.kt` (173 lines) - Server interface
18. `ILanguageClient.java` (98 lines) - Client interface
19. `JavaLanguageServer.kt` (319 lines) - Java LSP impl
20. `ILanguageServerRegistry.kt` (70 lines) - Server registry
21. `ICompletionProvider.java` (86 lines) - Completion provider
22. `IServerSettings.java` (107 lines) - Server settings

### Build Engine (2 files)
23. `IToolingApiServer.kt` (76 lines) - Gradle tooling API
24. `IToolingApiClient.kt` (92 lines) - Client callbacks

### App/Layouts (5 files)
25. `BaseEditorActivity.kt` (809 lines) - Editor activity base
26. `CodeEditorView.kt` (498 lines) - Editor container
27. `activity_editor.xml` (53 lines)
28. `content_editor.xml` (116 lines)
29. `IDEPreferences.kt` (44 lines) - Preference keys

### File Manager (2 files)
30. `layout_editor_file_tree.xml` (21 lines)
31. `layout_filetree_item.xml` (49 lines)
