package com.xcoder.ide.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
//  XCoder IDE Compose Color Tokens
//
//  Naming mirrors the XML palette (xcoder_*, studio_*, editor_*, etc.) so that
//  XML-view and Compose-view always reference the same semantic slot.
//
//  Light tokens are defined directly; dark tokens live in [XCoderDarkColors].
//  Use [ideColors] composition-local to read the active palette.
// ============================================================================

// ---------------------------------------------------------------------------
//  Brand — XCoder Purple (Kelivo)
// ---------------------------------------------------------------------------
/** Primary brand purple. */
val PurplePrimary = Color(0xFF6B5CE7)
/** Lighter tint — hover states, badges. */
val PurplePrimaryLight = Color(0xFF8B84FF)
/** Darker shade — pressed states, deep accents. */
val PurplePrimaryDark = Color(0xFF4A3FBF)

// ---------------------------------------------------------------------------
//  File Type Colors (shared light/dark — high-contrast on both backgrounds)
// ---------------------------------------------------------------------------
object FileColors {
    val Kotlin = Color(0xFFB56DFF)
    val Java = Color(0xFFF18A54)
    val Xml = Color(0xFF6ACB8F)
    val Json = Color(0xFFE5C84F)
    val Gradle = Color(0xFF6DD4A0)
    val KotlinScript = Color(0xFFB56DFF)
    val C = Color(0xFF4FC1FF)
    val Cpp = Color(0xFF4FC1FF)
    val CHeader = Color(0xFF4FC1FF)
    val Python = Color(0xFF4B8BBE)
    val JavaScript = Color(0xFFF7DF1E)
    val JavaScriptDark = Color(0xFFFFF176) // brighter for dark bg
    val TypeScript = Color(0xFF3178C6)
    val Html = Color(0xFFE44D26)
    val Css = Color(0xFF264DE4)
    val Scss = Color(0xFFCC6699)
    val Shell = Color(0xFF89E051)
    val Markdown = Color(0xFF519ABA)
    val Yaml = Color(0xFFCB171E)
    val Toml = Color(0xFF9C4121)
    val Sql = Color(0xFFE38C00)
    val Dart = Color(0xFF00B4AB)
    val Rust = Color(0xFFCE422B)
    val Go = Color(0xFF00ADD8)
    val Swift = Color(0xFFFA7343)
    val Image = Color(0xFF9B59B6)
    val Font = Color(0xFFB0B0C4)
    val Archive = Color(0xFFF18A54)
    val Pdf = Color(0xFFE44D26)
    val Properties = Color(0xFF8B8BA0)
    val Proguard = Color(0xFFE44D26)
    val Manifest = Color(0xFF6ACB8F)
    val Generic = Color(0xFF8B8BA0)
    val Folder = Color(0xFF6B5CE7)
    val FolderClosed = Color(0xFF8B84FF)
}

// ---------------------------------------------------------------------------
//  Git Semantic Colors (shared light/dark)
// ---------------------------------------------------------------------------
object GitColors {
    val Added = Color(0xFF00C48C)
    val Modified = Color(0xFF5CA0FF)
    val Deleted = Color(0xFFFF6B6B)
    val Renamed = Color(0xFFB56DFF)
    val Untracked = Color(0xFF8B8BA0)
    val Conflict = Color(0xFFFFB547)
    val Ignored = Color(0xFFBDBDCD)
    val Branch = Color(0xFFB56DFF)
    val Tag = Color(0xFF6ACB8F)
    val Remote = Color(0xFF5CA0FF)

    // Dark-specific overrides (slightly more vibrant)
    object Dark {
        val Added = Color(0xFFA6E3A1)
        val Modified = Color(0xFF89B4FA)
        val Deleted = Color(0xFFF38BA8)
        val Renamed = Color(0xFFC792EA)
        val Untracked = Color(0xFF6E6E86)
        val Conflict = Color(0xFFF9E2AF)
        val Ignored = Color(0xFF484860)
        val Branch = Color(0xFFC792EA)
        val Tag = Color(0xFFA6E3A1)
        val Remote = Color(0xFF89B4FA)
    }
}

