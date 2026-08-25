package com.xcoder.ide.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.provides
import androidx.compose.ui.platform.*
import androidx.core.view.*

// ============================================================================
//  Composition Local — access IDE-specific colors from any composable
// ============================================================================

/** Provides the full [IdeColors] palette for the current theme. */
val LocalIdeColors: CompositionLocal<IdeColors> = staticCompositionLocalOf {
    error("IdeColors not provided. Wrap content in XCoderTheme.")
}

// ============================================================================
//  Theme Mode
// ============================================================================

/** Supported theme variants for XCoder IDE. */
enum class ThemeMode {
 /** Follow the system setting (dark if system dark). */
    SYSTEM,
 /** Force light color scheme. */
    LIGHT,
 /** Force dark color scheme (deep blue-black). */
    DARK,
 /** AMOLED black — pure black backgrounds for OLED displays. */
    AMOLED
}

// ============================================================================
//  XCoder Theme
// ============================================================================

/**
 * XCoder IDE Material 3 theme.
 *
 * This is the single entry point for all Compose theming.
 *
 * @param darkTheme When `true`, the dark colour scheme is used.
 *   Defaults to following the system setting but can be overridden from Settings.
 * @param themeMode Explicit theme mode selection. Takes priority over [darkTheme]
 *   when not [ThemeMode.SYSTEM].
 * @param dynamicColour On Android 12+ (API 31+) this pulls colours from the
 *   user's wallpaper. **Disabled by default** so the hand-crafted XCoder
 *   palette is always used — matching the AndroidIDE approach of preferring
 *   deterministic theming for a code editor.
 * @param content The Composable content tree.
 */
@Composable
fun XCoderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColour: Boolean = false,
    content: @Composable () -> Unit
) {
    // Determine whether to use dark mode based on themeMode or fallback.
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> darkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AMOLED -> true
    }

    // Select the IdeColors palette.
    val ideColors: IdeColors = when (themeMode) {
        ThemeMode.SYSTEM, ThemeMode.LIGHT -> if (isDark) XCoderDarkColors else XCoderLightColors
        ThemeMode.DARK -> XCoderDarkColors
        ThemeMode.AMOLED -> XCoderAmoledColors
    }

    // Build the M3 color scheme.
    val colorScheme = when {
        dynamicColour && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> darkColorScheme(ideColors)
        else -> lightColorScheme(ideColors)
    }

    // Sync the status bar / navigation bar colours with the theme.
    // Pattern from AndroidIDE's BaseEditorActivity#setupWindowDecor().
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = ideColors.toolbarBackground.toArgb()
            window.navigationBarColor = ideColors.statusBarBackground.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(LocalIdeColors provides ideColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = XCoderTypography,
            content = content
        )
    }
}

/** Convenience accessor: `val colors = LocalIdeColors.current` */
object XCoderTheme {
    val colors: IdeColors
        @Composable get() = LocalIdeColors.current

    val typography: Typography
        @Composable get() = MaterialTheme.typography

    val isDark: Boolean
        @Composable get() = MaterialTheme.colorScheme.background.luminance() < 0.5f
}

// ============================================================================
//  M3 Color Scheme Builders
// ============================================================================

@Composable
private fun lightColorScheme(c: IdeColors): ColorScheme = lightColorScheme(
    primary = c.primary,
    onPrimary = c.onPrimary,
    primaryContainer = c.primaryContainer,
    onPrimaryContainer = c.onPrimaryContainer,
    secondary = Color(0xFF7C7CDB),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4E3FF),
    onSecondaryContainer = Color(0xFF2D2A6E),
    tertiary = Color(0xFF3D8B7A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD0F0E8),
    onTertiaryContainer = Color(0xFF003730),
    error = c.error,
    onError = Color(0xFFFFFFFF),
    errorContainer = c.errorBackground,
    onErrorContainer = c.error,
    background = c.background,
    onBackground = c.onBackground,
    surface = c.surface,
    onSurface = c.onSurface,
    surfaceVariant = c.surfaceVariant,
    onSurfaceVariant = c.onSurfaceVariant,
    surfaceContainer = c.surfaceContainer,
    surfaceContainerHigh = c.surfaceContainerHigh,
    surfaceContainerHighest = c.surfaceContainerHighest,
    outline = c.outline,
    outlineVariant = c.outlineVariant,
    inverseSurface = c.inverseSurface,
    inverseOnSurface = c.inverseOnSurface,
    inversePrimary = c.inversePrimary
)

