package com.suarastudio.tts.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

private val FallbackLightColorScheme = lightColorScheme(
    primary = Color(0xFF2D6A4F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8F3DC),
    onPrimaryContainer = Color(0xFF081C15),
    secondary = Color(0xFF40916C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6EFE9),
    onSecondaryContainer = Color(0xFF1B4332),
    tertiary = Color(0xFF2557A7),
    background = Color(0xFFF8FAF9),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFF8FAF9),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFEAEFEA),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFC0C9C2)
)

private val FallbackDarkColorScheme = darkColorScheme(
    primary = Color(0xFF74C69D),
    onPrimary = Color(0xFF062C1C),
    primaryContainer = Color(0xFF1B4332),
    onPrimaryContainer = Color(0xFFD8F3DC),
    secondary = Color(0xFF95D5B2),
    onSecondary = Color(0xFF0C3824),
    secondaryContainer = Color(0xFF1F2E27),
    onSecondaryContainer = Color(0xFFB7E4C7),
    tertiary = Color(0xFF8AB4F8),
    background = Color(0xFF111412),
    onBackground = Color(0xFFE1E3E0),
    surface = Color(0xFF111412),
    onSurface = Color(0xFFE1E3E0),
    surfaceVariant = Color(0xFF1E2420),
    onSurfaceVariant = Color(0xFFC0C9C2),
    outline = Color(0xFF8A938D),
    outlineVariant = Color(0xFF404943)
)

@Composable
fun SuaraStudioTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicMonet: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        useDynamicMonet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> FallbackDarkColorScheme
        else -> FallbackLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