// ---------------------------------------------------------------------------
//  IDE Color Palette — data class for each theme variant
// ---------------------------------------------------------------------------

/**
 * Full set of IDE-specific color tokens.
 * Each field is a semantic slot that changes between light and dark themes.
 */
data class IdeColors(
    // ---- Brand ----
    val primary: Color,
    val primaryLight: Color,
    val primaryDark: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val primaryHover: Color,
    val primaryPressed: Color,

    // ---- Background & Surface ----
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceElevated: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val outline: Color,
    val outlineVariant: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val inversePrimary: Color,
    val scrollbarThumb: Color,
    val scrollbarTrack: Color,

    // ---- Text ----
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val textPlaceholder: Color,
    val textLink: Color,

    // ---- Studio Panels ----
    val studioBackground: Color,
    val studioSurface: Color,
    val studioSurfaceElevated: Color,
    val studioSurfaceDim: Color,

    // Toolbar
    val toolbarBackground: Color,
    val toolbarDivider: Color,
    val toolbarTitle: Color,

    // Tab Bar
    val tabBarBackground: Color,
    val tabActiveBackground: Color,
    val tabInactiveBackground: Color,
    val tabActiveIndicator: Color,
    val tabActiveText: Color,
    val tabInactiveText: Color,
    val tabCloseIcon: Color,
    val tabModifiedIndicator: Color,

    // Sidebar
    val sidebarBackground: Color,
    val sidebarHeaderBackground: Color,
    val sidebarItemHover: Color,
    val sidebarItemSelected: Color,
    val sidebarItemSelectedText: Color,
    val sidebarIcon: Color,
    val sidebarIconActive: Color,
    val sidebarDivider: Color,
    val sidebarSectionHeader: Color,

    // Status Bar
    val statusBarBackground: Color,
    val statusBarText: Color,
    val statusBarIcon: Color,
    val statusBarError: Color,
    val statusBarWarning: Color,
    val statusBarSuccess: Color,

    // Breadcrumb
    val breadcrumbBackground: Color,
    val breadcrumbText: Color,
    val breadcrumbSeparator: Color,
    val breadcrumbActive: Color,

    // Minimap
    val minimapBackground: Color,
    val minimapSlider: Color,
    val minimapViewport: Color,

    // ---- Editor ----
    val editorBackground: Color,
    val editorGutterBackground: Color,
    val editorLineNumber: Color,
    val editorLineNumberActive: Color,
    val editorCurrentLineHighlight: Color,
    val editorSelectionBackground: Color,
    val editorSelectionForeground: Color,
    val editorCaret: Color,
    val editorFindMatchBackground: Color,
    val editorFindMatchBorder: Color,
    val editorFindMatchCurrentBackground: Color,
    val editorFindRangeBackground: Color,
    val editorWordHighlightBackground: Color,
    val editorIndentGuide: Color,
    val editorIndentGuideActive: Color,
    val editorBracketMatching: Color,
    val editorFoldingIcon: Color,
    val editorFoldingHover: Color,
    val editorWhitespace: Color,
    val editorLink: Color,

    // ---- Editor Syntax (Light) ----
    val syntaxKeyword: Color,
    val syntaxString: Color,
    val syntaxNumber: Color,
    val syntaxComment: Color,
    val syntaxCommentDoc: Color,
    val syntaxFunction: Color,
    val syntaxVariable: Color,
    val syntaxType: Color,
    val syntaxOperator: Color,
    val syntaxAnnotation: Color,
    val syntaxConstant: Color,
    val syntaxTag: Color,
    val syntaxAttributeName: Color,
    val syntaxAttributeValue: Color,
    val syntaxDelimiter: Color,
    val syntaxIdentifier: Color,

    // ---- Search ----
    val searchPanelBackground: Color,
    val searchPanelBorder: Color,
    val searchInputBackground: Color,
    val searchInputFocusedBorder: Color,
    val searchResultHighlight: Color,
    val searchResultSelected: Color,
    val searchResultTextMatch: Color,
    val searchResultFileHeader: Color,
    val searchResultLineNumber: Color,

    // ---- Terminal ----
    val terminalBackground: Color,
    val terminalForeground: Color,
    val terminalCursor: Color,
    val terminalBlack: Color,
    val terminalRed: Color,
    val terminalGreen: Color,
    val terminalYellow: Color,
    val terminalBlue: Color,
    val terminalMagenta: Color,
    val terminalCyan: Color,
    val terminalWhite: Color,
    val terminalBrightBlack: Color,
    val terminalBrightRed: Color,
    val terminalBrightGreen: Color,
    val terminalBrightYellow: Color,
    val terminalBrightBlue: Color,
    val terminalBrightMagenta: Color,
    val terminalBrightCyan: Color,
    val terminalBrightWhite: Color,

    // ---- Diff ----
    val diffAddedBackground: Color,
    val diffAddedText: Color,
    val diffAddedBorder: Color,
    val diffAddedGutter: Color,
    val diffRemovedBackground: Color,
    val diffRemovedText: Color,
    val diffRemovedBorder: Color,
    val diffRemovedGutter: Color,
    val diffModifiedBackground: Color,
    val diffModifiedText: Color,
    val diffModifiedBorder: Color,
    val diffHeaderBackground: Color,
    val diffHeaderText: Color,
    val diffWordAdded: Color,
    val diffWordRemoved: Color,

    // ---- Semantic / Status ----
    val success: Color,
    val successBackground: Color,
    val successBorder: Color,
    val warning: Color,
    val warningBackground: Color,
    val warningBorder: Color,
    val error: Color,
    val errorBackground: Color,
    val errorBorder: Color,
    val info: Color,
    val infoBackground: Color,
    val infoBorder: Color,

    // ---- Chat / AI ----
    val chatUserBubble: Color,
    val chatUserText: Color,
    val chatAiBubble: Color,
    val chatAiText: Color,
    val chatInputBackground: Color,
    val chatInputBorder: Color,
    val chatInputFocusedBorder: Color,
    val chatCodeBlockBackground: Color,
    val chatCodeBlockBorder: Color,
    val chatTimestamp: Color,
    val chatDivider: Color,

    // ---- Dialog / Bottom Sheet ----
    val dialogBackground: Color,
    val dialogScrim: Color,
    val bottomSheetBackground: Color,
    val bottomSheetHandle: Color,

    // ---- Notification ----
    val notificationBackground: Color,
    val notificationText: Color,
    val notificationIcon: Color,

    // ---- Progress ----
    val progressTrack: Color,
    val progressIndeterminate: Color,

    // ---- File Colors ----
    val fileFolder: Color,
    val fileFolderClosed: Color,
)

