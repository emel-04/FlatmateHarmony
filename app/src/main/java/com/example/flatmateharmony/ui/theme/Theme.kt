package com.example.flatmateharmony.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF3CB371)
private val Orange = Color(0xFFFF7043)
private val White = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = Green,
    secondary = Orange,
    background = White
)

@Composable
fun FlatmateHarmonyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography(),
        content = content
    )
}
