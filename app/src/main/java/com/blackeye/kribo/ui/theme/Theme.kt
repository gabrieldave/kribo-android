package com.blackeye.kribo.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.blackeye.kribo.ui.preferences.AppTheme

private val ObsidianColors = darkColorScheme(
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

private val WarmPaperColors = lightColorScheme(
    primary = Color(0xFF0099B8),
    onPrimary = KriboWhite,
    primaryContainer = Color(0xFFD6EFF4),
    onPrimaryContainer = Color(0xFF00323C),
    secondary = Color(0xFF0099B8),
    onSecondary = KriboWhite,
    secondaryContainer = Color(0xFFDDF1F5),
    onSecondaryContainer = Color(0xFF00505F),
    background = Color(0xFFFAF8F5),
    onBackground = Color(0xFF1C1917),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1917),
    surfaceVariant = Color(0xFFF1EDE6),
    onSurfaceVariant = Color(0xFF6B6257),
    outline = Color(0xFFE8E4DC),
    outlineVariant = Color(0xFFE8E4DC),
    error = KriboLightError,
    onError = KriboWhite,
)

private val AmoledColors = darkColorScheme(
    primary = KriboElectricCyan,
    onPrimary = Color(0xFF00232B),
    primaryContainer = Color(0xFF0B3944),
    onPrimaryContainer = Color(0xFFE6FBFF),
    secondary = KriboCyanSoft,
    onSecondary = Color(0xFF00232B),
    secondaryContainer = Color(0xFF0F2A33),
    onSecondaryContainer = KriboElectricCyan,
    background = Color(0xFF000000),
    onBackground = KriboWhite,
    surface = Color(0xFF0A0D12),
    onSurface = KriboWhite,
    surfaceVariant = Color(0xFF121821),
    onSurfaceVariant = Color(0xFF9BA7B4),
    outline = Color(0xFF1F242D),
    outlineVariant = Color(0xFF1F242D),
    error = KriboErrorDark,
    onError = Color(0xFF2B0000),
)

private val PureLightColors = lightColorScheme(
    primary = Color(0xFF0088A8),
    onPrimary = KriboWhite,
    primaryContainer = Color(0xFFD9F2F7),
    onPrimaryContainer = Color(0xFF00414F),
    secondary = Color(0xFF0088A8),
    onSecondary = KriboWhite,
    secondaryContainer = Color(0xFFD9F2F7),
    onSecondaryContainer = Color(0xFF00505F),
    background = Color(0xFFFFFFFF),
    onBackground = KriboLightText,
    surface = Color(0xFFF4F6F8),
    onSurface = KriboLightText,
    surfaceVariant = Color(0xFFE9EDF1),
    onSurfaceVariant = Color(0xFF5C6670),
    outline = Color(0xFFDDE3E8),
    outlineVariant = Color(0xFFDDE3E8),
    error = KriboLightError,
    onError = KriboWhite,
)

@Composable
fun KriboTheme(
    theme: AppTheme = AppTheme.OBSIDIAN,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (theme) {
        AppTheme.OBSIDIAN -> ObsidianColors
        AppTheme.WARM_PAPER -> WarmPaperColors
        AppTheme.AMOLED -> AmoledColors
        AppTheme.PURE_LIGHT -> PureLightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = theme.isLight
                isAppearanceLightNavigationBars = theme.isLight
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KriboTypography,
        content = content,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
