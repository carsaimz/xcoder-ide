package com.xcoder.ide.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
//  XCoder Theme
// ============================================================================

/**
 * XCoder IDE Material 3 theme.
 *
 * This is the single entry point for all Compose theming.
 *
 * @param darkTheme When `true`, the dark colour scheme is used.
 *   Defaults to following the system setting but can be overridden from Settings.
 * @param dynamicColour On Android 12+ (API 31+) this pulls colours from the
 *   user's wallpaper. **Disabled by default** so the hand-crafted XCoder
 *   palette is always used — matching the AndroidIDE approach of preferring
 *   deterministic theming.
 * @param content The Composable content tree.
 */
@Composable
fun XCoderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColour: Boolean = false,
    content: @Composable () -> Unit
) {
    val ideColors = if (darkTheme) XCoderDarkColors else XCoderLightColors

    val colorScheme = when {
        dynamicColour && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        darkTheme -> darkColorScheme(ideColors)
        else -> lightColorScheme(ideColors)
    }

    // Sync the status bar / navigation bar colours with the theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Toolbar blends into status bar — match toolbar surface.
            window.statusBarColor = ideColors.toolbarBackground.toArgb()
            window.navigationBarColor = ideColors.statusBarBackground.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            // Light icons on dark surface, dark icons on light surface.
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
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
}

// ============================================================================
//  M3 Color Scheme Builders
// ============================================================================

@Composable
private fun lightColorScheme(c: IdeColors): ColorScheme = lightColorScheme(
    // Primary
    primary = c.primary,
    onPrimary = c.onPrimary,
    primaryContainer = c.primaryContainer,
    onPrimaryContainer = c.onPrimaryContainer,
    // Secondary
    secondary = Color(0xFF7C7CDB),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE4E3FF),
    onSecondaryContainer = Color(0xFF2D2A6E),
    // Tertiary
    tertiary = Color(0xFF3D8B7A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD0F0E8),
    onTertiaryContainer = Color(0xFF003730),
    // Error
    error = c.error,
    onError = Color(0xFFFFFFFF),
    errorContainer = c.errorBackground,
    onErrorContainer = c.error,
    // Background & Surface
    background = c.background,
    onBackground = c.onBackground,
    surface = c.surface,
    onSurface = c.onSurface,
    surfaceVariant = c.surfaceVariant,
    onSurfaceVariant = c.onSurfaceVariant,
    surfaceContainer = c.surfaceContainer,
    surfaceContainerHigh = c.surfaceContainerHigh,
    surfaceContainerHighest = c.surfaceContainerHighest,
    // Outline
    outline = c.outline,
    outlineVariant = c.outlineVariant,
    // Inverse
    inverseSurface = c.inverseSurface,
    inverseOnSurface = c.inverseOnSurface,
    inversePrimary = c.inversePrimary,
)

@Composable
private fun darkColorScheme(c: IdeColors): ColorScheme = darkColorScheme(
    // Primary
    primary = c.primaryLight,
    onPrimary = c.onPrimary,
    primaryContainer = c.primaryContainer,
    onPrimaryContainer = c.onPrimaryContainer,
    // Secondary
    secondary = Color(0xFF9B97E0),
    onSecondary = Color(0xFF1B1B2F),
    secondaryContainer = Color(0xFF38336B),
    onSecondaryContainer = Color(0xFFC5C1F0),
    // Tertiary
    tertiary = Color(0xFF6BC4B0),
    onTertiary = Color(0xFF003730),
    tertiaryContainer = Color(0xFF005044),
    onTertiaryContainer = Color(0xFF8FE8D0),
    // Error
    error = c.error,
    onError = c.onSurface,
    errorContainer = c.errorBackground,
    onErrorContainer = c.error,
    // Background & Surface
    background = c.background,
    onBackground = c.onBackground,
    surface = c.surface,
    onSurface = c.onSurface,
    surfaceVariant = c.surfaceVariant,
    onSurfaceVariant = c.onSurfaceVariant,
    surfaceContainer = c.surfaceContainer,
    surfaceContainerHigh = c.surfaceContainerHigh,
    surfaceContainerHighest = c.surfaceContainerHighest,
    // Outline
    outline = c.outline,
    outlineVariant = c.outlineVariant,
    // Inverse
    inverseSurface = c.inverseSurface,
    inverseOnSurface = c.inverseOnSurface,
    inversePrimary = c.inversePrimary,
)
