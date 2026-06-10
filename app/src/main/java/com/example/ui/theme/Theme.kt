package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StrictEngineeringLightColorScheme = lightColorScheme(
    primary = ElectricVoltGold,
    onPrimary = Color.White,
    secondary = LightBlueGlow,
    onSecondary = Color.White,
    tertiary = TerminalGreen,
    onTertiary = Color.White,
    background = IndustrialBackground,
    onBackground = TextDark,
    surface = IndustrialSurface,
    onSurface = TextDark,
    surfaceVariant = IndustrialCard,
    onSurfaceVariant = TextGray,
    outline = IndustrialBorder,
    error = SafetyRed
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StrictEngineeringLightColorScheme,
        typography = Typography,
        content = content
    )
}
