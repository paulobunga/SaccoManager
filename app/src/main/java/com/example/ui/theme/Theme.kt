package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkBackground,
    primaryContainer = Color(0xFF386641),
    onPrimaryContainer = Color(0xFFDDE6D5),
    secondary = DarkSecondary,
    onSecondary = DarkBackground,
    tertiary = Color(0xFFF7E1D7),
    background = DarkBackground,
    onBackground = Color(0xFFE2E3DC),
    surface = DarkSurface,
    onSurface = Color(0xFFE2E3DC),
    surfaceVariant = Color(0xFF3F423C),
    onSurfaceVariant = Color(0xFFE2E3DC),
    outline = Color(0xFF5A5E54),
    error = Color(0xFFE89390)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = SoftCream,
    primaryContainer = WarmGold,
    onPrimaryContainer = PrimaryGreen,
    secondary = SecondaryGreen,
    onSecondary = DarkSlate,
    secondaryContainer = Color(0xFFF2E8CF),
    onSecondaryContainer = Color(0xFFBC4749),
    tertiary = AccentBlue,
    onTertiary = SoftCream,
    background = SoftCream,
    onBackground = DarkSlate,
    surface = LightSurface,
    onSurface = DarkSlate,
    surfaceVariant = Color(0xFFF3F4F9),
    onSurfaceVariant = DarkSlate,
    outline = Color(0xFFDDE6D5),
    error = AccentRed,
    onError = SoftCream
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep dynamicColor false by default to preserve the customized brand identity
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
