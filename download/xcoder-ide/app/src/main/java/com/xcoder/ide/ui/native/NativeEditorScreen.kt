package com.xcoder.ide.ui.native

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xcoder.ide.theme.*
import com.xcoder.ide.ui.common.EditorTab
import com.xcoder.ide.ui.common.EditorTabs
import com.xcoder.ide.ui.common.FileTreeNode
import com.xcoder.ide.ui.common.FileTree

/**
 * Native Android development editor screen.
 *
 * Layout:
 * - Left: File tree sidebar
 * - Top: Tab bar
 * - Center: Code editor (Compose-based syntax highlighting)
 * - Bottom: Collapsible build panel with log output
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeEditorScreen(
    fileUri: Uri? = null,
    modifier: Modifier = Modifier
) {
    var sidebarVisible by remember { mutableStateOf(true) }
    var buildPanelExpanded by remember { mutableStateOf(false) }
    var selectedFilePath by remember { mutableStateOf("/project/MainActivity.kt") }

    // Sample project tree for a native Android project
    val projectNodes = remember {
        listOf(
            FileTreeNode("app", "/project/app", isDirectory = true, children = listOf(
                FileTreeNode("src", "/project/app/src", isDirectory = true, children = listOf(
                    FileTreeNode("main", "/project/app/src/main", isDirectory = true, children = listOf(
                        FileTreeNode("java", "/project/app/src/main/java", isDirectory = true, children = listOf(
                            FileTreeNode("com", "/project/app/src/main/java/com", isDirectory = true, children = listOf(
                                FileTreeNode("xcoder", "/project/app/src/main/java/com/xcoder", isDirectory = true, children = listOf(
                                    FileTreeNode("MainActivity.kt", "/project/app/src/main/java/com/xcoder/MainActivity.kt", isDirectory = false, extension = "kt"),
                                    FileTreeNode("App.kt", "/project/app/src/main/java/com/xcoder/App.kt", isDirectory = false, extension = "kt")
                                ))
                            ))
                        )),
                        FileTreeNode("res", "/project/app/src/main/res", isDirectory = true, children = listOf(
                            FileTreeNode("layout", "/project/app/src/main/res/layout", isDirectory = true, children = listOf(
                                FileTreeNode("activity_main.xml", "/project/app/src/main/res/layout/activity_main.xml", isDirectory = false, extension = "xml")
                            )),
                            FileTreeNode("values", "/project/app/src/main/res/values", isDirectory = true, children = listOf(
                                FileTreeNode("strings.xml", "/project/app/src/main/res/values/strings.xml", isDirectory = false, extension = "xml"),
                                FileTreeNode("colors.xml", "/project/app/src/main/res/values/colors.xml", isDirectory = false, extension = "xml")
                            ))
                        )),
                        FileTreeNode("AndroidManifest.xml", "/project/app/src/main/AndroidManifest.xml", isDirectory = false, extension = "xml")
                    ))
                )),
                FileTreeNode("build.gradle.kts", "/project/app/build.gradle.kts", isDirectory = false, extension = "kts")
            )),
            FileTreeNode("build.gradle.kts", "/project/build.gradle.kts", isDirectory = false, extension = "kts"),
            FileTreeNode("settings.gradle.kts", "/project/settings.gradle.kts", isDirectory = false, extension = "kts")
        )
    }

    // Open tabs
    var openTabs by remember {
        mutableStateOf(
            listOf(
                EditorTab(id = "/project/app/src/main/java/com/xcoder/MainActivity.kt", title = "MainActivity.kt", isActive = true, isModified = true),
                EditorTab(id = "/project/app/build.gradle.kts", title = "build.gradle.kts", isActive = false)
            )
        )
    }

    // Build log
    val buildLog = remember {
        mutableStateOf(listOf(
            "[INFO] Starting Gradle Daemon…",
            "[INFO] Configuring project :app",
            "[INFO] Task :app:compileDebugKotlin",
            "[WARN] 'unused' parameter in doSomething()",
            "[INFO] BUILD SUCCESSFUL in 12s"
        ))
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- File tree sidebar ---
        AnimatedVisibility(
            visible = sidebarVisible,
            enter = horizontalSlideIn(initialOffsetX = { -it }) + fadeIn(),
            exit = horizontalSlideOut(targetOffsetX = { -it }) + fadeOut()
        ) {
            Surface(
                modifier = Modifier.width(260.dp).fillMaxHeight(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                shadowElevation = 4.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Project", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { sidebarVisible = false }) {
                            Icon(Icons.Default.ChevronLeft, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider()
                    FileTree(
                        rootNodes = projectNodes,
                        onFileClick = { node ->
                            selectedFilePath = node.path
                            openTabs = openTabs.map { it.copy(isActive = it.id == node.path) }.let { current ->
                                if (current.none { it.id == node.path }) {
                                    current + EditorTab(id = node.path, title = node.name, isActive = true, extension = node.extension)
                                } else current
                            }
                        },
                        selectedPath = selectedFilePath
                    )
                }
            }
        }

        // --- Main area ---
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // Toolbar
            Row(
                modifier = Modifier.fillMaxWidth().height(36.dp).background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!sidebarVisible) {
                    IconButton(onClick = { sidebarVisible = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Menu, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    selectedFilePath?.substringAfterLast("/") ?: "Untitled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                // Build button
                FilledTonalButton(
                    onClick = { buildLog.value = buildLog.value + "[INFO] Build triggered at ${System.currentTimeMillis()}" },
                    modifier = Modifier.height(26.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Build", style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { buildPanelExpanded = !buildPanelExpanded }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (buildPanelExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        null,
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tabs
            EditorTabs(
                tabs = openTabs,
                onTabSelected = { tab ->
                    selectedFilePath = tab.id
                    openTabs = openTabs.map { it.copy(isActive = it.id == tab.id) }
                },
                onTabClosed = { tab ->
                    openTabs = openTabs
                        .filter { it.id != tab.id }
                        .let { updated ->
                            if (updated.isEmpty()) {
                                listOf(EditorTab(id = "untitled", title = "Untitled", isActive = true))
                            } else if (tab.isActive && updated.isNotEmpty()) {
                                updated.mapIndexed { i, t -> t.copy(isActive = i == updated.lastIndex) }
                            } else updated
                        }
                }
            )

            HorizontalDivider()

            // Code editor area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                NativeCodeContent(selectedFilePath)
            }

            // Build panel
            AnimatedVisibility(
                visible = buildPanelExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Build, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(6.dp))
                            Text("Build Output", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { buildLog.value = emptyList() }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Clear, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 4.dp)) {
                            items(buildLog.value) { line ->
                                val color = when {
                                    line.startsWith("[ERR") || line.startsWith("[FAIL") -> RedError
                                    line.startsWith("[WARN") -> OrangeWarning
                                    line.contains("SUCCESSFUL") -> GreenSuccess
                                    else -> TextSecondaryDark
                                }
                                Text(
                                    text = line,
                                    style = CodeTypography.terminal,
                                    color = color,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            // Status bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Native Editor", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.weight(1f))
                    Text("Kotlin  |  Android  |  UTF-8", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

/**
 * Renders a simulated Kotlin source file with inline syntax highlighting.
 */