@Composable
private fun darkColorScheme(c: IdeColors): ColorScheme = darkColorScheme(
    primary = c.primaryLight,
    onPrimary = c.onPrimary,
    primaryContainer = c.primaryContainer,
    onPrimaryContainer = c.onPrimaryContainer,
    secondary = Color(0xFF9B97E0),
    onSecondary = Color(0xFF1B1B2F),
    secondaryContainer = Color(0xFF38336B),
    onSecondaryContainer = Color(0xFFC5C1F0),
    tertiary = Color(0xFF6BC4B0),
    onTertiary = Color(0xFF003730),
    tertiaryContainer = Color(0xFF005044),
    onTertiaryContainer = Color(0xFF8FE8D0),
    error = c.error,
    onError = c.onSurface,
    errorContainer = c.errorBackground,
    onErrorContainer = c.error,
    background = c.background,
    onBackground = c.onBackground,
    surface = c.surface,
    onSurface = c.onSurface,
    surfaceVariant = c.surfaceVariant,
    onSurfaceVariant = c.onSurfaceVariant,
    surfaceContainer = c.surfaceContainer,
    surfaceContainerHigh = c.surfaceContainerHigh,
    surfaceContainerHighest = c.surfaceContainerHighest,
    outline = c.outline,
    outlineVariant = c.outlineVariant,
    inverseSurface = c.inverseSurface,
    inverseOnSurface = c.inverseOnSurface,
    inversePrimary = c.inversePrimary
)

// ============================================================================
//  AMOLED Color Palette
// ============================================================================

/**
 * AMOLED black palette — uses pure `#000000` backgrounds for OLED displays.
 *
 * Based on AndroidIDE's approach of offering a true-black dark theme.
 * The editor and terminal backgrounds use `#000000` instead of the
 * standard dark theme's `#1E1E2E`, saving power on OLED screens.
 *
 * Syntax highlighting uses slightly brighter variants for contrast
 * against the pure black background.
 */
val XCoderAmoledColors = XCoderDarkColors.copy(
    // Pure black backgrounds for AMOLED.
    background = Color(0xFF000000),
    onBackground = Color(0xFFE4E4F0),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE4E4F0),
    surfaceVariant = Color(0xFF121212),
    surfaceElevated = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF080808),
    surfaceContainerHigh = Color(0xFF121212),
    surfaceContainerHighest = Color(0xFF1A1A1A),
    outline = Color(0xFF333333),
    outlineVariant = Color(0xFF222222),
    scrollbarTrack = Color(0xFF0A0A0A),
    scrollbarThumb = Color(0xFF333333),

    // Studio panels
    studioBackground = Color(0xFF000000),
    studioSurface = Color(0xFF000000),
    studioSurfaceElevated = Color(0xFF0A0A0A),
    studioSurfaceDim = Color(0xFF0A0A0A),

    // Toolbar
    toolbarBackground = Color(0xFF000000),
    toolbarDivider = Color(0xFF1A1A1A),

    // Tab bar
    tabBarBackground = Color(0xFF000000),
    tabActiveBackground = Color(0xFF000000),
    tabInactiveBackground = Color(0xFF000000),

    // Sidebar
    sidebarBackground = Color(0xFF000000),
    sidebarHeaderBackground = Color(0xFF0A0A0A),
    sidebarItemHover = Color(0xFF0A0A0A),
    sidebarItemSelected = Color(0xFF1A1A2E),
    sidebarDivider = Color(0xFF1A1A1A),

    // Status bar
    statusBarBackground = Color(0xFF000000),

    // Breadcrumb
    breadcrumbBackground = Color(0xFF000000),

    // Minimap
    minimapBackground = Color(0xFF000000),

    // Editor — pure black for AMOLED.
    editorBackground = Color(0xFF000000),
    editorGutterBackground = Color(0xFF000000),
    editorCurrentLineHighlight = Color(0xFF0F0F0F),
    editorSelectionBackground = Color(0xFF333355),
    editorIndentGuide = Color(0xFF1A1A1A),
    editorIndentGuideActive = Color(0xFF333333),
    editorWhitespace = Color(0xFF1A1A1A),

    // Syntax — brighter for contrast on pure black.
    syntaxKeyword = Color(0xFFD4A0FF),
    syntaxString = Color(0xFFD4F0A0),
    syntaxNumber = Color(0xFFFFA080),
    syntaxComment = Color(0xFF666680),
    syntaxFunction = Color(0xFF90BBFF),
    syntaxVariable = Color(0xFFF0F0FF),
    syntaxType = Color(0xFFFFD080),
    syntaxOperator = Color(0xFF90EEFF),
    syntaxAnnotation = Color(0xFFD4A0FF),
    syntaxTag = Color(0xFFFF8090),

    // Search
    searchPanelBackground = Color(0xFF000000),
    searchPanelBorder = Color(0xFF1A1A1A),
    searchInputBackground = Color(0xFF0A0A0A),

    // Terminal — also pure black.
    terminalBackground = Color(0xFF000000),

    // Diff
    diffAddedBackground = Color(0xFF0A1A10),
    diffRemovedBackground = Color(0xFF1A0A0A),
    diffModifiedBackground = Color(0xFF1A1A0A),
    diffHeaderBackground = Color(0xFF0A0A1A),

    // Semantic backgrounds
    successBackground = Color(0xFF0A1A10),
    warningBackground = Color(0xFF1A1A0A),
    errorBackground = Color(0xFF1A0A0A),
    infoBackground = Color(0xFF0A0A1A),

    // Dialog
    dialogBackground = Color(0xFF0A0A0A),
    dialogScrim = Color(0xFF000000),
    bottomSheetBackground = Color(0xFF0A0A0A),

    // Chat
    chatAiBubble = Color(0xFF0A0A0A),
    chatInputBackground = Color(0xFF0A0A0A),
    chatInputBorder = Color(0xFF1A1A1A),
    chatCodeBlockBackground = Color(0xFF000000),
    chatCodeBlockBorder = Color(0xFF1A1A1A),

    // Progress
    progressTrack = Color(0xFF1A1A1A),

    // Notification
    notificationBackground = Color(0xFF1A1A1A)
)

