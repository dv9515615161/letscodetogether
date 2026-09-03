package com.ridescore.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val AcceptGreen = Color(0xFF1E9E52)
val MaybeAmber = Color(0xFFD79A00)
val RejectRed = Color(0xFFD64545)
val CheckGrey = Color(0xFF7A7A7A)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD07A),
    onPrimary = Color(0xFF04240F),
    secondary = Color(0xFFF5C211),
    background = Color(0xFF101418),
    surface = Color(0xFF171C21),
    error = RejectRed,
)

private val LightColors = lightColorScheme(
    primary = AcceptGreen,
    secondary = Color(0xFF8A6A00),
    error = RejectRed,
)

@Composable
fun RideScoreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