// ---------------------------------------------------------------------------
//  Light Palette
// ---------------------------------------------------------------------------
val XCoderLightColors = IdeColors(
    // Brand
    primary = Color(0xFF6B5CE7),
    primaryLight = Color(0xFF8B84FF),
    primaryDark = Color(0xFF4A3FBF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8E4FF),
    onPrimaryContainer = Color(0xFF4A3FBF),
    primaryHover = Color(0xFF5B4CD4),
    primaryPressed = Color(0xFF3D35A3),

    // Background & Surface
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF1B1B2F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B2F),
    surfaceVariant = Color(0xFFECEEF5),
    onSurfaceVariant = Color(0xFF5C5C72),
    surfaceElevated = Color(0xFFF0F1F7),
    surfaceContainer = Color(0xFFF2F3F9),
    surfaceContainerHigh = Color(0xFFE8EAF0),
    surfaceContainerHighest = Color(0xFFDEE0E8),
    outline = Color(0xFFC4C5D0),
    outlineVariant = Color(0xFFE4E5ED),
    inverseSurface = Color(0xFF303044),
    inverseOnSurface = Color(0xFFF0F0FA),
    inversePrimary = Color(0xFFA9A0FF),
    scrollbarThumb = Color(0xFFC0C0D0),
    scrollbarTrack = Color(0xFFE8E8F0),

    // Text
    textPrimary = Color(0xFF1B1B2F),
    textSecondary = Color(0xFF5C5C72),
    textTertiary = Color(0xFF8B8BA0),
    textDisabled = Color(0xFFBDBDCD),
    textPlaceholder = Color(0xFFA0A0B4),
    textLink = Color(0xFF5A7AE6),

    // Studio Panels
    studioBackground = Color(0xFFF7F8FC),
    studioSurface = Color(0xFFFFFFFF),
    studioSurfaceElevated = Color(0xFFF0F1F7),
    studioSurfaceDim = Color(0xFFECEEF5),

    // Toolbar
    toolbarBackground = Color(0xFFFFFFFF),
    toolbarDivider = Color(0xFFE4E5ED),
    toolbarTitle = Color(0xFF1B1B2F),

    // Tab Bar
    tabBarBackground = Color(0xFFF0F1F7),
    tabActiveBackground = Color(0xFFFFFFFF),
    tabInactiveBackground = Color(0xFFF0F1F7),
    tabActiveIndicator = Color(0xFF6B5CE7),
    tabActiveText = Color(0xFF6B5CE7),
    tabInactiveText = Color(0xFF8B8BA0),
    tabCloseIcon = Color(0xFF8B8BA0),
    tabModifiedIndicator = Color(0xFF6B5CE7),

    // Sidebar
    sidebarBackground = Color(0xFFF0F1F7),
    sidebarHeaderBackground = Color(0xFFE8EAF0),
    sidebarItemHover = Color(0xFFE4E5ED),
    sidebarItemSelected = Color(0xFFE0DEFF),
    sidebarItemSelectedText = Color(0xFF4A3FBF),
    sidebarIcon = Color(0xFF8B8BA0),
    sidebarIconActive = Color(0xFF6B5CE7),
    sidebarDivider = Color(0xFFE4E5ED),
    sidebarSectionHeader = Color(0xFF5C5C72),

    // Status Bar
    statusBarBackground = Color(0xFFFFFFFF),
    statusBarText = Color(0xFF5C5C72),
    statusBarIcon = Color(0xFF8B8BA0),
    statusBarError = Color(0xFFFF6B6B),
    statusBarWarning = Color(0xFFFFB547),
    statusBarSuccess = Color(0xFF00C48C),

    // Breadcrumb
    breadcrumbBackground = Color(0xFFFFFFFF),
    breadcrumbText = Color(0xFF5C5C72),
    breadcrumbSeparator = Color(0xFFBDBDCD),
    breadcrumbActive = Color(0xFF6B5CE7),

    // Minimap
    minimapBackground = Color(0xFFF7F8FC),
    minimapSlider = Color(0x336B5CE7),
    minimapViewport = Color(0x1F6B5CE7),

    // Editor
    editorBackground = Color(0xFFFFFFFF),
    editorGutterBackground = Color(0xFFFAFBFE),
    editorLineNumber = Color(0xFFB0B0C4),
    editorLineNumberActive = Color(0xFF6B5CE7),
    editorCurrentLineHighlight = Color(0xFFF4F2FF),
    editorSelectionBackground = Color(0xFFC5BEF6),
    editorSelectionForeground = Color(0xFF1B1B2F),
    editorCaret = Color(0xFF6B5CE7),
    editorFindMatchBackground = Color(0xFFFFF3BF),
    editorFindMatchBorder = Color(0xFFE5C84F),
    editorFindMatchCurrentBackground = Color(0xFFFFD666),
    editorFindRangeBackground = Color(0xFFFFF8E1),
    editorWordHighlightBackground = Color(0xFFECE8FF),
    editorIndentGuide = Color(0xFFE4E5ED),
    editorIndentGuideActive = Color(0xFFC5BEF6),
    editorBracketMatching = Color(0xFFC5BEF6),
    editorFoldingIcon = Color(0xFFB0B0C4),
    editorFoldingHover = Color(0xFF8B8BA0),
    editorWhitespace = Color(0xFFD8D8E8),
    editorLink = Color(0xFF5A7AE6),

    // Syntax (Light)
    syntaxKeyword = Color(0xFF7C4DFF),
    syntaxString = Color(0xFF2E7D32),
    syntaxNumber = Color(0xFFE65100),
    syntaxComment = Color(0xFF8B8BA0),
    syntaxCommentDoc = Color(0xFF6D8F3E),
    syntaxFunction = Color(0xFF6B5CE7),
    syntaxVariable = Color(0xFF1B1B2F),
    syntaxType = Color(0xFF00838F),
    syntaxOperator = Color(0xFF7C4DFF),
    syntaxAnnotation = Color(0xFF7C4DFF),
    syntaxConstant = Color(0xFFC62828),
    syntaxTag = Color(0xFFE65100),
    syntaxAttributeName = Color(0xFF00838F),
    syntaxAttributeValue = Color(0xFF2E7D32),
    syntaxDelimiter = Color(0xFF5C5C72),
    syntaxIdentifier = Color(0xFF1B1B2F),

    // Search
    searchPanelBackground = Color(0xFFFFFFFF),
    searchPanelBorder = Color(0xFFE4E5ED),
    searchInputBackground = Color(0xFFF0F1F7),
    searchInputFocusedBorder = Color(0xFF6B5CE7),
    searchResultHighlight = Color(0xFFFFF3BF),
    searchResultSelected = Color(0xFFE0DEFF),
    searchResultTextMatch = Color(0xFFE65100),
    searchResultFileHeader = Color(0xFFF7F8FC),
    searchResultLineNumber = Color(0xFF8B8BA0),

    // Terminal (Catppuccin Mocha — always dark)
    terminalBackground = Color(0xFF1E1E2E),
    terminalForeground = Color(0xFFCDD6F4),
    terminalCursor = Color(0xFFF5E0DC),
    terminalBlack = Color(0xFF45475A),
    terminalRed = Color(0xFFF38BA8),
    terminalGreen = Color(0xFFA6E3A1),
    terminalYellow = Color(0xFFF9E2AF),
    terminalBlue = Color(0xFF89B4FA),
    terminalMagenta = Color(0xFFF5C2E7),
    terminalCyan = Color(0xFF94E2D5),
    terminalWhite = Color(0xFFBAC2DE),
    terminalBrightBlack = Color(0xFF585B70),
    terminalBrightRed = Color(0xFFF38BA8),
    terminalBrightGreen = Color(0xFFA6E3A1),
    terminalBrightYellow = Color(0xFFF9E2AF),
    terminalBrightBlue = Color(0xFF89B4FA),
    terminalBrightMagenta = Color(0xFFF5C2E7),
    terminalBrightCyan = Color(0xFF94E2D5),
    terminalBrightWhite = Color(0xFFA6ADC8),

    // Diff
    diffAddedBackground = Color(0xFFE8F5E9),
    diffAddedText = Color(0xFF2E7D32),
    diffAddedBorder = Color(0xFFA5D6A7),
    diffAddedGutter = Color(0xFFC8E6C9),
    diffRemovedBackground = Color(0xFFFFEBEE),
    diffRemovedText = Color(0xFFC62828),
    diffRemovedBorder = Color(0xFFEF9A9A),
    diffRemovedGutter = Color(0xFFFFCDD2),
    diffModifiedBackground = Color(0xFFFFF8E1),
    diffModifiedText = Color(0xFFF57F17),
    diffModifiedBorder = Color(0xFFFFE082),
    diffHeaderBackground = Color(0xFFE8EAF6),
    diffHeaderText = Color(0xFF3949AB),
    diffWordAdded = Color(0xFFA5D6A7),
    diffWordRemoved = Color(0xFFEF9A9A),

    // Semantic
    success = Color(0xFF00C48C),
    successBackground = Color(0xFFE8F5E9),
    successBorder = Color(0xFFA5D6A7),
    warning = Color(0xFFFFB547),
    warningBackground = Color(0xFFFFF8E1),
    warningBorder = Color(0xFFFFE082),
    error = Color(0xFFFF6B6B),
    errorBackground = Color(0xFFFFEBEE),
    errorBorder = Color(0xFFEF9A9A),
    info = Color(0xFF5CA0FF),
    infoBackground = Color(0xFFE3F2FD),
    infoBorder = Color(0xFF90CAF9),

    // Chat / AI
    chatUserBubble = Color(0xFF6B5CE7),
    chatUserText = Color(0xFFFFFFFF),
    chatAiBubble = Color(0xFFF0F1F7),
    chatAiText = Color(0xFF1B1B2F),
    chatInputBackground = Color(0xFFFFFFFF),
    chatInputBorder = Color(0xFFE4E5ED),
    chatInputFocusedBorder = Color(0xFF6B5CE7),
    chatCodeBlockBackground = Color(0xFFF7F8FC),
    chatCodeBlockBorder = Color(0xFFE4E5ED),
    chatTimestamp = Color(0xFF8B8BA0),
    chatDivider = Color(0xFFECEEF5),

    // Dialog / Bottom Sheet
    dialogBackground = Color(0xFFFFFFFF),
    dialogScrim = Color(0xFF1B1B2F),
    bottomSheetBackground = Color(0xFFFFFFFF),
    bottomSheetHandle = Color(0xFFC4C5D0),

    // Notification
    notificationBackground = Color(0xFF303044),
    notificationText = Color(0xFFF0F0FA),
    notificationIcon = Color(0xFFA9A0FF),

    // Progress
    progressTrack = Color(0xFFE4E5ED),
    progressIndeterminate = Color(0xFF6B5CE7),

    // File Colors
    fileFolder = Color(0xFF6B5CE7),
    fileFolderClosed = Color(0xFF8B84FF),
)

