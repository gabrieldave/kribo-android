package com.blackeye.kribo.ui.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The four approved Kribo palettes. */
enum class AppTheme(
    val id: String,
    val isLight: Boolean,
    val previewBackground: Long,
    val previewAccent: Long,
) {
    OBSIDIAN("obsidian", false, 0xFF0D1117, 0xFF00D8FF),
    WARM_PAPER("warm_paper", true, 0xFFFAF8F5, 0xFF0099B8),
    AMOLED("amoled", false, 0xFF000000, 0xFF00D8FF),
    PURE_LIGHT("pure_light", true, 0xFFFFFFFF, 0xFF0088A8),
    ;

    companion object {
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: OBSIDIAN
    }
}

enum class HomeLayout(val id: String) {
    COMPACT("compact"),
    BENTO("bento"),
    ;

    companion object {
        fun fromId(id: String?): HomeLayout = entries.firstOrNull { it.id == id } ?: COMPACT
    }
}

/**
 * Lightweight UI preferences (theme + home layout). Separate from Room, secure OCR storage and
 * billing; reads happen once and live changes are exposed as StateFlows.
 */
class UiPreferences(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        AppTheme.fromId(preferences.getString(KEY_THEME, null)),
    )
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    private val _homeLayout = MutableStateFlow(
        HomeLayout.fromId(preferences.getString(KEY_HOME_LAYOUT, null)),
    )
    val homeLayout: StateFlow<HomeLayout> = _homeLayout.asStateFlow()

    fun setTheme(theme: AppTheme) {
        if (_theme.value == theme) return
        preferences.edit().putString(KEY_THEME, theme.id).apply()
        _theme.value = theme
    }

    fun setHomeLayout(layout: HomeLayout) {
        if (_homeLayout.value == layout) return
        preferences.edit().putString(KEY_HOME_LAYOUT, layout.id).apply()
        _homeLayout.value = layout
    }

    companion object {
        private const val PREFS_NAME = "kribo_ui"
        private const val KEY_THEME = "theme_v1"
        private const val KEY_HOME_LAYOUT = "home_layout_v1"
    }
}
