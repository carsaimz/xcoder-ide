package com.xcoder.core.terminal

/**
 * Defines the extra keys map (key name → key code/string) for the terminal.
 *
 * Based on Termux's `TermuxTerminalExtraKeys` which provides a configurable
 * row of special keys displayed above the soft keyboard. These keys are
 * essential on Android devices that lack a physical keyboard.
 *
 * ## Termux's Extra Keys Architecture
 *
 * Termux stores extra keys configuration in `~/.termux/termux.properties`
 * as a `extra-keys` property with a nested list format:
 * ```
 * extra-keys = [['ESC','/','-','HOME','UP','END','PGUP'],['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]
 * ```
 *
 * Each key name maps to a value in this map. The value can be:
 * - A **special key**: `"TAB"`, `"ESC"`, `"ENTER"`, `"UP"`, `"DOWN"`, etc.
 * - A **control sequence**: `"CTRL C"`, `"CTRL D"`, `"CTRL Z"`, etc.
 * - A **literal character**: `"|"`, `"-"`, `"/"`, `" \\"`, etc.
 *
 * ## XCoder IDE's Default Layout
 *
 * We provide two rows of keys optimized for an IDE terminal:
 * - **Row 1**: ESC, /, -, HOME, UP, END, PGUP
 * - **Row 2**: TAB, CTRL, ALT, LEFT, DOWN, RIGHT, PGDN
 *
 * This matches Termux's default layout and is familiar to Termux users.
 */
object TerminalExtraKeys {

    // ── Default key layout rows ──────────────────────────────────────────

    /**
     * The default extra keys layout.
     *
     * Two rows of keys matching Termux's default configuration.
     * Each inner list represents a row in the toolbar.
     */
    val DEFAULT_LAYOUT: List<List<String>> = listOf(
        listOf("ESC", "/", "-", "HOME", "UP", "END", "PGUP"),
        listOf("TAB", "CTRL", "ALT", "LEFT", "DOWN", "RIGHT", "PGDN"),
    )

    /**
     * A compact layout with fewer keys for smaller screens.
     */
    val COMPACT_LAYOUT: List<List<String>> = listOf(
        listOf("ESC", "TAB", "CTRL", "UP", "DOWN"),
        listOf("/", "-", "|", "LEFT", "RIGHT"),
    )

    /**
     * A full layout with all available keys for large screens / landscape.
     */
    val FULL_LAYOUT: List<List<String>> = listOf(
        listOf(
            "ESC", "F1", "F2", "F3", "F4", "F5",
            "-", "/", "|", "HOME", "UP", "END", "PGUP"
        ),
        listOf(
            "TAB", "CTRL", "ALT", "F6", "F7", "F8", "F9",
            "LEFT", "DOWN", "RIGHT", "ENTER", "BACKSPACE", "PGDN"
        ),
    )

    /**
     * A development-focused layout with common coding keys.
     */
    val DEVELOPER_LAYOUT: List<List<String>> = listOf(
        listOf("ESC", "TAB", "CTRL", "ALT", "-", "/", "|"),
        listOf("HOME", "UP", "END", "PGUP", "LEFT", "DOWN", "RIGHT", "PGDN"),
    )

    // ── Key map: key name → key value ───────────────────────────────────

