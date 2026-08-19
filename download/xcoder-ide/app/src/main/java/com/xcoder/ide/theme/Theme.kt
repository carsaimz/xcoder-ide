package com.xcoder.ide.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.*
import androidx.core.view.*

/**
 * XCoder IDE Material 3 theme.
 *
 * @param darkTheme When `true` the dark colour scheme is used. Defaults to following the
 *  system setting but can be overridden from Settings.
 * @param dynamicColour On Android 12+ (API 31+) this pulls colours from the user's wallpaper.
 *  Disabled by default so the hand-crafted XCoder palette is always used.
 * @param content The Composable content tree.
 */
@Composable
fun XCoderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColour: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColour && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    // Sync the status bar / nav bar colours with the theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            // Light-status-bar icons on dark theme, dark icons on light.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = XCoderTypography,
        content = content
    )
}

// ---------------------------------------------------------------------------
// Dark scheme
// ---------------------------------------------------------------------------
@Composable
private fun darkColorScheme(): ColorScheme = darkColorScheme(
    primary = PurplePrimary,
    onPrimary = TextPrimaryDark,
    primaryContainer = PurplePrimaryDark,
    onPrimaryContainer = PurplePrimaryLight,
    secondary = TealAccent,
    onSecondary = BackgroundDark,
    secondaryContainer = TealAccentDark,
    onSecondaryContainer = TealAccentLight,
    tertiary = BlueInfo,
    onTertiary = BackgroundDark,
    tertiaryContainer = Color(0xFF2D5F8A),
    onTertiaryContainer = BlueInfo,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceElevatedDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = TextSecondaryDark.copy(alpha = 0.38f),
    outlineVariant = TextSecondaryDark.copy(alpha = 0.12f),
    error = RedError,
    onError = TextPrimaryDark,
    errorContainer = RedError.copy(alpha = 0.15f),
    onErrorContainer = RedError,
    success = GreenSuccess,
    onSuccess = TextPrimaryDark,
    successContainer = GreenSuccess.copy(alpha = 0.15f),
    onSuccessContainer = GreenSuccess,
    warning = OrangeWarning,
    onWarning = BackgroundDark,
    warningContainer = OrangeWarning.copy(alpha = 0.15f),
    onWarningContainer = OrangeWarning
)

// ---------------------------------------------------------------------------
// Light scheme
// ---------------------------------------------------------------------------
@Composable
private fun lightColorScheme(): ColorScheme = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = SurfaceLight,
    primaryContainer = PurplePrimaryLight.copy(alpha = 0.15f),
    onPrimaryContainer = PurplePrimaryDark,
    secondary = TealAccentDark,
    onSecondary = SurfaceLight,
    secondaryContainer = TealAccentLight.copy(alpha = 0.20f),
    onSecondaryContainer = TealAccentDark,
    tertiary = Color(0xFF2962FF),
    onTertiary = SurfaceLight,
    tertiaryContainer = BlueInfo.copy(alpha = 0.15f),
    onTertiaryContainer = Color(0xFF2962FF),
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceElevatedLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = TextSecondaryLight.copy(alpha = 0.38f),
    outlineVariant = TextSecondaryLight.copy(alpha = 0.12f),
    error = RedError,
    onError = SurfaceLight,
    errorContainer = RedError.copy(alpha = 0.10f),
    onErrorContainer = RedError,
    success = GreenSuccess,
    onSuccess = SurfaceLight,
    successContainer = GreenSuccess.copy(alpha = 0.10f),
    onSuccessContainer = GreenSuccess,
    warning = OrangeWarning,
    onWarning = TextPrimaryLight,
    warningContainer = OrangeWarning.copy(alpha = 0.10f),
    onWarningContainer = OrangeWarning
)

// ---------------------------------------------------------------------------
// Extended colour scheme (success / warning tokens not in M3)
// ---------------------------------------------------------------------------

/** Extension so `MaterialTheme.colorScheme.success` works. */
val MaterialTheme.colorSchemeExt: ExtendedColorScheme
    @Composable get() = ExtendedColorScheme(
        success = MaterialTheme.colorScheme.success
            ?: GreenSuccess,
        warning = MaterialTheme.colorScheme.warning
            ?: OrangeWarning
    )

/** Extra semantic colours that Material 3 does not provide. */
data class ExtendedColorScheme(
    val success: androidx.compose.ui.graphics.Color,
    val warning: androidx.compose.ui.graphics.Color
)

private val ColorScheme.success: androidx.compose.ui.graphics.Color?
    get() = try {
        javaClass.getMethod("getSuccess").invoke(this)
            as? androidx.compose.ui.graphics.Color
    } catch (_: Exception) {
        null
    }

private val ColorScheme.warning: androidx.compose.ui.graphics.Color?
    get() = try {
        javaClass.getMethod("getWarning").invoke(this)
            as? androidx.compose.ui.graphics.Color
    } catch (_: Exception) {
        null
    }
