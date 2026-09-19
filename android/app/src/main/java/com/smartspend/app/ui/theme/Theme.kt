package com.smartspend.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

private val LightColorScheme = lightColorScheme(
    primary = Violet,
    onPrimary = PaperWhite,
    primaryContainer = VioletContainer,
    onPrimaryContainer = OnVioletContainer,
    secondary = Tangerine,
    onSecondary = PaperWhite,
    tertiary = Positive,
    onTertiary = PaperWhite,
    background = Cream,
    onBackground = Ink,
    surface = PaperWhite,
    onSurface = Ink,
    surfaceVariant = Sand,
    onSurfaceVariant = InkMuted,
    outline = SandBorder,
    outlineVariant = SandBorder,
    error = Negative,
    onError = PaperWhite
)

private val DarkColorScheme = darkColorScheme(
    primary = VioletDark,
    onPrimary = VioletInk,
    primaryContainer = VioletContainerDark,
    onPrimaryContainer = OnVioletContainerDark,
    secondary = TangerineDark,
    onSecondary = TangerineInk,
    tertiary = PositiveDark,
    onTertiary = PositiveInk,
    background = NightBase,
    onBackground = Moon,
    surface = NightSurface,
    onSurface = Moon,
    surfaceVariant = NightMuted,
    onSurfaceVariant = MoonMuted,
    outline = NightBorder,
    outlineVariant = NightBorder,
    error = NegativeDark,
    onError = NegativeInk
)

@Composable
fun SmartSpendTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extended = if (darkTheme) DarkSmartSpendColors else LightSmartSpendColors
    val categories = remember(darkTheme) { categoryPalette(darkTheme) }

    CompositionLocalProvider(
        LocalSmartSpendColors provides extended,
        LocalCategoryPalette provides categories
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SmartSpendTypography,
            shapes = SmartSpendShapes,
            content = content
        )
    }
}

/**
 * Accessors for the tokens Material3 does not carry. Mirrors the `MaterialTheme.colorScheme`
 * shape so call sites read the same way: `SmartSpendTheme.colors.positive`.
 */
object SmartSpendTheme {
    val colors: SmartSpendColors
        @Composable @ReadOnlyComposable get() = LocalSmartSpendColors.current

    val categories: CategoryPalette
        @Composable @ReadOnlyComposable get() = LocalCategoryPalette.current
}
