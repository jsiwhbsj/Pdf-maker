package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

@Immutable
data class PdfPlusTokens(
    val isDark: Boolean,
    val canvasBackground: Color,
    val glassSurface: Color,
    val glassSurfaceStrong: Color,
    val glassBorder: Color,
    val primaryGlow: Color,
    val secondaryGlow: Color,
    val tertiaryCyan: Color,
    val goldAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val success: Color,
    val error: Color
)

val LocalPdfPlusTokens = staticCompositionLocalOf {
    PdfPlusTokens(
        isDark = true,
        canvasBackground = ObsidianBlack,
        glassSurface = ObsidianGlass,
        glassSurfaceStrong = ObsidianCard,
        glassBorder = ObsidianBorder,
        primaryGlow = ElectricViolet,
        secondaryGlow = RoyalBlue,
        tertiaryCyan = LuminousCyan,
        goldAccent = ChampagneGold,
        textPrimary = Color.White,
        textSecondary = Color(0xFFA5AAC8),
        success = EmeraldSuccess,
        error = CoralError
    )
}

private val PdfPlusShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun PdfPlusTheme(
    darkTheme: Boolean = true,
    accentTheme: AccentTheme = AccentTheme.VIOLET,
    isBengali: Boolean = false,
    content: @Composable () -> Unit
) {
    val (primary, secondary) = when (accentTheme) {
        AccentTheme.VIOLET -> ElectricViolet to RoyalBlue
        AccentTheme.CYAN -> LuminousCyan to RoyalBlue
        AccentTheme.GOLD -> ChampagneGold to ElectricViolet
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = if (accentTheme == AccentTheme.GOLD) ObsidianBlack else Color.White,
            primaryContainer = DeepPurple.copy(alpha = 0.35f),
            onPrimaryContainer = Color.White,
            secondary = secondary,
            onSecondary = Color.White,
            tertiary = LuminousCyan,
            background = ObsidianBlack,
            onBackground = Color.White,
            surface = ObsidianElevated,
            onSurface = Color.White,
            surfaceVariant = ObsidianCard,
            onSurfaceVariant = Color(0xFFA5AAC8),
            outline = primary.copy(alpha = 0.35f),
            error = CoralError
        )
    } else {
        lightColorScheme(
            primary = if (accentTheme == AccentTheme.GOLD) WarmGold else primary,
            onPrimary = Color.White,
            primaryContainer = primary.copy(alpha = 0.14f),
            onPrimaryContainer = InkPrimary,
            secondary = secondary,
            onSecondary = Color.White,
            tertiary = LuminousCyan,
            background = PlatinumCanvas,
            onBackground = InkPrimary,
            surface = PlatinumSurface,
            onSurface = InkPrimary,
            surfaceVariant = Color(0xFFEAEFF8),
            onSurfaceVariant = InkSecondary,
            outline = primary.copy(alpha = 0.25f),
            error = CoralError
        )
    }

    val tokens = if (darkTheme) {
        PdfPlusTokens(
            isDark = true,
            canvasBackground = ObsidianBlack,
            glassSurface = Color(0xB0131524),
            glassSurfaceStrong = Color(0xDE16192C),
            glassBorder = primary.copy(alpha = 0.32f),
            primaryGlow = primary,
            secondaryGlow = secondary,
            tertiaryCyan = LuminousCyan,
            goldAccent = ChampagneGold,
            textPrimary = Color(0xFFF8F9FF),
            textSecondary = Color(0xFFA2A8C4),
            success = EmeraldSuccess,
            error = CoralError
        )
    } else {
        PdfPlusTokens(
            isDark = false,
            canvasBackground = PlatinumCanvas,
            glassSurface = Color(0xCCFFFFFF),
            glassSurfaceStrong = Color(0xF5FFFFFF),
            glassBorder = primary.copy(alpha = 0.22f),
            primaryGlow = if (accentTheme == AccentTheme.GOLD) WarmGold else primary,
            secondaryGlow = secondary,
            tertiaryCyan = LuminousCyan,
            goldAccent = WarmGold,
            textPrimary = InkPrimary,
            textSecondary = InkSecondary,
            success = EmeraldSuccess,
            error = CoralError
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            activity?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalPdfPlusTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = buildPdfPlusTypography(isBengali),
            shapes = PdfPlusShapes,
            content = content
        )
    }
}
