package com.xcoder.editor.native.syntax

import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry

/**
 * Kotlin syntax highlighting using TextMate grammar.
 */
object KotlinLanguage {
    
    fun create(): TextMateLanguage {
        val themeRegistry = ThemeRegistry()
        themeRegistry.loadTheme(
            "dark",
            """{
                "name": "XCoder Dark",
                "settings": [
                    {"scope": "keyword", "settings": {"foreground": "#C678DD"}},
                    {"scope": "string", "settings": {"foreground": "#98C379"}},
                    {"scope": "comment", "settings": {"foreground": "#5C6370", "fontStyle": "italic"}},
                    {"scope": "variable", "settings": {"foreground": "#E06C75"}},
                    {"scope": "support.function", "settings": {"foreground": "#61AFEF"}},
                    {"scope": "entity.name.function", "settings": {"foreground": "#61AFEF"}},
                    {"scope": "entity.name.type", "settings": {"foreground": "#E5C07B"}},
                    {"scope": "constant", "settings": {"foreground": "#D19A66"}},
                    {"scope": "number", "settings": {"foreground": "#D19A66"}}
                ]
            }""".trimIndent()
        )
        
        val grammarRegistry = GrammarRegistry()
        // Load Kotlin grammar from bundled TextMate grammar JSON
        val grammar = grammarRegistry.loadGrammar("source.kotlin")
        
        return TextMateLanguage(
            themeRegistry,
            grammarRegistry,
            grammar,
            true
        )
    }
}
