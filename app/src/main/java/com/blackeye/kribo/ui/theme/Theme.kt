package com.blackeye.kribo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = KriboElectricCyan,
    onPrimary = KriboOnCyan,
    primaryContainer = KriboCyanDeep,
    onPrimaryContainer = KriboWhite,
    secondary = KriboCyanSoft,
    onSecondary = KriboOnCyan,
    secondaryContainer = Color(0xFF0F2A33),
    onSecondaryContainer = KriboElectricCyan,
    background = KriboObsidian,
    onBackground = KriboWhite,
    surface = KriboSurface,
    onSurface = KriboWhite,
    surfaceVariant = KriboSurfaceRaised,
    onSurfaceVariant = KriboTextSecondary,
    outline = KriboOutline,
    outlineVariant = KriboOutline,
    error = KriboErrorDark,
    onError = KriboOnCyan,
)

private val LightColors = lightColorScheme(
    primary = KriboCyanDeep,
    onPrimary = KriboWhite,
    primaryContainer = KriboPrimaryContainerLight,
    onPrimaryContainer = KriboLightText,
    secondary = KriboCyanDeep,
    onSecondary = KriboWhite,
    secondaryContainer = KriboPrimaryContainerLight,
    onSecondaryContainer = KriboCyanDeep,
    background = KriboLightBackground,
    onBackground = KriboLightText,
    surface = KriboLightSurface,
    onSurface = KriboLightText,
    surfaceVariant = KriboLightSurfaceVariant,
    onSurfaceVariant = KriboLightTextSecondary,
    outline = KriboLightOutline,
    outlineVariant = KriboLightOutline,
    error = KriboLightError,
    onError = KriboWhite,
)

@Composable
fun KriboTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = KriboTypography,
        content = content,
    )
}