    /**
     * Maps key names to their terminal values.
     *
     * Based on Termux's key mapping in `TermuxTerminalExtraKeys`:
     * - Special keys (TAB, ESC, arrows) are mapped to their escape sequences
     * - Control keys (CTRL C, CTRL D) are mapped for processing by [TerminalViewClient]
     * - Literal characters are mapped directly
     *
     * This map is consumed by [TerminalViewClient.onExtraKeyButtonPress].
     */
    val DEFAULT_EXTRA_KEYS: Map<String, String> = mapOf(
        // ── Navigation keys ──────────────────────────────────────────────
        "UP" to "UP",
        "DOWN" to "DOWN",
        "LEFT" to "LEFT",
        "RIGHT" to "RIGHT",
        "HOME" to "HOME",
        "END" to "END",
        "PGUP" to "PGUP",
        "PGDN" to "PGDN",
        "INSERT" to "INSERT",
        "DELETE" to "DELETE",

        // ── Modifier / special keys ─────────────────────────────────────
        "TAB" to "TAB",
        "ESC" to "ESC",
        "ESCAPE" to "ESC",
        "ENTER" to "ENTER",
        "RETURN" to "ENTER",
        "SPACE" to "SPACE",
        "BACKSPACE" to "BACKSPACE",

        // ── Function keys ───────────────────────────────────────────────
        "F1" to "F1",
        "F2" to "F2",
        "F3" to "F3",
        "F4" to "F4",
        "F5" to "F5",
        "F6" to "F6",
        "F7" to "F7",
        "F8" to "F8",
        "F9" to "F9",
        "F10" to "F10",
        "F11" to "F11",
        "F12" to "F12",

        // ── Control key shortcuts (most commonly used in terminal) ──────
        // These are processed by TerminalViewClient.sendControlKey()
        "CTRL" to "CTRL",           // CTRL is a toggle key, not a direct key
        "ALT" to "ALT",             // ALT is a toggle key
        "CTRL C" to "CTRL C",       // SIGINT - interrupt current command
        "CTRL D" to "CTRL D",       // EOF / exit shell
        "CTRL Z" to "CTRL Z",       // SIGTSTP - suspend current command
        "CTRL L" to "CTRL L",       // Clear screen
        "CTRL A" to "CTRL A",       // Move to beginning of line
        "CTRL E" to "CTRL E",       // Move to end of line
        "CTRL U" to "CTRL U",       // Kill line (clear to start)
        "CTRL K" to "CTRL K",       // Kill to end of line
        "CTRL W" to "CTRL W",       // Delete word before cursor
        "CTRL R" to "CTRL R",       // Reverse search (Ctrl+R)
        "CTRL S" to "CTRL S",       // Forward search
        "CTRL P" to "CTRL P",       // Previous command in history
        "CTRL N" to "CTRL N",       // Next command in history
        "CTRL H" to "CTRL H",       // Backspace (alternative)
        "CTRL J" to "CTRL J",       // Newline
        "CTRL M" to "CTRL M",       // Carriage return (Enter)

        // ── Literal characters commonly used in terminal commands ───────
        "-" to "-",
        "/" to "/",
        "\\" to "\\",
        "|" to "|",
        "`" to "`",
        "$" to "$",
        "@" to "@",
        "#" to "#",
        "~" to "~",
        "%" to "%",
        "^" to "^",
        "&" to "&",
        "*" to "*",
        "(" to "(",
        ")" to ")",
        "[" to "[",
        "]" to "]",
        "{" to "{",
        "}" to "}",
        "<" to "<",
        ">" to ">",
        "!" to "!",
        "_" to "_",
        "=" to "=",
        "+" to "+",
        ":" to ":",
        ";" to ";",
        "\"" to "\"",
        "'" to "'",
        "," to ",",
        "." to ".",
        "?" to "?",
    )

    // ── Display labels ──────────────────────────────────────────────────

    /**
     * Maps key names to their display labels for the toolbar buttons.
     *
     * Some keys have shorter display names to fit the toolbar better.
     * Based on Termux's display label mapping.
     */
    val KEY_DISPLAY_LABELS: Map<String, String> = mapOf(
        "ESCAPE" to "ESC",
        "RETURN" to "↵",
        "BACKSPACE" to "⌫",
        "SPACE" to "SPC",
        "DELETE" to "DEL",
        "PGUP" to "PgUp",
        "PGDN" to "PgDn",
        "INSERT" to "Ins",
        "ENTER" to "↵",
        "UP" to "↑",
        "DOWN" to "↓",
        "LEFT" to "←",
        "RIGHT" to "→",
        "HOME" to "⇤",
        "END" to "⇥",
        "CTRL" to "CTRL",
        "ALT" to "ALT",
    )

    /**
     * Get the display label for a key name.
     * Falls back to the key name itself if no mapping exists.
     */
    fun getDisplayLabel(keyName: String): String {
        return KEY_DISPLAY_LABELS[keyName] ?: keyName
    }

    /**
     * Parse a layout string (from user preferences) into a list of key rows.
     *
     * Termux stores the layout as a nested list in properties files.
     * This method parses that format back into a structured layout.
     *
     * Expected format: `[['ESC','/','UP'],['TAB','CTRL','DOWN']]`
     *
     * @param layoutString The layout string to parse.
     * @return Parsed list of key rows, or the default layout on parse error.
     */
    fun parseLayout(layoutString: String): List<List<String>> {
        return try {
            // Simple parsing: strip outer brackets, split by "],["
            val stripped = layoutString
                .trim()
                .removePrefix("[")
                .removeSuffix("]")
            val rows = stripped.split("]
          ,[
          ".toRegex())
            rows.map { row ->
                row
                    .removePrefix("[")
                    .removeSuffix("]")
                    .split(",")
                    .map { key ->
                        // Strip quotes and whitespace
                        key.trim().removeSurrounding("'").removeSurrounding("\"")
                    }
                    .filter { it.isNotEmpty() }
            }.filter { it.isNotEmpty() }
        } catch (e: Exception) {
            DEFAULT_LAYOUT
        }
    }

    /**
     * Serialize a layout to string format (for persistence).
     */
    fun serializeLayout(layout: List<List<String>>): String {
        val rows = layout.joinToString(",") { row ->
            "[${row.joinToString(",") { "'$it'" }}]"
        }
        return "[$rows]"
    }
}
