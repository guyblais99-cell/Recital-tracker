package com.recital.scavengerhunt.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(0xFFFF6B9D),
    onPrimary = Color(0xFF1A0A2E),
    secondary = Color(0xFF00D4FF),
    tertiary = Color(0xFFFFD93D),
    background = Color(0xFF1A0A2E),
    surface = Color(0xFF2D1B4E),
    surfaceVariant = Color(0xFF3D2066),
    onBackground = Color(0xFFFFF5E6),
    onSurface = Color(0xFFFFF5E6),
    onSecondary = Color(0xFF1A0A2E)
)

@Composable
fun ScavengerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
