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

private val EmeraldColors = darkColorScheme(
    primary = Color(0xFF34D399),
    onPrimary = Color(0xFF04231A),
    primaryContainer = Color(0xFF0F3D2A),
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = Color(0xFF6EE7B7),
    onSecondary = Color(0xFF04231A),
    secondaryContainer = Color(0xFF103A2A),
    onSecondaryContainer = Color(0xFFA7F3D0),
    background = Color(0xFF071A12),
    onBackground = Color(0xFFE6FFF4),
    surface = Color(0xFF0E2419),
    onSurface = Color(0xFFE6FFF4),
    surfaceVariant = Color(0xFF143024),
    onSurfaceVariant = Color(0xFF9CCFB8),
    outline = Color(0xFF1F4433),
    outlineVariant = Color(0xFF1F4433),
    error = KriboErrorDark,
    onError = Color(0xFF2B0000),
)

private val OceanColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF002233),
    primaryContainer = Color(0xFF0B3A52),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF002233),
    secondaryContainer = Color(0xFF0E3348),
    onSecondaryContainer = Color(0xFFBAE6FD),
    background = Color(0xFF08111F),
    onBackground = Color(0xFFEAF6FF),
    surface = Color(0xFF0F1B2D),
    onSurface = Color(0xFFEAF6FF),
    surfaceVariant = Color(0xFF16263D),
    onSurfaceVariant = Color(0xFF9FB6CF),
    outline = Color(0xFF243B57),
    outlineVariant = Color(0xFF243B57),
    error = KriboErrorDark,
    onError = Color(0xFF2B0000),
)

private val VioletColors = darkColorScheme(
    primary = Color(0xFFC084FC),
    onPrimary = Color(0xFF2A0B45),
    primaryContainer = Color(0xFF3B1D5A),
    onPrimaryContainer = Color(0xFFF3E8FF),
    secondary = Color(0xFFD8B4FE),
    onSecondary = Color(0xFF2A0B45),
    secondaryContainer = Color(0xFF33204D),
    onSecondaryContainer = Color(0xFFE9D5FF),
    background = Color(0xFF120A1F),
    onBackground = Color(0xFFF6EDFF),
    surface = Color(0xFF1B102B),
    onSurface = Color(0xFFF6EDFF),
    surfaceVariant = Color(0xFF26163B),
    onSurfaceVariant = Color(0xFFC0A8D8),
    outline = Color(0xFF3A2455),
    outlineVariant = Color(0xFF3A2455),
    error = KriboErrorDark,
    onError = Color(0xFF2B0000),
)

private val RoseColors = lightColorScheme(
    primary = Color(0xFFDB2777),
    onPrimary = KriboWhite,
    primaryContainer = Color(0xFFFCE7F3),
    onPrimaryContainer = Color(0xFF500724),
    secondary = Color(0xFFEC4899),
    onSecondary = KriboWhite,
    secondaryContainer = Color(0xFFFCE7F3),
    onSecondaryContainer = Color(0xFF831843),
    background = Color(0xFFFFF7FA),
    onBackground = Color(0xFF2B0B18),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2B0B18),
    surfaceVariant = Color(0xFFFCE7EF),
    onSurfaceVariant = Color(0xFF7A4A5D),
    outline = Color(0xFFF3D3DF),
    outlineVariant = Color(0xFFF3D3DF),
    error = KriboLightError,
    onError = KriboWhite,
)

private val AmberColors = lightColorScheme(
    primary = Color(0xFFD97706),
    onPrimary = KriboWhite,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF451A03),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color(0xFF2A1A05),
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    background = Color(0xFFFFF9EE),
    onBackground = Color(0xFF2A1A05),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2A1A05),
    surfaceVariant = Color(0xFFFCEFD5),
    onSurfaceVariant = Color(0xFF7A5A2E),
    outline = Color(0xFFF0DFC0),
    outlineVariant = Color(0xFFF0DFC0),
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
        AppTheme.EMERALD -> EmeraldColors
        AppTheme.OCEAN -> OceanColors
        AppTheme.VIOLET -> VioletColors
        AppTheme.ROSE -> RoseColors
        AppTheme.AMBER -> AmberColors
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
