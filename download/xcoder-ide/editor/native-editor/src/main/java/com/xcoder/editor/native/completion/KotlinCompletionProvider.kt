package com.xcoder.editor.native.completion

import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference

/**
 * Basic auto-completion provider for Kotlin with common keywords,
 * Android API, and standard library functions.
 */
@Suppress("unused")
class KotlinCompletionProvider {

    fun getCompletions(prefix: String, position: CharPosition): List<CompletionItem> {
        if (prefix.isBlank()) return emptyList()
        val lower = prefix.lowercase()
        return ALL_ITEMS.filter { it.label.lowercase().startsWith(lower) || it.desc.lowercase().contains(lower) }
    }

    companion object {
        private val KEYWORDS = listOf(
            "fun", "val", "var", "class", "object", "interface", "enum", "when",
            "if", "else", "for", "while", "do", "return", "break", "continue",
            "try", "catch", "finally", "throw", "import", "package", "as", "is",
            "in", "typealias", "sealed", "data", "abstract", "open", "override",
            "private", "protected", "internal", "public", "companion", "init",
            "constructor", "suspend", "inline", "reified", "crossinline", "noinline",
            "tailrec", "operator", "infix", "lateinit", "by", "super", "this"
        ).map { CompletionItem(it, it, "Kotlin keyword") }

        private val ANDROID_APIS = listOf(
            CompletionItem("findViewById", "findViewById<T>(id: Int): T?", "Android View"),
            CompletionItem("setContentView", "setContentView(layoutResId: Int)", "Activity"),
            CompletionItem("getIntent", "getIntent(): Intent", "Activity"),
            CompletionItem("startActivity", "startActivity(intent: Intent)", "Context"),
            CompletionItem("Toast.makeText", "Toast.makeText(context, text, duration)", "Android Widget"),
            CompletionItem("Log.d", "Log.d(tag: String, msg: String)", "Android Util"),
            CompletionItem("Log.e", "Log.e(tag: String, msg: String)", "Android Util"),
            CompletionItem("ViewModel", "ViewModel", "Lifecycle"),
            CompletionItem("LiveData", "LiveData<T>", "Lifecycle"),
            CompletionItem("StateFlow", "StateFlow<T>", "Coroutines"),
            CompletionItem("MutableStateFlow", "MutableStateFlow<T>", "Coroutines"),
            CompletionItem("LaunchedEffect", "LaunchedEffect(key1) { }", "Compose"),
            CompletionItem("remember", "remember { }", "Compose"),
            CompletionItem("mutableStateOf", "mutableStateOf(value)", "Compose"),
            CompletionItem("derivedStateOf", "derivedStateOf { }", "Compose"),
            CompletionItem("Surface", "Surface(modifier, color, content)", "Compose Material3"),
            CompletionItem("Box", "Box(modifier, content)", "Compose Layout"),
            CompletionItem("Column", "Column(modifier, content)", "Compose Layout"),
            CompletionItem("Row", "Row(modifier, content)", "Compose Layout"),
            CompletionItem("LazyColumn", "LazyColumn(modifier, content)", "Compose Layout"),
            CompletionItem("Text", "Text(text, modifier, style)", "Compose Material3"),
            CompletionItem("Button", "Button(onClick, content)", "Compose Material3"),
            CompletionItem("OutlinedTextField", "OutlinedTextField(value, onValueChange)", "Compose Material3")
        )

        private val STD_LIB = listOf(
            CompletionItem("println", "println(message: Any?)", "Kotlin stdlib"),
            CompletionItem("listOf", "listOf<T>(vararg elements: T): List<T>", "Kotlin stdlib"),
            CompletionItem("mutableListOf", "mutableListOf<T>(): MutableList<T>", "Kotlin stdlib"),
            CompletionItem("mapOf", "mapOf<K, V>(vararg pairs: Pair<K, V>)", "Kotlin stdlib"),
            CompletionItem("setOf", "setOf<T>(vararg elements: T): Set<T>", "Kotlin stdlib"),
            CompletionItem("arrayListOf", "arrayListOf<T>(): ArrayList<T>", "Kotlin stdlib"),
            CompletionItem("HashMap", "HashMap<K, V>()", "Kotlin stdlib"),
            CompletionItem("readLine", "readLine(): String?", "Kotlin stdlib"),
            CompletionItem("lazy", "lazy { initializer }", "Kotlin stdlib"),
            CompletionItem("run", "run { block }", "Kotlin stdlib"),
            CompletionItem("let", "value.let { block }", "Kotlin stdlib"),
            CompletionItem("apply", "value.apply { block }", "Kotlin stdlib"),
            CompletionItem("also", "value.also { block }", "Kotlin stdlib"),
            CompletionItem("with", "with(receiver) { block }", "Kotlin stdlib"),
            CompletionItem("repeat", "repeat(times) { index -> }", "Kotlin stdlib")
        )

        val ALL_ITEMS = KEYWORDS + ANDROID_APIS + STD_LIB
    }
}