// ---------------------------------------------------------------------------
//  Dark Palette
// ---------------------------------------------------------------------------
val XCoderDarkColors = IdeColors(
    // Brand
    primary = Color(0xFF6B5CE7),
    primaryLight = Color(0xFFA9A0FF),
    primaryDark = Color(0xFF8B84FF),
    onPrimary = Color(0xFF1B1B2F),
    primaryContainer = Color(0xFF5249B8),
    onPrimaryContainer = Color(0xFFE0DEFF),
    primaryHover = Color(0xFF8B84FF),
    primaryPressed = Color(0xFFA9A0FF),

    // Background & Surface
    background = Color(0xFF0E0E1A),
    onBackground = Color(0xFFE4E4F0),
    surface = Color(0xFF161625),
    onSurface = Color(0xFFE4E4F0),
    surfaceVariant = Color(0xFF242438),
    onSurfaceVariant = Color(0xFFA0A0B8),
    surfaceElevated = Color(0xFF1C1C30),
    surfaceContainer = Color(0xFF1A1A2E),
    surfaceContainerHigh = Color(0xFF262640),
    surfaceContainerHighest = Color(0xFF303044),
    outline = Color(0xFF484860),
    outlineVariant = Color(0xFF363650),
    inverseSurface = Color(0xFFE4E4F0),
    inverseOnSurface = Color(0xFF303044),
    inversePrimary = Color(0xFF6B5CE7),
    scrollbarThumb = Color(0xFF484860),
    scrollbarTrack = Color(0xFF242438),

    // Text
    textPrimary = Color(0xFFE4E4F0),
    textSecondary = Color(0xFFA0A0B8),
    textTertiary = Color(0xFF6E6E86),
    textDisabled = Color(0xFF484860),
    textPlaceholder = Color(0xFF6E6E86),
    textLink = Color(0xFFA9A0FF),

    // Studio Panels
    studioBackground = Color(0xFF0E0E1A),
    studioSurface = Color(0xFF161625),
    studioSurfaceElevated = Color(0xFF1C1C30),
    studioSurfaceDim = Color(0xFF1A1A2E),

    // Toolbar
    toolbarBackground = Color(0xFF161625),
    toolbarDivider = Color(0xFF303044),
    toolbarTitle = Color(0xFFE4E4F0),

    // Tab Bar
    tabBarBackground = Color(0xFF12121F),
    tabActiveBackground = Color(0xFF161625),
    tabInactiveBackground = Color(0xFF12121F),
    tabActiveIndicator = Color(0xFF8B84FF),
    tabActiveText = Color(0xFFE4E4F0),
    tabInactiveText = Color(0xFF6E6E86),
    tabCloseIcon = Color(0xFF6E6E86),
    tabModifiedIndicator = Color(0xFF8B84FF),

    // Sidebar
    sidebarBackground = Color(0xFF12121F),
    sidebarHeaderBackground = Color(0xFF1A1A2E),
    sidebarItemHover = Color(0xFF1C1C30),
    sidebarItemSelected = Color(0xFF2A2845),
    sidebarItemSelectedText = Color(0xFFA9A0FF),
    sidebarIcon = Color(0xFF6E6E86),
    sidebarIconActive = Color(0xFF8B84FF),
    sidebarDivider = Color(0xFF303044),
    sidebarSectionHeader = Color(0xFFA0A0B8),

    // Status Bar
    statusBarBackground = Color(0xFF0E0E1A),
    statusBarText = Color(0xFFA0A0B8),
    statusBarIcon = Color(0xFF6E6E86),
    statusBarError = Color(0xFFFF6B6B),
    statusBarWarning = Color(0xFFFFB547),
    statusBarSuccess = Color(0xFF00C48C),

    // Breadcrumb
    breadcrumbBackground = Color(0xFF161625),
    breadcrumbText = Color(0xFFA0A0B8),
    breadcrumbSeparator = Color(0xFF484860),
    breadcrumbActive = Color(0xFFA9A0FF),

    // Minimap
    minimapBackground = Color(0xFF161625),
    minimapSlider = Color(0x408B84FF),
    minimapViewport = Color(0x1A8B84FF),

    // Editor
    editorBackground = Color(0xFF1E1E2E),
    editorGutterBackground = Color(0xFF191928),
    editorLineNumber = Color(0xFF484860),
    editorLineNumberActive = Color(0xFFA9A0FF),
    editorCurrentLineHighlight = Color(0xFF262640),
    editorSelectionBackground = Color(0xFF4A4580),
    editorSelectionForeground = Color(0xFFE4E4F0),
    editorCaret = Color(0xFF8B84FF),
    editorFindMatchBackground = Color(0xFF42370E),
    editorFindMatchBorder = Color(0xFFE5C84F),
    editorFindMatchCurrentBackground = Color(0xFF6B5B1A),
    editorFindRangeBackground = Color(0xFF2A2510),
    editorWordHighlightBackground = Color(0xFF3A3560),
    editorIndentGuide = Color(0xFF303044),
    editorIndentGuideActive = Color(0xFF484860),
    editorBracketMatching = Color(0xFF4A4580),
    editorFoldingIcon = Color(0xFF484860),
    editorFoldingHover = Color(0xFF6E6E86),
    editorWhitespace = Color(0xFF363650),
    editorLink = Color(0xFFA9A0FF),

    // Syntax (Dark — Material Ocean-ish)
    syntaxKeyword = Color(0xFFC792EA),
    syntaxString = Color(0xFFC3E88D),
    syntaxNumber = Color(0xFFF78C6C),
    syntaxComment = Color(0xFF546E7A),
    syntaxCommentDoc = Color(0xFF6D8F3E),
    syntaxFunction = Color(0xFF82AAFF),
    syntaxVariable = Color(0xFFEEFFFF),
    syntaxType = Color(0xFFFFCB6B),
    syntaxOperator = Color(0xFF89DDFF),
    syntaxAnnotation = Color(0xFFC792EA),
    syntaxConstant = Color(0xFFF78C6C),
    syntaxTag = Color(0xFFF07178),
    syntaxAttributeName = Color(0xFFFFCB6B),
    syntaxAttributeValue = Color(0xFFC3E88D),
    syntaxDelimiter = Color(0xFF89DDFF),
    syntaxIdentifier = Color(0xFFEEFFFF),

    // Search
    searchPanelBackground = Color(0xFF1C1C30),
    searchPanelBorder = Color(0xFF303044),
    searchInputBackground = Color(0xFF242438),
    searchInputFocusedBorder = Color(0xFF8B84FF),
    searchResultHighlight = Color(0xFF42370E),
    searchResultSelected = Color(0xFF2A2845),
    searchResultTextMatch = Color(0xFFF78C6C),
    searchResultFileHeader = Color(0xFF1A1A2E),
    searchResultLineNumber = Color(0xFF484860),

    // Terminal (always dark)
    terminalBackground = Color(0xFF1E1E2E),
    terminalForeground = Color(0xFFCDD6F4),
    terminalCursor = Color(0xFFF5E0DC),
    terminalBlack = Color(0xFF45475A),
    terminalRed = Color(0xFFF38BA8),
    terminalGreen = Color(0xFFA6E3A1),
    terminalYellow = Color(0xFFF9E2AF),
    terminalBlue = Color(0xFF89B4FA),
    terminalMagenta = Color(0xFFF5C2E7),
    terminalCyan = Color(0xFF94E2D5),
    terminalWhite = Color(0xFFBAC2DE),
    terminalBrightBlack = Color(0xFF585B70),
    terminalBrightRed = Color(0xFFF38BA8),
    terminalBrightGreen = Color(0xFFA6E3A1),
    terminalBrightYellow = Color(0xFFF9E2AF),
    terminalBrightBlue = Color(0xFF89B4FA),
    terminalBrightMagenta = Color(0xFFF5C2E7),
    terminalBrightCyan = Color(0xFF94E2D5),
    terminalBrightWhite = Color(0xFFA6ADC8),

    // Diff
    diffAddedBackground = Color(0xFF1A3328),
    diffAddedText = Color(0xFFA6E3A1),
    diffAddedBorder = Color(0xFF2D5A3D),
    diffAddedGutter = Color(0xFF1E3A2E),
    diffRemovedBackground = Color(0xFF3A1A1A),
    diffRemovedText = Color(0xFFF38BA8),
    diffRemovedBorder = Color(0xFF5A2D2D),
    diffRemovedGutter = Color(0xFF3A2020),
    diffModifiedBackground = Color(0xFF332E1A),
    diffModifiedText = Color(0xFFF9E2AF),
    diffModifiedBorder = Color(0xFF5A4D2D),
    diffHeaderBackground = Color(0xFF1A1A33),
    diffHeaderText = Color(0xFF89B4FA),
    diffWordAdded = Color(0xFF2D5A3D),
    diffWordRemoved = Color(0xFF5A2D2D),

    // Semantic
    success = Color(0xFF00C48C),
    successBackground = Color(0xFF1A3328),
    successBorder = Color(0xFF2D5A3D),
    warning = Color(0xFFFFB547),
    warningBackground = Color(0xFF332E1A),
    warningBorder = Color(0xFF5A4D2D),
    error = Color(0xFFFF6B6B),
    errorBackground = Color(0xFF3A1A1A),
    errorBorder = Color(0xFF5A2D2D),
    info = Color(0xFF5CA0FF),
    infoBackground = Color(0xFF1A2640),
    infoBorder = Color(0xFF2D4A6B),

    // Chat / AI
    chatUserBubble = Color(0xFF5249B8),
    chatUserText = Color(0xFFE4E4F0),
    chatAiBubble = Color(0xFF1C1C30),
    chatAiText = Color(0xFFE4E4F0),
    chatInputBackground = Color(0xFF242438),
    chatInputBorder = Color(0xFF303044),
    chatInputFocusedBorder = Color(0xFF8B84FF),
    chatCodeBlockBackground = Color(0xFF12121F),
    chatCodeBlockBorder = Color(0xFF303044),
    chatTimestamp = Color(0xFF6E6E86),
    chatDivider = Color(0xFF242438),

    // Dialog / Bottom Sheet
    dialogBackground = Color(0xFF1C1C30),
    dialogScrim = Color(0xFF000000),
    bottomSheetBackground = Color(0xFF1C1C30),
    bottomSheetHandle = Color(0xFF484860),

    // Notification
    notificationBackground = Color(0xFF303044),
    notificationText = Color(0xFFE4E4F0),
    notificationIcon = Color(0xFFA9A0FF),

    // Progress
    progressTrack = Color(0xFF303044),
    progressIndeterminate = Color(0xFF8B84FF),

    // File Colors
    fileFolder = Color(0xFF8B84FF),
    fileFolderClosed = Color(0xFF6B5CE7),
)