// ============================================================================
//  Syntax Highlighting Theme (exported for sora-editor configuration)
// ============================================================================

/**
 * Syntax highlighting colors for the current theme.
 *
 * This is a convenience object to bridge Compose [IdeColors] syntax
 * tokens to the sora-editor styling configuration
 * configuration used by AndroidIDE's `EditorColorScheme`.
 *
 * Usage in the editor module:
 * ```
 * val colors = LocalIdeColors.current
 * editor.colorScheme.apply {
 *     setColor(EditorColorScheme.WHOLE_BACKGROUND, colors.editorBackground)
 *     setColor(EditorColorScheme.LINE_NUMBER, colors.editorLineNumber)
 *     // ...
 * }
 * ```
 */
object SyntaxHighlightingTheme {

    /** Map of TextMate scope names to Compose [Color].
     *  Used to configure the sora-editor TextMate color registry. */
    @Composable
    fun scopeColors(): Map<String, Color> {
        val c = LocalIdeColors.current
        return mapOf(
            "keyword" to c.syntaxKeyword,
            "string" to c.syntaxString,
            "constant.numeric" to c.syntaxNumber,
            "comment" to c.syntaxComment,
            "comment.block.documentation" to c.syntaxCommentDoc,
            "entity.name.function" to c.syntaxFunction,
            "variable" to c.syntaxVariable,
            "entity.name.type" to c.syntaxType,
            "keyword.operator" to c.syntaxOperator,
            "storage.type.annotation" to c.syntaxAnnotation,
            "constant" to c.syntaxConstant,
            "entity.name.tag" to c.syntaxTag,
            "entity.other.attribute-name" to c.syntaxAttributeName,
            "string.unquoted.attrvalue" to c.syntaxAttributeValue,
            "punctuation.delimiter" to c.syntaxDelimiter,
            "source" to c.syntaxIdentifier
        )
    }

    /** Map of editor UI color slots. */
    @Composable
    fun editorUiColors(): Map<String, Color> {
        val c = LocalIdeColors.current
        return mapOf(
            "background" to c.editorBackground,
            "gutter" to c.editorGutterBackground,
            "lineNumber" to c.editorLineNumber,
            "lineNumberActive" to c.editorLineNumberActive,
            "currentLine" to c.editorCurrentLineHighlight,
            "selection" to c.editorSelectionBackground,
            "selectionForeground" to c.editorSelectionForeground,
            "caret" to c.editorCaret,
            "indentGuide" to c.editorIndentGuide,
            "indentGuideActive" to c.editorIndentGuideActive,
            "bracketMatch" to c.editorBracketMatching,
            "folding" to c.editorFoldingIcon,
            "foldingHover" to c.editorFoldingHover,
            "whitespace" to c.editorWhitespace,
            "link" to c.editorLink
        )
    }

    /** Terminal ANSI color map for Termux TerminalEmulator. */
    @Composable
    fun terminalColors(): Map<Int, Color> {
        val c = LocalIdeColors.current
        return mapOf(
            0 to c.terminalBlack,
            1 to c.terminalRed,
            2 to c.terminalGreen,
            3 to c.terminalYellow,
            4 to c.terminalBlue,
            5 to c.terminalMagenta,
            6 to c.terminalCyan,
            7 to c.terminalWhite,
            8 to c.terminalBrightBlack,
            9 to c.terminalBrightRed,
            10 to c.terminalBrightGreen,
            11 to c.terminalBrightYellow,
            12 to c.terminalBrightBlue,
            13 to c.terminalBrightMagenta,
            14 to c.terminalBrightCyan,
            15 to c.terminalBrightWhite
        )
    }
}
