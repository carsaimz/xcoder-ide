/*
 * Ace Editor Kotlin Language Mode
 * A functional Kotlin syntax highlighter for Ace Editor
 */
define("ace/mode/kotlin", ["require", "exports", "module", "ace/lib/oop", "ace/mode/text", "ace/mode/text_highlight_rules"], function(require, exports, module) {
    "use strict";

    var oop = require("ace/lib/oop");
    var TextMode = require("ace/mode/text").Mode;
    var TextHighlightRules = require("ace/mode/text_highlight_rules").TextHighlightRules;

    var KotlinHighlightRules = function() {
        // Regex helper for string escape sequences
        var escapeRe = "\\\\(?:[btnfr\\"'\\$]|\\u[0-9a-fA-F]{4}|\\$[\\w]+)";

        // Kotlin keywords
        var keywords = (
            "abstract|annotation|as|break|by|catch|class|companion|const|constructor|continue|
            crossinline|data|do|dynamic|else|enum|expect|external|finally|final|for|
            fun|get|if|import|in|infix|init|inline|inner|interface|internal|is|
            it|lateinit|noinline|object|open|operator|out|override|package|param|
            private|protected|public|reified|return|sealed|set|super|suspend|tailrec|
            this|throw|try|typealias|typeof|val|var|vararg|when|where|while|yield"
        );

        // Kotlin built-in types
        var buildinTypes = (
            "Boolean|Byte|Char|Double|Float|Int|Long|Short|String|Unit|Nothing|Any|
            Array|List|Map|Set|MutableList|MutableMap|MutableSet|Sequence|Pair|Triple|
            IntArray|LongArray|FloatArray|DoubleArray|ByteArray|ShortArray|CharArray|BooleanArray"
        );

        // Kotlin constants/literals
        var constants = (
            "true|false|null|this|super"
        );

        // Language-level annotations
        var annotations = (
            "@[a-zA-Z_][a-zA-Z0-9_]*"
        );

        // Package and import
        var declarations = (
            "package|import"
        );

        this.$rules = {
            "start": [
                // Single-line comments
                { token: "comment.line.double-slash.kotlin", regex: "\/\/.*$" },
                // Multi-line comments
                { token: "comment.block.kotlin", regex: "\/\\*", next: "comment" },
                // Strings - double quoted
                { token: "string.quoted.double.kotlin", regex: '"', next: "dstring" },
                // Strings - single quoted (char)
                { token: "string.quoted.single.kotlin", regex: "'", next: "sstring" },
                // Raw / multi-line strings
                { token: "string.quoted.triple.kotlin", regex: '"""', next: "triplestring" },
                // Annotations
                { token: "meta.annotation.kotlin", regex: annotations },
                // Numbers: hex
                { token: "constant.numeric.hex.kotlin", regex: "0[xX][0-9a-fA-F_]+[lLuU]*" },
                // Numbers: binary
                { token: "constant.numeric.binary.kotlin", regex: "0[bB][01_]+[lLuU]*" },
                // Numbers: float
                { token: "constant.numeric.float.kotlin", regex: "[0-9_]+\\.(?:[0-9_]+)?(?:[eE][+-]?[0-9_]+)?[fFdD]?" },
                // Numbers: long integer with suffix
                { token: "constant.numeric.integer.kotlin", regex: "[0-9_]+[lLuU]" },
                // Numbers: integer
                { token: "constant.numeric.integer.kotlin", regex: "[0-9_]+" },
                // Keywords
                { token: "keyword.control.kotlin", regex: "\\b(?:" + keywords + ")\\b" },
                // Built-in types
                { token: "support.type.kotlin", regex: "\\b(?:" + buildinTypes + ")\\b" },
                // Constants
                { token: "constant.language.kotlin", regex: "\\b(?:" + constants + ")\\b" },
                // Function definition (after fun keyword)
                { token: "keyword.declaration.function.kotlin", regex: "\\bfun\\b", next: "fundef" },
                // Class definition
                { token: "keyword.declaration.class.kotlin", regex: "\\b(?:class|interface|object|enum)\\b", next: "classdef" },
                // Variable declaration
                { token: "keyword.declaration.variable.kotlin", regex: "\\b(?:val|var)\\b" },
                // Package/import
                { token: "keyword.other.package.kotlin", regex: "\\b(?:" + declarations + ")\\b" },
                // String template - dollar identifier
                { token: "variable.language.template.kotlin", regex: "\\$\\w+" },
                // String template - dollar expression start
                { token: "punctuation.definition.template.begin.kotlin", regex: "\\${", push: "template" },
                // Identifiers
                { token: "identifier.kotlin", regex: "[a-zA-Z_$][a-zA-Z0-9_$]*" },
                // Operators
                { token: "keyword.operator.kotlin", regex: "+|-|\\*|\/|%|={1,2}|!=|===|!==|<|>|<=|>=|&&|\\|\\||!|::|\?\?|\?\.|\.\.|->|=>|@|\?|:\\:" },
                // Punctuation
                { token: "punctuation.terminator.kotlin", regex: ";" },
                { token: "punctuation.accessor.kotlin", regex: "\\." },
                { token: "punctuation.separator.kotlin", regex: "," },
                { token: "paren.lparen", regex: "[\\[{(]" },
                { token: "paren.rparen", regex: "[\\])}]" }
            ],

            "comment": [
                { token: "comment.block.kotlin", regex: ".*?\\*\/", next: "start" },
                { token: "comment.block.kotlin", regex: ".+" }
            ],

            "dstring": [
                { token: "constant.language.escape.kotlin", regex: escapeRe },
                { token: "variable.language.template.kotlin", regex: "\\$\\w+" },
                { token: "punctuation.definition.template.begin.kotlin", regex: "\\${", push: "template" },
                { token: "string.quoted.double.kotlin", regex: '"', next: "pop" },
                { token: "string.quoted.double.kotlin", regex: "." }
            ],

            "sstring": [
                { token: "constant.language.escape.kotlin", regex: escapeRe },
                { token: "string.quoted.single.kotlin", regex: "'", next: "pop" },
                { token: "invalid.illegal.character.kotlin", regex: "." }
            ],

            "triplestring": [
                { token: "constant.language.escape.kotlin", regex: escapeRe },
                { token: 