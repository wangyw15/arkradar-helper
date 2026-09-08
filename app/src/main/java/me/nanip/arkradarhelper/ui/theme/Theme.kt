package me.nanip.arkradarhelper.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ArkColorScheme = darkColorScheme(
    primary = ArkCyan,
    onPrimary = ArkBlack,
    secondary = ArkGrey,
    onSecondary = ArkBlack,
    background = ArkBlack,
    onBackground = ArkWhite,
    surface = ArkSurface,
    onSurface = ArkWhite,
    surfaceVariant = ArkSurfaceHigh,
    onSurfaceVariant = ArkGrey,
    outline = ArkGrey
)

@Composable
fun ArkRadarHelperTheme(content: @Composable () -> Unit) {
    // Ark family is a dark-only industrial shell; ignore system light theme
    // and dynamic color to preserve the black/white/cyan contract.
    MaterialTheme(
        colorScheme = ArkColorScheme,
        typography = Typography,
        content = content
    )
}
