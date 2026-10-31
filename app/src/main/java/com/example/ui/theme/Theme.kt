package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PersonaForgeColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = ElectricCyanDim,
    onPrimaryContainer = StudioTextPrimary,
    secondary = NeonMagenta,
    onSecondary = Color.White,
    secondaryContainer = NeonMagentaDim,
    onSecondaryContainer = StudioTextPrimary,
    tertiary = SignalLime,
    onTertiary = ObsidianVoid,
    tertiaryContainer = SignalLimeDim,
    onTertiaryContainer = SignalLime,
    background = ObsidianVoid,
    onBackground = StudioTextPrimary,
    surface = CarbonSlate,
    onSurface = StudioTextPrimary,
    surfaceVariant = ControlDeckSlate,
    onSurfaceVariant = StudioTextSecondary,
    outline = StudioBorderSubtle,
    error = AlertCrimson,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = PersonaForgeColorScheme,
        typography = Typography,
        content = content
    )
}
