package com.riddlesdiary.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DiaryColorScheme = darkColorScheme(
    primary = InkGreen,
    onPrimary = Parchment,
    primaryContainer = InkGreenLight,
    onPrimaryContainer = ParchmentLight,
    secondary = GoldAccent,
    onSecondary = DiaryCover,
    secondaryContainer = GoldDim,
    onSecondaryContainer = Parchment,
    tertiary = DiaryCoverLight,
    onTertiary = Parchment,
    background = DiaryCover,
    onBackground = Parchment,
    surface = ParchmentAged,
    onSurface = TextDark,
    surfaceVariant = ParchmentDark,
    onSurfaceVariant = TextMuted,
    error = androidx.compose.ui.graphics.Color(0xFF8B0000),
    onError = ParchmentLight
)

@Composable
fun RiddlesDiaryTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DiaryColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DiaryCover.toArgb()
            window.navigationBarColor = DiaryCover.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DiaryTypography,
        content = content
    )
}
