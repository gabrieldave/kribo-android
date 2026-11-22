package com.blackeye.kribo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = KriboElectricCyan,
    onPrimary = KriboOnCyan,
    primaryContainer = KriboCyanDeep,
    onPrimaryContainer = KriboWhite,
    secondary = KriboCyanSoft,
    onSecondary = KriboOnCyan,
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
