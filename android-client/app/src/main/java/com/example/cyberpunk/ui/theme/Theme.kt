package com.example.cyberpunk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TokyoBackground = Color(0xFF1A1B26)
val TokyoSurface = Color(0xFF24283B)
val TokyoPrimary = Color(0xFF7AA2F7)
val TokyoSecondary = Color(0xFFBB9AF7)
val TokyoAccent = Color(0xFF7DCFFF)
val TokyoError = Color(0xFFF7768E)
val TokyoSuccess = Color(0xFF9ECE6A)
val TokyoOnBackground = Color(0xFFC0CAF5)
val TokyoOnSurface = Color(0xFFA9B1D6)

private val TokyoNightColorScheme = darkColorScheme(
    primary = TokyoPrimary,
    onPrimary = TokyoBackground,
    secondary = TokyoSecondary,
    onSecondary = TokyoBackground,
    tertiary = TokyoAccent,
    onTertiary = TokyoBackground,
    background = TokyoBackground,
    onBackground = TokyoOnBackground,
    surface = TokyoSurface,
    onSurface = TokyoOnBackground,
    surfaceVariant = Color(0xFF2F3549),
    onSurfaceVariant = TokyoOnSurface,
    error = TokyoError,
    onError = TokyoBackground
)

@Composable
fun TimeCapsuleTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TokyoNightColorScheme,
        content = content
    )
}