@Composable
private fun NativeCodeContent(filePath: String) {
    val lines = if (filePath.endsWith(".kt")) {
        listOf(
            "package com.xcoder.app" to null,
            "" to null,
            "import android.os.Bundle" to null,
            "import androidx.appcompat.app.AppCompatActivity" to null,
            "" to null,
            "class MainActivity : AppCompatActivity() {" to null,
            "" to null,
            "    private lateinit var viewModel: MainViewModel" to null,
            "" to null,
            "    override fun onCreate(savedInstanceState: Bundle?) {" to null,
            "        super.onCreate(savedInstanceState)" to null,
            "        setContentView(R.layout.activity_main)" to null,
            "" to null,
            "        viewModel = ViewModelProvider(this)[MainViewModel::class.java]" to null,
            "        observeState()" to null,
            "    }" to null,
            "" to null,
            "    private fun observeState() {" to null,
            "        viewModel.uiState.collect { state ->" to null,
            "            when (state) {" to null,
            "                is UiState.Loading -> showLoading()" to null,
            "                is UiState.Success -> showContent(state.data)" to null,
            "                is UiState.Error -> showError(state.message)" to null,
            "            }" to null,
            "        }" to null,
            "    }" to null,
            "}" to null,
        )
    } else {
        listOf(
            "// ${filePath.substringAfterLast("/")}" to null,
            "// Open this file to see its contents." to null
        )
    }

    val lineCount = lines.size
    val digits = lineCount.toString().length

    Row {
        // Line numbers
        Column {
            lines.forEachIndexed { index, _ ->
                val num = (index + 1).toString().padStart(digits)
                Text(
                    text = num,
                    style = CodeTypography.lineNumbers,
                    color = TextSecondaryDark.copy(alpha = 0.5f)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        // Code
        Column {
            lines.forEach { (line, _) ->
                val styled = highlightKotlin(line)
                Text(
                    text = styled,
                    style = CodeTypography.editorBody,
                    softWrap = false
                )
            }
        }
    }
}

/** Very small Kotlin keyword highlighter for demonstration. */
private fun highlightKotlin(line: String) = buildAnnotatedString {
    val keywords = setOf("package", "import", "class", "object", "interface", "fun", "val", "var", "private", "protected", "public", "internal", "override", "lateinit", "when", "is", "if", "else", "return", "this", "super", "companion", "data", "sealed", "enum", "typealias", "inline", "reified", "suspend", "by", "init")

    // Tokenize by splitting on word boundaries while keeping separators.
    val tokenRegex = Regex("(\\w+|[^\\w\\s]+|\\s+)")
    val matches = tokenRegex.findAll(line)

    for (match in matches) {
        val token = match.value
        val start = match.range.first
        append(token)
        val end = length
        when {
            token in keywords -> addStyle(SpanStyle(color = SyntaxKeyword, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), start, end)
            token.startsWith("//") -> addStyle(SpanStyle(color = SyntaxComment, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), start, end)
            token.matches(Regex("\\d+")) -> addStyle(SpanStyle(color = SyntaxNumber), start, end)
            token == "(" || token == ")" || token == "{" || token == "}" -> addStyle(SpanStyle(color = SyntaxOperator), start, end)
            token.firstOrNull()?.isUpperCase() == true && token.length > 1 && token.all { it.isLetter() || it == '.' } -> addStyle(SpanStyle(color = SyntaxType), start, end)
        }
    }
}
